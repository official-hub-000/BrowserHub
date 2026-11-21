#!/bin/bash
# Verifies this tree is stock Titanium plus a switchable feature layer, and
# nothing else. Run before building.
#
# Two things must hold:
#   1. With no features disabled, the build applies every Titanium edit.
#   2. No BrowserHub identity or feature has leaked in.
set -uo pipefail
cd "$(dirname "$0")"

pass=0; fail=0
ok()   { pass=$((pass+1)); printf '  ok    %s\n' "$1"; }
bad()  { fail=$((fail+1)); printf '  FAIL  %s\n' "$1"; }
check(){ if eval "$2"; then ok "$1"; else bad "$1"; fi; }

echo "== Stock Titanium identity =="
check "package id is Titanium's io.github.jqssun.helium" \
  'grep -q "chrome_public_manifest_package = \"io.github.jqssun.helium\"" args.gn'
check "no BrowserHub package id" \
  '! grep -q "com.browserhub.app" args.gn'
check "no BrowserHub name in the string edits" \
  '! grep -rq "BrowserHub" patch.sh features/ --include="*.sh"'
check "no Google LLC -> BrowserHub attribution rewrite" \
  '! grep -rq "based on Titanium Browser and Chromium" patch.sh features/'
check "launcher name left as Titanium/Chrome (upstream behaviour)" \
  '! grep -rq ">BrowserHub<" patch.sh features/'
check "upstream app icon restored" \
  '! grep -q "BrowserHub" res/icon.svg'
check "store listing is upstream text" \
  '! grep -q "BrowserHub" fastlane/metadata/android/en-US/full_description.txt'

echo "== All Titanium features present =="
check "22 feature fragments exist" \
  '[ "$(ls features/*.sh 2>/dev/null | wc -l)" -eq 22 ]'
check "every fragment is sourced by patch.sh" \
  'grep -qF "for f in \"\$FEATURES_DIR\"/*.sh" patch.sh'
check "features default to ON (stock Titanium)" \
  'grep -q "!= \"0\"" patch.sh'
check "core cannot be silently missing" \
  '[ -f features/core.sh ]'
check "manifest describes every feature" \
  'python3 -c "
import json,glob,os
m=json.load(open(\"features/manifest.json\"))
keys={x[\"key\"] for x in m}
files={os.path.basename(p)[:-3] for p in glob.glob(\"features/*.sh\")}
assert keys==files, (keys^files)
"'
check "every fragment has a non-empty description" \
  'python3 -c "
import json
m=json.load(open(\"features/manifest.json\"))
assert all(x[\"desc\"].strip() for x in m)
"'

echo "== Feature statements are intact =="
check "fragment statements total 120 (upstream count)" \
  'python3 -c "
import glob
n=0
for p in glob.glob(\"features/*.sh\"):
    for l in open(p):
        s=l.strip()
        if s and not s.startswith(\"#\"): n+=1
assert n==120, n
"'
check "no fragment is empty" \
  'python3 -c "
import glob,sys
for p in glob.glob(\"features/*.sh\"):
    body=[l for l in open(p) if l.strip() and not l.lstrip().startswith(\"#\")]
    assert body, p
"'

echo "== Scripts parse =="
check "patch.sh parses" 'bash -n patch.sh'
check "feature-picker.sh parses" 'bash -n feature-picker.sh'
check "build.sh parses" 'bash -n build.sh'
check "every fragment parses" \
  'for f in features/*.sh; do bash -n "$f" || exit 1; done'
check "build.sh loads a saved selection" \
  'grep -q "features/enabled.env" build.sh'

echo "== Submodule pin =="
# The vanadium gitlink is easy to lose: copying the tree with tar --exclude
# .git drops it silently, and .gitmodules alone is not enough. build.sh reads
# vanadium/args.gn on line 4, so without it the build fails immediately.
check "vanadium gitlink is committed at upstream's pin" \
  'git ls-files -s vanadium 2>/dev/null | grep -q "^160000 a8b304ef63b51f0158775a9d4f90106e8fc9a721"'
check "git recognises the submodule mapping" \
  '! git submodule status 2>&1 | grep -q "no submodule mapping"'
check ".gitmodules is at the repository root" \
  '[ -f ../.gitmodules ]'

echo "== Build kit is actually committed =="
# Upstream's .gitignore excludes keys/, so anything placed there never ships.
# Check every deliverable is tracked rather than silently missing.
for f in build-elsewhere.md Dockerfile.build local.properties.example \
         verify-identity.sh feature-picker.sh; do
    check "$f is not gitignored" "! git check-ignore -q '$f' 2>/dev/null"
done
check "signing template matches names sign_apk reads" \
  'grep -q "^storePassword=" local.properties.example && grep -q "^keyPassword=" local.properties.example && grep -q "^keyAlias=" local.properties.example'

echo
if [ "$fail" -eq 0 ]; then
  echo "verify: $pass checks pass — stock Titanium with a switchable feature layer"
  exit 0
fi
echo "verify: $pass passed, $fail FAILED"
exit 1
