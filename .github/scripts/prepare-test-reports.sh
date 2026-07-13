#!/usr/bin/env bash
set -euo pipefail

if [ ! -d target/surefire-reports ]; then
  echo "No surefire reports found."
  exit 0
fi

mkdir -p build-reports/surefire-reports build-reports/jacoco
cp -r target/surefire-reports/. build-reports/surefire-reports/
if [ -d target/site/jacoco ]; then
  cp -r target/site/jacoco/. build-reports/jacoco/
fi

mvn -B org.apache.maven.plugins:maven-surefire-report-plugin:3.0.0-M3:report-only
if [ -f target/site/surefire-report.html ]; then
  cp target/site/surefire-report.html build-reports/
fi

python3 "$(dirname "$0")/parse_test_reports.py"
