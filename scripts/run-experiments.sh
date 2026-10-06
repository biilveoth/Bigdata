#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"
cd "$TASK_ROOT"
python3 scripts/experiments.py run "$@"
