#!/bin/bash

echo "Deploying Data Transfer API..."
sudo -E docker compose -p eosc-data-transfer up -d --build --remove-orphans
