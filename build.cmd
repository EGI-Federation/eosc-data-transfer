@echo off

echo Building the EOSC Future Data Transfer Proxy...

cd src\main\docker
docker-compose -p eosc-future up -d --build --remove-orphans
