#!/usr/bin/env bash
# Runs a command in the pinned Java 25 image of one Linux architecture, with the repository at /w.
# Java 25 is the runtime of the application that uses this fork.
#
#   trdc/java.sh x86_64|aarch64 mvn -B -f languages/java/oso package
set -euo pipefail

usage="usage: $0 x86_64|aarch64 command..."
arch="${1:?$usage}"
shift

# maven:3.9-eclipse-temurin-25, index sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976
case "$arch" in
  x86_64)
    platform=linux/amd64
    digest=sha256:96f175708f766ea049ab6c794a85335d238894c75f8eb364bddd98086a5b7cfd
    ;;
  aarch64)
    platform=linux/arm64
    digest=sha256:4908705d2e22261d4ee0aa74f2ecdabf7c034fd49f1aa15e262e4220f1d6ef06
    ;;
  *) echo "$usage" >&2; exit 1 ;;
esac

root="$(cd "$(dirname "$0")/.." && pwd)"
docker run --rm --platform "$platform" \
  --volume "$root:/w" --workdir /w \
  --volume "oso-m2-$arch:/root/.m2" \
  "maven@$digest" "$@"
