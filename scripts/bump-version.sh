#!/usr/bin/env bash
# Bump the app version: sets versionName to the argument and auto-increments
# versionCode, then commits the change. Does not push or tag.
#
# Usage:  ./scripts/bump-version.sh <versionName>
# Example: ./scripts/bump-version.sh 1.6
set -euo pipefail

cd "$(dirname "$0")/.."
GRADLE="app/build.gradle.kts"

new_name="${1:-}"
if [ -z "$new_name" ]; then
  echo "usage: $0 <versionName>   e.g. $0 1.6" >&2
  exit 1
fi

cur_code=$(grep -oE 'versionCode = [0-9]+' "$GRADLE" | grep -oE '[0-9]+' | head -1)
cur_name=$(grep -oE 'versionName = "[^"]+"' "$GRADLE" | sed -E 's/.*"([^"]+)".*/\1/' | head -1)
if [ -z "$cur_code" ] || [ -z "$cur_name" ]; then
  echo "error: could not read current versionCode/versionName from $GRADLE" >&2
  exit 1
fi
new_code=$((cur_code + 1))

if [ "$new_name" = "$cur_name" ]; then
  echo "error: versionName is already \"$new_name\" — nothing to bump" >&2
  exit 1
fi

perl -i -pe "s/versionCode = ${cur_code}\b/versionCode = ${new_code}/" "$GRADLE"
perl -i -pe 's/versionName = "[^"]*"/versionName = "'"${new_name}"'"/' "$GRADLE"

echo "versionName ${cur_name} -> ${new_name}"
echo "versionCode ${cur_code} -> ${new_code}"

git add "$GRADLE"
git commit -m "Bump version to ${new_name} (versionCode ${new_code})"

cat <<EOF

Committed. To cut the release:
  git push origin main
  git tag -a v${new_name} -m "Tacklebox ${new_name}" && git push origin v${new_name}
EOF
