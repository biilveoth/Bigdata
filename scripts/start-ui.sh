#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"
cd "$TASK_ROOT"
if [[ ! -f target/conditional-filter-1.0.0.jar ]]; then
  mvn --batch-mode package
fi
exec python3 scripts/web_server.py "$@"
