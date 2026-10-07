#!/usr/bin/env bash
# Copies the macOS and Windows libraries of the upstream 0.27.3 release into the Java resources.
# They are built from the same revision (tag v0.27.3) and the same toolchain (Rust 1.69.0) as the
# Linux libraries of trdc/build-linux-lib.sh. The checksum pins the jar on Maven Central.
set -euo pipefail

URL="https://repo1.maven.org/maven2/com/osohq/oso/0.27.3/oso-0.27.3.jar"
SHA256="444941211d8ed7a8d560a79bc0fc8a8e67b72342a4665c3636d1ef78baab76dc"

root="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

curl --fail --silent --show-error --location --output "$work/oso.jar" "$URL"
echo "$SHA256  $work/oso.jar" | sha256sum --check --status 2>/dev/null \
  || echo "$SHA256  $work/oso.jar" | shasum -a 256 --check --status

resources="$root/languages/java/oso/src/main/resources"
mkdir -p "$resources"
unzip -o -q "$work/oso.jar" 'macos/libpolar.dylib' 'win/polar.dll' -d "$resources"
ls "$resources/macos/libpolar.dylib" "$resources/win/polar.dll"
