# Third Party Notices

## Giac/Xcas

The project includes the `giac-2.0.0` source tree and builds selected Giac core sources into the Android native library.

- Project: Giac/Xcas
- Author: Bernard Parisse and contributors
- License files in this repository: `giac-2.0.0/COPYING`, `giac-2.0.0/src/COPYING`
- Integration status: Android `arm64-v8a` debug builds link the Giac native core through CMake/JNI. The source tree also remains available for future backend work.

## AndroidX and Jetpack Compose

CalculatorPlus uses AndroidX, Jetpack Compose, and Material 3 libraries through Gradle dependencies. These components are distributed under their respective Android Open Source Project licenses, primarily Apache License 2.0.

## LibTomMath

The Android native Giac build uses LibTomMath 1.3.0 for Giac's `USE_GMP_REPLACEMENTS` path.

- Project: LibTomMath
- Source: `third_party/libtommath-1.3.0`
- License file: `third_party/libtommath-1.3.0/LICENSE`
