# oso for TRDC: Linux aarch64

This branch is upstream tag `v0.27.3` plus the changes that make the Java binding run on Linux
aarch64 (AWS Graviton). The Rust core and the Java binding come from the same revision.

## The limitation

The upstream jar `com.osohq:oso:0.27.3` holds one Linux library, `linux/libpolar.so`, built for
x86-64. `Ffi` selected it by `os.name` only. On Linux aarch64 the JVM therefore loaded an x86-64
library and failed with `UnsatisfiedLinkError`. The upstream release workflow already built
`libpolar-aarch64.so` for the Ruby gem, but it did not put it into the jar.

The other parts already support aarch64: the Rust core builds and passes its tests, and the
shaded `jnr-ffi` 2.2.11 / `jffi` 1.3.9 hold `jni/aarch64-Linux/libjffi-1.2.so`.

## The changes

- `Ffi` selects `linux/<arch>/libpolar.so` by `os.arch` (`amd64`/`x86_64` → `x86_64`,
  `aarch64`/`arm64` → `aarch64`). An unsupported architecture, a missing resource, or a failed
  extraction throws `UnsatisfiedLinkError` with the path. Upstream ignored an extraction failure
  and failed later with a `NullPointerException`.
- The jar holds `linux/x86_64/libpolar.so` and `linux/aarch64/libpolar.so`, both built by
  `trdc/linux-lib.sh` with Rust 1.69.0 (the toolchain of the upstream release) on Debian
  bullseye. They need glibc 2.28 or later. musl (Alpine) is not supported.
- `macos/libpolar.dylib` and `win/polar.dll` are the files of the upstream jar, pinned by its
  SHA-256 (`trdc/fetch-upstream-libs.sh`).
- The coordinates are `de.tailord.oso:oso:0.27.3-trdc.1`. The Sonatype signing and staging
  plugins are gone; the jar carries the Oso license as `META-INF/oso/LICENSE`.
- The bytecode level stays at Java 8, as upstream. The build and the tests use Java 25, the
  runtime of the application.

## Build and test

Docker is the only requirement. Each script pins its image by digest.

```bash
trdc/linux-lib.sh test  aarch64        # cargo test of polar-core and polar-c-api
trdc/linux-lib.sh build aarch64        # → languages/java/oso/src/main/resources/linux/aarch64/
trdc/linux-lib.sh build x86_64
trdc/fetch-upstream-libs.sh            # macOS and Windows libraries
trdc/java.sh aarch64 mvn -B -f languages/java/oso package
trdc/check-jar.sh languages/java/oso/target/oso-0.27.3-trdc.1.jar
trdc/java.sh aarch64 java -cp languages/java/oso/target/oso-0.27.3-trdc.1.jar trdc/smoke/Smoke.java
```

On a host of the other architecture Docker emulates the container. That is good for
development; the workflow `trdc java` runs each architecture on a native runner
(`ubuntu-24.04`, `ubuntu-24.04-arm`) and compares the decision transcripts of `Smoke.java`.

## Release

1. Set the version in `languages/java/oso/pom.xml` (`<!-- oso_version -->`).
2. Push the tag `trdc-v<version>`, for example `trdc-v0.27.3-trdc.1`. The workflow attaches
   `oso-<version>.jar`, `oso-<version>-sources.jar`, `oso-<version>.pom`, and `SHA256SUMS` to a
   GitHub release.
3. In TRDC, set the version and the checksums in the job `oso-publish`
   (`.gitlab/ci/app.gitlab-ci.yml`) and run it. It copies the verified files into the Maven
   registry of the TRDC project.

## Update from upstream

The Java binding of upstream ends at 0.27.3 (the library is deprecated). For a new upstream tag:
create a branch from the tag, cherry-pick the commits of this branch, set the Rust toolchain
of the upstream release workflow in `trdc/linux-lib.sh`, the checksum of the new upstream jar in
`trdc/fetch-upstream-libs.sh`, and a new version `<upstream>-trdc.1`.

## Roll back

Set the dependency in TRDC back to `com.osohq:oso:0.27.3` and the ECS CPU architecture to
`X86_64`. The Java API and the policies are the same.
