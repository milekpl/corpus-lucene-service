#!/bin/bash
# Corpus Lucene Service - Stop Script (Linux/Mac)

set -e

PORT="${PORT:-8081}"

echo "Stopping Corpus Lucene Service on port $PORT..."

# Find and kill the process
PID=$(lsof -ti:$PORT 2>/dev/null || true)

if [ -n "$PID" ]; then
    echo "Killing process $PID..."
    kill $PID 2>/dev/null || true
    echo "Service stopped."
else
    echo "No service found running on port $PORT."
fi

# Also try to find by process name
PIDS=$(pgrep -f "corpus-lucene-service" 2>/dev/null || true)

if [ -n "$PIDS" ]; then
    echo "Killing processes: $PIDS"
    echo $PIDS | xargs kill 2>/dev/null || true
fi
