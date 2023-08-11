#!/bin/sh

echo "Building the EOSC Data Transfer Proxy..."

cd src/main/docker
sudo docker-compose -p eosc-beyond up -d --build --remove-orphans
