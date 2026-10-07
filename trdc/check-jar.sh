#!/usr/bin/env bash
# Fails if the jar lacks a native library, holds one of the wrong architecture, or lacks the license.
set -euo pipefail

jar="${1:?usage: $0 path/to/oso.jar}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
unzip -q "$jar" 'linux/*' 'macos/*' 'win/*' 'META-INF/oso/LICENSE' -d "$work"

expect() {
  local description
  description="$(file -b "$work/$1")"
  case "$description" in
    *"$2"*) echo "$1: $2" ;;
    *) echo "$1: expected $2, found: $description" >&2; exit 1 ;;
  esac
}

expect linux/x86_64/libpolar.so "ELF 64-bit LSB shared object, x86-64"
expect linux/aarch64/libpolar.so "ELF 64-bit LSB shared object, ARM aarch64"
expect macos/libpolar.dylib "Mach-O universal binary with 2 architectures"
expect win/polar.dll "PE32+ executable"
test ! -e "$work/linux/libpolar.so"
grep -q "Apache License" "$work/META-INF/oso/LICENSE"
echo "META-INF/oso/LICENSE: Apache License 2.0"
