#!/bin/bash
#
# Titanium feature dispatcher.
#
# Upstream Titanium applied all of its edits in one 185-line script. Here the
# same edits live in features/*.sh, one file per feature area, and this script
# sources the ones you have enabled. Every statement in features/ is verbatim
# upstream — the split was verified statement-for-statement against the
# original patch.sh, so an unmodified build is byte-identical to Titanium.
#
#   ./build.sh                     # every feature on = stock Titanium
#   ENABLE_EXT_MV2=0 ./build.sh    # everything except Manifest V2 support
#   ./feature-picker.sh            # same thing, interactively
#
# A feature that is off contributes nothing: its file is simply not sourced.

FEATURES_DIR="$SCRIPT_DIR/features"

# Enabled unless explicitly set to 0. Stock behaviour is "all on", so a build
# with no environment set is exactly Titanium.
feature_on() {
    local var="ENABLE_$(echo "$1" | tr 'a-z-' 'A-Z_')"
    [ "${!var:-1}" != "0" ]
}

if [ ! -d "$FEATURES_DIR" ]; then
    echo "patch.sh: missing $FEATURES_DIR — cannot apply features" >&2
    exit 1
fi

applied=0
skipped=0

for f in "$FEATURES_DIR"/*.sh; do
    [ -e "$f" ] || { echo "patch.sh: no feature files found in $FEATURES_DIR" >&2; exit 1; }
    key=$(basename "$f" .sh)
    if feature_on "$key"; then
        # shellcheck disable=SC1090
        source "$f"
        applied=$((applied + 1))
    else
        echo "patch.sh: skipped $key"
        skipped=$((skipped + 1))
    fi
done

echo "patch.sh: $applied feature blocks applied, $skipped disabled"
