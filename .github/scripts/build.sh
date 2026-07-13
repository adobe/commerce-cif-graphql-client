#!/usr/bin/env bash
set -euo pipefail

mkdir -p target
start_time=$(date +%s)
{
  echo "## Build environment"
  java -version 2>&1
  mvn -v
} | tee target/build-log.txt
mvn -B clean install
end_time=$(date +%s)
echo "duration=$((end_time - start_time))" >> "$GITHUB_OUTPUT"
echo "result=success" >> "$GITHUB_OUTPUT"
