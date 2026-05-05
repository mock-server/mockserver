import json
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


def _wait_for_mockserver(port, timeout=30):
    url = f"http://localhost:{port}/mockserver/status"
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            req = urllib.request.Request(url, method="PUT")
            with urllib.request.urlopen(req, timeout=2) as resp:
                if resp.status == 200:
                    return True
        except Exception:
            time.sleep(0.5)
    raise RuntimeError(f"MockServer did not start on port {port} within {timeout}s")


@pytest.fixture(scope="session")
def mockserver_port():
    if not shutil.which("docker"):
        pytest.skip("Docker is not available")

    port = _find_free_port()
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
        _wait_for_mockserver(port)
        yield port
    finally:
        subprocess.run(["docker", "rm", "-f", container_id], capture_output=True)


@pytest.fixture()
def mockserver_client(mockserver_port):
    from mockserver import MockServerClient

    with MockServerClient("localhost", mockserver_port) as client:
        client.reset()
        yield client
