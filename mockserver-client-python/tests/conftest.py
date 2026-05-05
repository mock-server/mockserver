import os
import shutil
import socket
import subprocess
import time
import urllib.request

import pytest


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
    env_host = os.environ.get("MOCKSERVER_HOST")
    env_port = os.environ.get("MOCKSERVER_PORT")

    if env_host and env_port:
        host = env_host
        port = int(env_port)
        _wait_for_mockserver(host, port)
        yield host, port
        return

    if not shutil.which("docker"):
        pytest.skip("Docker is not available")

    port = _find_free_port()
    host = "localhost"
    container_name = f"mockserver-python-integration-{port}"

    proc = subprocess.run(
        [
            "docker", "run", "-d",
            "--name", container_name,
            "-p", f"{port}:1080",
            "mockserver/mockserver:latest",
        ],
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        pytest.skip(f"Failed to start MockServer container: {proc.stderr}")

    container_id = proc.stdout.strip()

    try:
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
