#!/bin/bash
set -uo pipefail

TEST_DIR="${TEST_DIR:-/tests}"
mkdir -p /logs/verifier
echo 0 > /logs/verifier/reward.txt

if [ "$PWD" = "/" ]; then
  echo "Error: No working directory set."
  exit 0
fi

# Activate the pre-built uv venv isolation
source /opt/verifier-venv/bin/activate

# Run the python verifier to check the Java app's output files
python -m pytest --ctrf /logs/verifier/ctrf.json /tests/test_outputs.py -rA

if [ $? -eq 0 ]; then
  echo 1 > /logs/verifier/reward.txt
else
  echo 0 > /logs/verifier/reward.txt
fi