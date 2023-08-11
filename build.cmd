@echo off

echo Building the EOSC Data Transfer Proxy...

cd src\main\docker
docker-compose -p eosc-beyond up -d --build --remove-orphans
