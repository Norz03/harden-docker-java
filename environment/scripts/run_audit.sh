#!/bin/bash
set -euo pipefail

echo "Starting Mercury Security Audit Workflow..."
cd /app/mercury-cli

echo "Validating Docker Compose Fixture..."
python3 /app/scripts/validate_yaml.py /app/fixtures/docker-compose.yml

echo "Running Java Compose Hardener..."
./gradlew run

echo "Audit Workflow Complete. Check /app/output/ for results."