#!/bin/bash

echo "Building API containers..."
sudo -E docker compose -p eosc-data-transfer up -d --build --remove-orphans
