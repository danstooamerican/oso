package com.osohq.oso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class FfiTest {
  @Test
  public void selectsTheLinuxLibraryOfTheCpuArchitecture() {
    assertEquals("linux/x86_64/libpolar.so", Ffi.nativeLibraryPath("Linux", "amd64"));
    assertEquals("linux/x86_64/libpolar.so", Ffi.nativeLibraryPath("Linux", "x86_64"));
    assertEquals("linux/aarch64/libpolar.so", Ffi.nativeLibraryPath("Linux", "aarch64"));
    assertEquals("linux/aarch64/libpolar.so", Ffi.nativeLibraryPath("Linux", "arm64"));
  }

  @Test
  public void keepsOneLibraryForMacOsAndWindows() {
    assertEquals("macos/libpolar.dylib", Ffi.nativeLibraryPath("Mac OS X", "aarch64"));
    assertEquals("macos/libpolar.dylib", Ffi.nativeLibraryPath("Mac OS X", "x86_64"));
    assertEquals("win/polar.dll", Ffi.nativeLibraryPath("Windows 11", "amd64"));
  }

  @Test
  public void rejectsAnUnsupportedLinuxArchitecture() {
    assertThrows(UnsatisfiedLinkError.class, () -> Ffi.nativeLibraryPath("Linux", "ppc64le"));
  }

  @Test
  public void bundlesTheLibraryOfTheRunningPlatform() {
    String path =
        Ffi.nativeLibraryPath(System.getProperty("os.name"), System.getProperty("os.arch"));
    assertEquals(true, Ffi.class.getClassLoader().getResource(path) != null, path);
  }
}
