#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"
cd "$TASK_ROOT"
output="${1:-output/demo-$(date -u '+%Y%m%dT%H%M%SZ')}"
hadoop jar target/conditional-filter-1.0.0.jar data/orders.csv "$output"
cat "$output/part-r-00000"
