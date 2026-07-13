#!/usr/bin/env bash
#*******************************************************************************
#
#    Copyright 2026 Adobe. All rights reserved.
#    This file is licensed to you under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License. You may obtain a copy
#    of the License at http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software distributed under
#    the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
#    OF ANY KIND, either express or implied. See the License for the specific language
#    governing permissions and limitations under the License.
#
#******************************************************************************/
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
