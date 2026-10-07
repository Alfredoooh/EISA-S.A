#!/bin/sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if grep -RInE "getChildAte|(^|[^A-Za-z0-9_])ite\." "$ROOT/app/src/main/java" >/tmp/appao_bad_tokens.txt 2>/dev/null; then
  echo "Build source sanity check: FAIL"
  cat /tmp/appao_bad_tokens.txt
  exit 1
fi
echo "Build source sanity check: PASS"
