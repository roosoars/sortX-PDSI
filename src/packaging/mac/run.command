#!/bin/bash
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"  # raiz do ZIP (onde fica o JAR)
cd "$DIR"

JAR=$(ls -1 sortx-*.jar 2>/dev/null | head -n1 || true)
if [[ -z "${JAR}" ]]; then
  echo "[ERRO] JAR não encontrado. Rode 'mvn -DskipTests package' antes e gere o ZIP."
  exit 1
fi

JAVA_BIN=""
if /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
  JAVA_HOME_21="$(`/usr/libexec/java_home -v 21`)"
  JAVA_BIN="${JAVA_HOME_21}/bin/java"
fi
if [[ -z "$JAVA_BIN" && -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
fi
if [[ -z "$JAVA_BIN" ]]; then
  JAVA_BIN="java"
fi

exec "$JAVA_BIN" -jar "$JAR"
