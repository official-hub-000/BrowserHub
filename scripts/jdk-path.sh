#!/usr/bin/env bash
# Prints a usable JDK bin directory, restoring one if it is missing.
#
# The sandbox's /tmp is wiped between sessions, so jdk4py's runtime disappears and
# every build fails with a confusing "cannot open shared object file". This makes
# the toolchain self-healing instead of something to remember.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JDK="$ROOT/.jdk"

if [ -x "$JDK/bin/java" ]; then
  echo "$JDK/bin"
  exit 0
fi

echo "Restoring the JDK into $JDK ..." >&2
TMPVENV="$(mktemp -d)"
python3 -m venv "$TMPVENV" >&2
"$TMPVENV/bin/pip" install -q jdk4py >&2
SRC="$(ls -d "$TMPVENV"/lib/python3.*/site-packages/jdk4py/java-runtime 2>/dev/null | head -n 1)"
[ -n "$SRC" ] || { echo "jdk4py did not provide a java-runtime" >&2; exit 1; }
# The whole directory is needed: the launcher resolves libjli.so relative to
# itself, and a bin/-only copy fails to start.
cp -a "$SRC" "$JDK"
rm -rf "$TMPVENV"
echo "$JDK/bin"
