@echo off
setlocal

if "%~1"=="" (
    echo Usage: run-mo.bat ^<server-pc-ip^>
    echo Example: run-mo.bat 192.168.1.25
    exit /b 1
)

set "SERVER_IP=%~1"
set "MULLIGAN_DB_HOST=%SERVER_IP%"
set "MULLIGAN_DB_PORT=5433"
set "MULLIGAN_DB_HOSTS=%SERVER_IP%:5433"
set "MULLIGAN_DB_NAME=mulligan_db"
set "MULLIGAN_DB_USER=mulligan_app"
set "MULLIGAN_DB_PASSWORD=mulligan_app_pw"
set "MULLIGAN_QUEUE_HOST=%SERVER_IP%"
set "MULLIGAN_QUEUE_PORT=5672"
set "MULLIGAN_QUEUE_HOSTS=%SERVER_IP%:5672"
set "MULLIGAN_QUEUE_USER_MO=mulligan_mo"
set "MULLIGAN_QUEUE_PASSWORD_MO=mo159357"
if "%MULLIGAN_HMAC_KEY%"=="" (
    set "MULLIGAN_HMAC_KEY=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
)

pushd "%~dp0"
call gradlew.bat :parking-system-MOUI:run --no-daemon
set "RUN_EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %RUN_EXIT_CODE%
