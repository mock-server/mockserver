#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

MOCKSERVER_PORT=1080
UI_PORT=3000
REBUILD=false
NO_POPULATE=false
NO_BROWSER=false

show_help() {
  cat <<EOF
Usage: ./scripts/local_ui_dev.sh [OPTIONS]

Launch MockServer backend and UI dev server for development.

Options:
  --rebuild       Force rebuild MockServer JAR even if it exists
  --no-populate   Skip loading example data into MockServer
  --no-browser    Don't auto-open browser
  --port PORT     Custom MockServer port (default: 1080)
  --help          Show this help message

Examples:
  ./scripts/local_ui_dev.sh
  ./scripts/local_ui_dev.sh --rebuild --no-browser
  ./scripts/local_ui_dev.sh --port 9090

Press Ctrl+C to stop both servers.
EOF
}

while [[ $# -gt 0 ]]; do
  case $1 in
    --rebuild)
      REBUILD=true
      shift
      ;;
    --no-populate)
      NO_POPULATE=true
      shift
      ;;
    --no-browser)
      NO_BROWSER=true
      shift
      ;;
    --port)
      MOCKSERVER_PORT="$2"
      shift 2
      ;;
    --help)
      show_help
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

echo "========================================"
echo "MockServer UI Development Environment"
echo "========================================"
echo ""

check_port() {
  local port=$1
  local name=$2
  if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo "ERROR: Port $port is already in use (needed for $name)"
    echo "Please free the port and try again"
    exit 1
  fi
}

echo "→ Checking port availability..."
check_port $MOCKSERVER_PORT "MockServer"
check_port $UI_PORT "UI Dev Server"

MOCKSERVER_JAR="$REPO_ROOT/mockserver/mockserver-netty/target/mockserver-netty-"*"-shaded.jar"

if [ "$REBUILD" = true ] || ! ls $MOCKSERVER_JAR 1> /dev/null 2>&1; then
  if [ "$REBUILD" = true ]; then
    echo "→ Rebuilding MockServer (--rebuild flag set)..."
  else
    echo "→ Building MockServer (JAR not found)..."
  fi
  
  cd "$REPO_ROOT/mockserver"
  ./mvnw clean install -DskipTests -pl mockserver-netty -am
  cd "$REPO_ROOT"
  
  if ! ls $MOCKSERVER_JAR 1> /dev/null 2>&1; then
    echo "ERROR: MockServer build failed - JAR not found"
    exit 1
  fi
  echo "✓ MockServer built successfully"
else
  echo "✓ MockServer JAR already exists (use --rebuild to force rebuild)"
fi

if [ ! -d "$REPO_ROOT/mockserver-ui/node_modules" ]; then
  echo "→ Installing UI dependencies..."
  cd "$REPO_ROOT/mockserver-ui"
  npm install
  cd "$REPO_ROOT"
  echo "✓ UI dependencies installed"
else
  echo "✓ UI dependencies already installed"
fi

echo "→ Starting MockServer on port $MOCKSERVER_PORT..."
java -jar $MOCKSERVER_JAR -serverPort $MOCKSERVER_PORT -logLevel INFO > /dev/null 2>&1 &
MOCKSERVER_PID=$!

wait_for_service() {
  local url=$1
  local name=$2
  local timeout=30
  local elapsed=0
  
  echo "  Waiting for $name to start..."
  until curl -s "$url" > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
      echo "ERROR: $name failed to start within ${timeout}s"
      return 1
    fi
    sleep 0.5
    elapsed=$((elapsed + 1))
  done
}

cleanup() {
  echo ""
  echo "→ Stopping servers..."
  if [ ! -z "$MOCKSERVER_PID" ]; then
    kill $MOCKSERVER_PID 2>/dev/null || true
  fi
  if [ ! -z "$UI_PID" ]; then
    kill $UI_PID 2>/dev/null || true
  fi
  wait $MOCKSERVER_PID $UI_PID 2>/dev/null || true
  echo "✓ Servers stopped"
  exit 0
}

trap cleanup INT TERM EXIT

if ! wait_for_service "http://localhost:$MOCKSERVER_PORT/mockserver/status" "MockServer"; then
  kill $MOCKSERVER_PID 2>/dev/null || true
  exit 1
fi
echo "✓ MockServer started (PID: $MOCKSERVER_PID)"

if [ "$NO_POPULATE" = false ]; then
  echo "→ Populating example data..."
  "$SCRIPT_DIR/ui_dev_populate_data.sh" --port $MOCKSERVER_PORT
  echo "✓ Example data loaded"
else
  echo "⊘ Skipping example data (--no-populate flag set)"
fi

echo "→ Starting UI dev server on port $UI_PORT..."
cd "$REPO_ROOT/mockserver-ui"
MOCKSERVER_URL="http://localhost:$MOCKSERVER_PORT" npm run dev > /dev/null 2>&1 &
UI_PID=$!
cd "$REPO_ROOT"

if ! wait_for_service "http://localhost:$UI_PORT" "UI Dev Server"; then
  kill $MOCKSERVER_PID $UI_PID 2>/dev/null || true
  exit 1
fi
echo "✓ UI dev server started (PID: $UI_PID)"

UI_URL="http://localhost:$UI_PORT/mockserver/dashboard/"

if [ "$NO_BROWSER" = false ]; then
  echo "→ Opening browser..."
  if command -v open > /dev/null 2>&1; then
    open "$UI_URL"
  elif command -v xdg-open > /dev/null 2>&1; then
    xdg-open "$UI_URL"
  elif command -v start > /dev/null 2>&1; then
    start "$UI_URL"
  else
    echo "  Could not detect browser command, please open manually"
  fi
fi

echo ""
echo "========================================"
echo "✓ Development Environment Ready"
echo "========================================"
echo ""
echo "MockServer:     http://localhost:$MOCKSERVER_PORT"
echo "UI Dashboard:   http://localhost:$MOCKSERVER_PORT/mockserver/dashboard"
echo "UI Dev Server:  $UI_URL"
echo ""
echo "MockServer PID: $MOCKSERVER_PID"
echo "UI Dev PID:     $UI_PID"
echo ""
echo "Press Ctrl+C to stop both servers"
echo "========================================"
echo ""

wait
