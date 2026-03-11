#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_REPO="$ROOT_DIR/.mvn/local-repo"
SOURCE_REPO="${1:-$HOME/.m2/repository}"

mkdir -p "$TARGET_REPO"

if [ ! -d "$SOURCE_REPO" ]; then
  echo "Source Maven repository not found: $SOURCE_REPO" >&2
  exit 1
fi

rsync -a --ignore-existing "$SOURCE_REPO/" "$TARGET_REPO/"

echo "Seeded repo-local Maven cache: $TARGET_REPO"
