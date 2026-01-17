@echo off
REM Corpus Lucene Service - Build Index Script (Windows)

setlocal

REM Configuration
set INDEX_DIR=.\index
set JDBC_URL=
set DB_USER=
set DB_PASSWORD=
set QUERY=SELECT source_text, target_text FROM parallel_corpus
set SOURCE_LANG=en
set TARGET_LANG=pl

echo ============================================
echo   Building Corpus Lucene Index
echo ============================================
echo.

REM Parse command line arguments
:parse_args
if "%~1"=="" goto :check_params
if "%~1"=="--jdbc" set JDBC_URL=%~2& shift& shift& goto :parse_args
if "%~1"=="--user" set DB_USER=%~2& shift& shift& goto :parse_args
if "%~1"=="--password" set DB_PASSWORD=%~2& shift& shift& goto :parse_args
if "%~1"=="--index" set INDEX_DIR=%~2& shift& shift& goto :parse_args
if "%~1"=="--source-lang" set SOURCE_LANG=%~2& shift& shift& goto :parse_args
if "%~1"=="--target-lang" set TARGET_LANG=%~2& shift& shift& goto :parse_args
if "%~1"=="--query" set QUERY=%~2& shift& shift& goto :parse_args
shift
goto :parse_args

:check_params
if "%JDBC_URL%"=="" (
    echo Error: JDBC_URL is required
    echo Usage: build-index.bat --jdbc "jdbc:postgresql://localhost/db" --user user --password pass
    exit /b 1
)

echo Index directory: %INDEX_DIR%
echo Source language: %SOURCE_LANG%
echo Target language: %TARGET_LANG%

REM Check for JAR file
for %%f in (target\corpus-lucene-service-*.jar) do set JAR=%%f

if not defined JAR (
    echo Error: JAR file not found in target\
    echo.
    echo Please build the project first:
    echo   mvn package -DskipTests
    exit /b 1
)

echo Using JAR: %JAR%

REM Create index directory if it doesn't exist
if not exist %INDEX_DIR% mkdir %INDEX_DIR%

REM Build the index
java -jar "%JAR%" build ^
    --jdbc "%JDBC_URL%" ^
    --user "%DB_USER%" ^
    --password "%DB_PASSWORD%" ^
    --index "%INDEX_DIR%" ^
    --source-lang "%SOURCE_LANG%" ^
    --target-lang "%TARGET_LANG%" ^
    --query "%QUERY%"

endlocal
