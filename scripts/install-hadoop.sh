#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$TASK_ROOT"
mkdir -p .tools evidence
exec > >(tee -a evidence/install-hadoop.log) 2>&1
echo "Installation started: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
if [[ -z "${BAI20_JAVA_HOME:-}" ]] && command -v brew >/dev/null 2>&1; then
  HOMEBREW_NO_AUTO_UPDATE=1 brew install openjdk@11
fi
source scripts/env.sh
archive="$TASK_ROOT/.tools/hadoop-3.3.6.tar.gz"
base=https://archive.apache.org/dist/hadoop/common/hadoop-3.3.6
if [[ ! -x "$HADOOP_HOME/bin/hadoop" ]]; then
  curl --fail --location --retry 3 --continue-at - --output "$archive" "$base/hadoop-3.3.6.tar.gz"
  curl --fail --location --retry 3 --output "$archive.sha512" "$base/hadoop-3.3.6.tar.gz.sha512"
  expected="$(tr '[:upper:]' '[:lower:]' < "$archive.sha512" | tr ' ' '\n' | awk '/^[a-f0-9]+$/ && length($0)==128 {print; exit}')"
  actual="$(shasum -a 512 "$archive" | awk '{print $1}')"
  [[ -n "$expected" && "$expected" == "$actual" ]] || { echo 'SHA-512 verification failed'; exit 1; }
  echo "SHA512=$actual"
  tar -xzf "$archive" -C "$TASK_ROOT/.tools"
fi
if [[ -f "$archive" && -f "$archive.sha512" ]]; then
  expected="$(tr '[:upper:]' '[:lower:]' < "$archive.sha512" | tr ' ' '\n' | awk '/^[a-f0-9]+$/ && length($0)==128 {print; exit}')"
  actual="$(shasum -a 512 "$archive" | awk '{print $1}')"
  [[ -n "$expected" && "$expected" == "$actual" ]] || { echo 'SHA-512 verification failed'; exit 1; }
  echo "DOWNLOAD_URL=$base/hadoop-3.3.6.tar.gz"
  echo "SHA512=$actual"
  echo 'ARCHIVE_VERIFICATION=PASS'
fi
"$JAVA_HOME/bin/java" -version
hadoop version
echo "Installed at $HADOOP_HOME"
