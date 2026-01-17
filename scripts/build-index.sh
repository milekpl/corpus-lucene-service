#!/bin/bash
# Corpus Lucene Service - Build Index Script (Linux/Mac)

set -e

# Configuration
JAR_FILE="${JAR_FILE:-target/corpus-lucene-service-*.jar}"
INDEX_DIR="${INDEX_DIR:-./index}"
JDBC_URL="${JDBC_URL:-}"
DB_USER="${DB_USER:-}"
DB_PASSWORD="${DB_PASSWORD:-}"
QUERY="${QUERY:-SELECT source_text, target_text FROM parallel_corpus}"
SOURCE_LANG="${SOURCE_LANG:-en}"
TARGET_LANG="${TARGET_LANG:-pl}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${GREEN}Building Corpus Lucene Index...${NC}"
echo "Index directory: $INDEX_DIR"
echo "Source language: $SOURCE_LANG"
echo "Target language: $TARGET_LANG"

# Check required parameters
if [ -z "$JDBC_URL" ]; then
    echo -e "${RED}Error: JDBC_URL is required${NC}"
    echo "Usage: ./build-index.sh --jdbc 'jdbc:postgresql://localhost/db' --user user --password pass"
    exit 1
fi

# Find the JAR file
JAR=$(ls target/corpus-lucene-service-*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
    echo -e "${RED}Error: No JAR file found in target/${NC}"
    echo ""
    echo "Please build the project first:"
    echo "  mvn package -DskipTests"
    exit 1
fi

echo "Using JAR: $JAR"

# Create index directory if it doesn't exist
mkdir -p "$INDEX_DIR"

# Build the index
exec java -jar "$JAR" build \
    --jdbc "$JDBC_URL" \
    --user "$DB_USER" \
    --password "$DB_PASSWORD" \
    --index "$INDEX_DIR" \
    --source-lang "$SOURCE_LANG" \
    --target-lang "$TARGET_LANG" \
    --query "$QUERY"
