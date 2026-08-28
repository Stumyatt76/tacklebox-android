#!/usr/bin/env bash
# Fails if any Kotlin source file is missing the proprietary copyright header.
# The header marker is the phrase "All rights reserved" within the first few lines.
set -euo pipefail

cd "$(dirname "$0")/.."

MARKER="All rights reserved"
SEARCH_DIRS=(app/src)
missing=()

while IFS= read -r -d '' f; do
  if ! head -n 5 "$f" | grep -q "$MARKER"; then
    missing+=("$f")
  fi
done < <(find "${SEARCH_DIRS[@]}" -name '*.kt' -print0 2>/dev/null)

if [ "${#missing[@]}" -gt 0 ]; then
  echo "✗ Missing copyright header in ${#missing[@]} file(s):" >&2
  printf '  %s\n' "${missing[@]}" >&2
  echo >&2
  echo "Add this block above the package declaration:" >&2
  echo "  /*" >&2
  echo "   * Copyright (c) 2026 Stuart Myatt. All rights reserved." >&2
  echo "   * Proprietary — source is public for reference only. See LICENSE at the repository root." >&2
  echo "   */" >&2
  exit 1
fi

echo "✓ All Kotlin source files carry the copyright header."
