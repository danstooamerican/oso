#!/usr/bin/env bash
# Builds or tests the Rust core for one Linux architecture in the pinned Rust image. CI runs it on
# a host of that architecture.
#
#   trdc/linux-lib.sh build x86_64|aarch64   libpolar.so into languages/java/oso/src/main/resources/linux/<arch>/
#   trdc/linux-lib.sh test  x86_64|aarch64   the tests of polar-core and polar-c-api
set -euo pipefail

usage="usage: $0 build|test x86_64|aarch64"
command="${1:?$usage}"
arch="${2:?$usage}"

# rust:1.69.0-slim-bullseye: the toolchain of the upstream 0.27.3 release, on glibc 2.31.
# The digests are the platform manifests, not the index: a Docker daemon with the classic image
# store keeps only one platform of an index digest.
case "$arch" in
  x86_64)
    platform=linux/amd64
    digest=sha256:2522978d04d670d70389ee2d91ae7d266f622eb619560f6a242ee4c5544a39c5
    ;;
  aarch64)
    platform=linux/arm64
    digest=sha256:64b11e12e14c9cafaa4361b544fb50df754a3df4f71bf6ed2af50edc8494d380
    ;;
  *) echo "$usage" >&2; exit 1 ;;
esac

root="$(cd "$(dirname "$0")/.." && pwd)"
target_dir="target/linux-$arch"

cargo() {
  docker run --rm --platform "$platform" \
    --user "$(id -u):$(id -g)" \
    --volume "$root:/src" --workdir /src \
    --env CARGO_HOME=/src/target/cargo-home \
    "rust:1.69.0-slim-bullseye@$digest" \
    cargo "$@" --locked --target-dir "$target_dir"
}

case "$command" in
  build)
    cargo build --release -p polar-c-api
    out="$root/languages/java/oso/src/main/resources/linux/$arch"
    mkdir -p "$out"
    cp "$root/$target_dir/release/libpolar.so" "$out/libpolar.so"
    echo "$out/libpolar.so"
    ;;
  test)
    cargo test --all-targets --all-features -p polar-core -p polar-c-api
    ;;
  *) echo "$usage" >&2; exit 1 ;;
esac
