# Build and repository guide

## Source map

- `app/src/com/filmsim/app/FilmSimActivity.java`: camera UI, controls, capture, and playback integration.
- `FilmLibrary.java`, `FilmLook.java`, `CustomLuts.java`: bundled looks and SD-card LUT loading.
- `Cube.java`, `Decomposer.java`, `LutParams.java`: LUT parsing and fitting to camera curves and matrices.
- `Develop.java`, `Developer.java`, `ArwPreview.java`: image effects, background processing, and RAW previews.
- `app/assets/films.txt` and `app/assets/films/`: checked-in catalog and 238 precomputed looks; normal builds do not regenerate them.
- `probe/`: independent APK for inspecting Sony camera colour-pipeline support.
- `tools/src/`: desktop Java LUT conversion, look design, preview rendering, and image-processing harness.
- `tools/spektrafilm/bake.py`: optional LUT regeneration using an external Spektrafilm checkout and its own Python environment.

## Local builds

Python 3.12+ on Windows/Linux x64 is the only bootstrap requirement:

```text
python scripts/build.py setup
python scripts/build.py all
```

Dependencies are pinned in `scripts/dependencies.json`. Setup verifies downloads before extraction, preserves existing installations, and assembles the compile-only Sony API jar with Jasmin 2.4 (the newer Sable fork uses incompatible syntax). SDK archives use Google's published SHA-1 checksums; other archives use SHA-256. Downloads and extracted dependencies stay under `.deps/`; delete that directory to reinstall the toolchain. To update dependency versions, update their URLs and checksums together and reinstall. Upstream licenses remain in the extracted packages.

Individual targets: `app`, `probe`, `tools`. Each cleans only its module's `build/` directory. APKs stay at `app/FilmSim.apk` and `probe/FilmProbe.apk`; compiled desktop classes are in `tools/build/`. Signing keys are created only if absent, and excluded from version control. Back them up if you need to update an existing installation without changing its signing identity.

The shared build uses Java 8 bytecode, the API 10 boot classpath, D8 with minimum API 10, and v1 APK signing. Sony stubs are compile-only and are not packaged into the apps. No camera is needed to build. A successful build verifies packaging and signatures, not camera hardware behavior.

## Desktop smoke checks

After building, render a bundled film contact sheet on Windows:

```powershell
.\.deps\jdk\bin\java.exe -Djava.awt.headless=true -cp tools/build com.filmsim.tools.Preview app/assets Cine tools/build/cine-preview.png
```

On Linux, use `.deps/jdk/bin/java` instead. `DevelopTest` can process an input JPEG using the actual camera development implementation:

```text
java -cp tools/build com.filmsim.tools.DevelopTest input.jpg output.jpg 2 halation grain2 seed=1
```

Use the local JDK's `java` executable if Java is not on PATH. See the README for conversion commands. Full asset regeneration additionally needs FilmCamera's source LUTs and, for the Spektrafilm section, the external Spektrafilm environment. Those optional inputs are not required to build the checked-in camera apps.

## CI

`.github/workflows/build.yml` builds on Windows and Linux, caches the pinned toolchain, verifies both APKs, and uploads debug APK artifacts. It does not publish releases or install apps on a camera. CI keys are ephemeral; use a separately managed signing workflow for distributable updates.
