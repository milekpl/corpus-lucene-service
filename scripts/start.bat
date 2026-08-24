@echo off
REM Corpus Lucene Service - Start Script (Windows)

setlocal

REM Configuration
set INDEX_DIR=D:\Dokumenty\slownik-wielki\lucene\lucene-corpus.index
set PORT=8082
set HEAP_SIZE=4g

echo ============================================
echo   Corpus Lucene Service
echo ============================================
echo.

REM Check for JAR file in target directory
for %%f in (target\corpus-lucene-service-*.jar) do set JAR=%%f

if not defined JAR (
    echo Error: JAR file not found in target\
    echo.
    echo Please build the project first:
    echo   mvn package -DskipTests
    echo.
    echo Or download a pre-built release from:
    echo   https://github.com/yourusername/corpus-lucene-service/releases
    exit /b 1
)

echo Using JAR: %JAR%
echo Index directory: %INDEX_DIR%
echo Port: %PORT%
echo Heap size: %HEAP_SIZE%
echo.

REM Create index directory if it doesn't exist
if not exist %INDEX_DIR% mkdir %INDEX_DIR%

echo Starting server...
java -Xmx%HEAP_SIZE% -jar "%JAR%" serve --index "%INDEX_DIR%" --port %PORT%

endlocal
