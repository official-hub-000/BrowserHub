#!/bin/bash
# Checks that the BrowserHub rebrand is complete and that the build scripts
# still parse. Run before building; a failure here is cheap, a failure 3 hours
# into a Chromium build is not.
set -uo pipefail
cd "$(dirname "$0")"

pass=0
fail=0
ok()   { pass=$((pass+1)); printf '  ok    %s\n' "$1"; }
bad()  { fail=$((fail+1)); printf '  FAIL  %s\n' "$1"; }
check(){ if eval "$2"; then ok "$1"; else bad "$1"; fi; }

echo "== Identity =="
check "package id is com.browserhub.app" \
  'grep -q "chrome_public_manifest_package = \"com.browserhub.app\"" args.gn'
check "no third-party package id remains" \
  '! grep -q "io.github.jqssun" args.gn'
check "launcher name is set in patch.sh" \
  'grep -q ">BrowserHub<" patch.sh'
check "Google LLC attribution is replaced" \
  'grep -q "BrowserHub, based on Titanium Browser and Chromium" patch.sh'
check "no leftover Titanium vendor line in patch.sh" \
  '! grep -q "^# sed -i .*jqssun, Google LLC" patch.sh'

echo "== Icon pipeline contract =="
check "icon.svg is well-formed XML" \
  'python3 -c "import xml.etree.ElementTree as E; E.parse(\"res/icon.svg\")"'
check "icon.svg keeps the 512 viewBox icon.sh assumes" \
  'grep -q "viewBox=\"0 0 512 512\"" res/icon.svg'
check "exactly one scale(1) token for icon.sh to rewrite" \
  '[ "$(grep -o "scale(1)" res/icon.svg | wc -l)" -eq 1 ]'
for c in "#1c1c31" "#50507a" "#9393bf" "#23233b"; do
  check "palette placeholder $c present" "grep -q '$c' res/icon.svg"
done

echo "== Scripts parse =="
for f in build.sh common.sh patch.sh res/icon.sh; do
  check "$f has valid bash syntax" "bash -n '$f'"
done
check "bundle.py is valid Python" \
  'python3 -c "import ast; ast.parse(open(\"extensions/bundle.py\").read())"'
check ".gclient is a valid gclient config" \
  'python3 -c "import ast; ast.parse(open(\".gclient\").read())"'

echo "== Build-safety changes =="
check "missing signing keys are handled, not silently empty" \
  'grep -q "SKIP_SIGNING" common.sh'
check "build.sh has an unsigned output path" \
  'grep -q "unsigned-" build.sh'
check "bundle.py tolerates no extension URL" \
  'grep -q "no bundle URL configured" extensions/bundle.py'

echo
if [ "$fail" -eq 0 ]; then
  echo "identity: $pass checks pass — BrowserHub rebrand complete, scripts parse"
  exit 0
fi
echo "identity: $pass passed, $fail FAILED"
exit 1
