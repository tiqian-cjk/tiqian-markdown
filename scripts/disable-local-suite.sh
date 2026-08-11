#!/usr/bin/env bash
set -euo pipefail

markdown_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
config="$markdown_dir/.tiqian-local.properties"

rm -f "$config"
printf 'Tiqian local suite disabled; dependencies now resolve without Maven Local.\n'
