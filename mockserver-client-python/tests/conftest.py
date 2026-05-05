import os
import shutil
import socket
import subprocess
import time
import urllib.request

import pytest


def _running_in_docker():
    return os.path.exists("/.dockerenv") or os.path.exists("/run/.containerenv")


def _find_free_port():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("", 0))
        return s.getsockname()[1]


def _wait_for_mockserver(host, port, timeout=30):
    url = f"http://{host}:{port}/mockserver/status"
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            req = urllib.request.Request(url, method="PUT")
            with urllib.request.urlopen(req, timeout=2) as resp:
                if resp.status == 200:
                    return True
        except Exception:
            time.sleep(0.5)
    raise RuntimeError(f"MockServer did not start on {host}:{port} within {timeout}s")


@pytest.fixture(scope="session")
def mockserver_url():
    if not shutil.which("docker"):
        pytest.skip("Docker is not available")

    container_name = "mockserver-python-integration"
    in_docker = _running_in_docker()

    if in_docker:
        host = container_name
        port = 1080
        docker_args = [
            "docker", "run", "-d",
            "--name", container_name,
            "mockserver/mockserver:latest",
        ]
        my_id = socket.gethostname()
        network_proc = subprocess.run(
            ["docker", "inspect", my_id, "--format", "{{range .NetworkSettings.Networks}}{{.NetworkID}}{{end}}"],
            capture_output=True, text=True,
        )
        network_id = network_proc.stdout.strip() if network_proc.returncode == 0 else ""
    else:
        port = _find_free_port()
        host = "localhost"
        docker_args = [
            "docker", "run", "-d",
            "--name", container_name,
            "-p", f"{port}:1080",
            "mockserver/mockserver:latest",
        ]
        network_id = ""

    proc = subprocess.run(docker_args, capture_output=True, text=True)
    if proc.returncode != 0:
        pytest.skip(f"Failed to start MockServer container: {proc.stderr}")

    container_id = proc.stdout.strip()

    try:
        if in_docker and network_id:
            subprocess.run(
                ["docker", "network", "connect", network_id, container_name],
                capture_output=True, text=True,
            )
        _wait_for_mockserver(host, port)
        yield host, port
    finally:
        subprocess.run(["docker", "rm", "-f", container_id], capture_output=True)


@pytest.fixture()
def mockserver_host(mockserver_url):
    return mockserver_url[0]


@pytest.fixture()
def mockserver_port(mockserver_url):
    return mockserver_url[1]


@pytest.fixture()
def mockserver_client(mockserver_url):
    from mockserver import MockServerClient

    host, port = mockserver_url
    with MockServerClient(host, port) as client:
        client.reset()
        yield client
