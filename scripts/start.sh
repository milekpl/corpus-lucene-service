#!/bin/bash
# Corpus Lucene Service - Start Script (Linux/Mac)

set -e

# Configuration
JAR_FILE="${JAR_FILE:-target/corpus-lucene-service-*.jar}"
INDEX_DIR="${INDEX_DIR:-./index}"
PORT="${PORT:-8081}"
HEAP_SIZE="${HEAP_SIZE:-4g}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting Corpus Lucene Service...${NC}"
echo "Index directory: $INDEX_DIR"
echo "Port: $PORT"
echo "Heap size: $HEAP_SIZE"

# Find the JAR file
JAR=$(ls target/corpus-lucene-service-*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
    echo -e "${RED}Error: No JAR file found in target/${NC}"
    echo ""
    echo "Please build the project first:"
    echo "  mvn package -DskipTests"
    echo ""
    echo "Or download a pre-built release from:"
    echo "  https://github.com/yourusername/corpus-lucene-service/releases"
    exit 1
fi

echo "Using JAR: $JAR"

# Create index directory if it doesn't exist
mkdir -p "$INDEX_DIR"

# Start the server
exec java -Xmx"$HEAP_SIZE" -jar "$JAR" serve --index "$INDEX_DIR" --port "$PORT"
