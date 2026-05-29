# Calcora

An Android scientific calculator and computer algebra system (CAS) powered by the [Giac/Xcas](https://www-fourier.ujf-grenoble.fr/~parisse/giac.html) engine.

## Features

- **Symbolic algebra** — simplification, expansion, factorization, equation solving, limits, differentiation, integration, and more
- **Arbitrary-precision arithmetic** — exact fractions, big integers, high-precision floating point
- **2D / 3D plotting** — interactive plots with pinch-to-zoom, drag-to-pan, and 3D rotation
- **CAS terminal** — direct Xcas/Giac command-line interface with full engine access
- **Script editor** — write, save, load, and run Xcas scripts with syntax highlighting
- **Comprehensive help system** — searchable offline reference for all Giac functions with descriptions, related functions, and examples
- **Smart autocomplete** — function name suggestions while typing
- **Monet dynamic theming** — colors adapt to your device wallpaper (Android 12+)
- **i18n** — English and Chinese UI, with Giac backend language support

## Screens

| Calc | Help | Script | Settings |
|------|------|--------|----------|
| Expression input, history scroll, mode switching (Auto/Exact/Approx/Raw), variable panel, function panel, plot viewing | Searchable function reference with descriptions, related commands, and examples | Xcas script editor with syntax highlighting, save/load, and output panel | Language, theme, angle unit, precision, history limit, and more |

## Build

### Requirements

- Android Studio (latest stable)
- NDK 30.0+
- Kotlin 2.x
- JDK 17+

### Steps

```bash
git clone git@github.com:jsjsjsjsjsjsjson/Calcora.git
cd Calcora
./gradlew assembleDebug    # debug build (-O2 native)
./gradlew assembleRelease  # release build (-O3 native, minified)
```

The project bundles a subset of the [Giac 2.0.0](https://www-fourier.ujf-grenoble.fr/~parisse/giac.html) C++ source tree under `giac-2.0.0/` and builds it via CMake in `app/src/main/cpp/CMakeLists.txt`. Only `arm64-v8a` is targeted.

## License

This project's original code is licensed under the MIT License.

Giac/Xcas is © Bernard Parisse and contributors, licensed under GPLv3. See `giac-2.0.0/COPYING`.

---

*by [libchara-dev](https://github.com/jsjsjsjsjsjsjson)* qwq
