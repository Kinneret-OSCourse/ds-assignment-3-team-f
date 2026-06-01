@echo off
setlocal

pushd "%~dp0"
docker compose up -d postgres rabbitmq
set "RUN_EXIT_CODE=%ERRORLEVEL%"
popd

if not "%RUN_EXIT_CODE%"=="0" (
    exit /b %RUN_EXIT_CODE%
)

echo.
echo Server services are starting.
echo Run ipconfig and give the IPv4 address to the Customer, PEO, and MO computers.
echo RabbitMQ management: http://^<server-pc-ip^>:15672
echo RabbitMQ username: mulligan
echo RabbitMQ password: mulligan123
