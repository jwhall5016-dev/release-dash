#!/bin/bash

# Release Dashboard - Build and Run Script with Debugger Support
# Usage:
#   ./run.sh           - Build and run normally
#   ./run.sh debug     - Build and run with debugger on port 5005
#   ./run.sh --skip-build - Skip building, just run

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="${SCRIPT_DIR}/frontend"
BACKEND_DIR="${SCRIPT_DIR}/backend"
DEBUG_MODE=false
SKIP_BUILD=false

# Parse arguments
for arg in "$@"; do
  case $arg in
    debug|--debug|-d)
      DEBUG_MODE=true
      shift
      ;;
    --skip-build|-s)
      SKIP_BUILD=true
      shift
      ;;
    *)
      shift
      ;;
  esac
done

echo "=========================================="
echo "Release Dashboard - Build & Run Script"
echo "=========================================="

# Build frontend
if [ "$SKIP_BUILD" = false ]; then
  echo ""
  echo "📦 Installing frontend dependencies..."
  cd "${FRONTEND_DIR}"
  npm install

  echo ""
  echo "📦 Building frontend..."
  npm run build
  
  echo ""
  echo "📋 Copying frontend to backend static resources..."
  cd "${SCRIPT_DIR}"
  rm -rf "${BACKEND_DIR}/src/main/resources/static"
  mkdir -p "${BACKEND_DIR}/src/main/resources/static/spa"
  cp "${FRONTEND_DIR}/dist/index.html" "${BACKEND_DIR}/src/main/resources/static/spa/index.html"
  cp -r "${FRONTEND_DIR}/dist/assets" "${BACKEND_DIR}/src/main/resources/static/assets"
  echo "✓ Frontend files copied"
  
  echo ""
  echo "🔨 Building backend..."
  cd "${BACKEND_DIR}"
  mvn clean package -DskipTests
  echo "✓ Backend built successfully"
else
  echo "⏭️  Skipping build (using existing artifacts)"
fi

# Kill any existing process on port 8080
echo ""
echo "🧹 Cleaning up existing processes..."
lsof -ti:8080 | xargs kill -9 2>/dev/null || echo "  No existing process on port 8080"

# Kill any existing process on debug port 5005 if in debug mode
if [ "$DEBUG_MODE" = true ]; then
  lsof -ti:5005 | xargs kill -9 2>/dev/null || echo "  No existing process on debug port 5005"
fi

# Run the application
echo ""
if [ "$DEBUG_MODE" = true ]; then
  echo "🐛 Starting application with DEBUGGER enabled..."
  echo "   Debug port: 5005"
  echo "   Application port: 8080"
  echo ""
  echo "   To attach debugger:"
  echo "   - IntelliJ IDEA: Run -> Attach to Process -> Remote JVM Debug"
  echo "   - VS Code: Use Java Debug extension with port 5005"
  echo "   - Command line: jdb -attach localhost:5005"
  echo ""
  cd "${BACKEND_DIR}"
  mvn spring-boot:run \
    -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
else
  echo "🚀 Starting application..."
  echo "   Application port: 8080"
  echo ""
  cd "${BACKEND_DIR}"
  mvn spring-boot:run
fi
