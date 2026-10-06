#!/usr/bin/env bash
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -n "${BAI20_JAVA_HOME:-}" ]]; then
  export JAVA_HOME="$BAI20_JAVA_HOME"
else
  if [[ -d /opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home ]]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
  elif [[ -d /usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home ]]; then
    export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
  elif [[ -x /usr/libexec/java_home ]]; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 11)"
  else
    echo 'Set BAI20_JAVA_HOME to a Java 11 JDK before running this script.' >&2
    return 1
  fi
fi
export HADOOP_HOME="${HADOOP_HOME:-$TASK_ROOT/.tools/hadoop-3.3.6}"
export HADOOP_CONF_DIR="$TASK_ROOT/config/hadoop"
export PATH="$JAVA_HOME/bin:$HADOOP_HOME/bin:$PATH"
