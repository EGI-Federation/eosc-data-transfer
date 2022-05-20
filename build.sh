#!/bin/sh

echo "Building the EOSC Future Data Transfer Proxy..."

cd src/main/docker
sudo docker-compose -p eosc-future up -d --build --remove-orphans
