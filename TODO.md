# To do

Current build: **v1.10.10-test6** (version code 47). Desktop regression tests and APK signature/alignment verification pass. Camera validation is tracked separately below.

## Priority: validate DCIM export and Imaging Edge Mobile

- [ ] On the a6500, develop a new photo and confirm that the copy is in its separate numbered FILMS folder under DCIM, displays in FilmSim, and leaves the original unchanged.
- [x] Confirm manual DSC renaming plus database recovery makes a developed copy visible and transferable in Imaging Edge.
- [ ] Verify test6 EXIF dimensions and developed thumbnail, and test magnification on camera.
- [ ] Take another normal photo after recovery to check the camera recording-folder selection and numbering.
- [ ] Check normal camera playback and Imaging Edge Mobile transfer before and after Recover Image DB. Eight-character filenames and DCIM placement do not establish Sony database compatibility; automatic registration is not implemented.
- [ ] If copies remain invisible, investigate verified Sony image registration/export APIs and DCF numbering requirements before claiming wireless-transfer support.
- [ ] Verify old flat/nested FILMSIM/DEV copies still appear in FilmSim playback. Old files are not migrated.

## Priority: validate the new shooting controls on the a6500

- [ ] Install test4 and confirm that holding AF/MF keeps manual focus active through half-shutter and capture, even with Fn Focus mode set to Auto. Half-shutter must cancel magnification without starting autofocus; releasing AF/MF must restore Auto.
- [ ] Confirm Fn Focus mode Manual prevents half-shutter autofocus, survives playback/reopen within the session, and can be switched back to Auto.
- [ ] Check focus-ring magnification while AF/MF is held, release/half-shutter cancellation, and suppression of further zoom until the button is released and pressed again. Verify the actual magnification ratio; 5.9x is not yet confirmed.
- [ ] Verify AF/MF?AEL selector event codes and held AEL behavior, including restoration of an already locked exposure. Unknown selector codes are logged rather than guessed.
- [ ] Verify each advertised metering mode, focus area and AF mode on the camera.
- [ ] Check whether native peaking level and colour are exposed, whether peaking stays on throughout the AF/MF hold, and whether the original settings are restored on camera close. Unsupported peaking currently shows Unavailable; no software fallback exists.
- [ ] Exercise playback, recording, selector changes and app exit during/after holds to check state restoration.

## Development performance and reliability

- [ ] Measure on-camera development time for Small/Large copies with grain, halation and light leaks separately and together. Separate source-file waiting, decoding, effects and encoding before choosing further optimizations.
- [ ] Validate capture matching under slow-card writes and repeated shots, RAW-only and RAW+JPEG, duplicate photo numbers in different DCIM folders, and ambiguous matches. Desktop tests cover matching and rejection, but do not reproduce camera timing.
- [ ] Check copy-name exhaustion, card-full/write failures and existing developed versions on camera. Keep new copies in their separate FILMS folder under DCIM and preserve existing files.

## Other outstanding work

- [ ] Verify RAW format selection on camera using `raw rec modes` / `raw rec mode set` logs and ARW file sizes. The earlier v1.10.9 notes did not confirm this check.
- [ ] Improve Spektrafilm colour fitting: the softened tone is implemented, but the looks still rate one star and lose colour richness in the camera curve/matrix approximation.
- [ ] Consider movable flexible focus spots; the current Focus area row selects a mode only.
- [ ] Decide whether to persist the new shooting-control choices across app restarts. They currently survive camera reopenings within the same activity session only.

Spektrafilm LUT generation still requires the external spektrafilm checkout and its Python dependencies; the LUT sources are not included here. See `tools/spektrafilm/bake.py` and the README. Further tone tuning should be judged on full-size photos and on camera, including brightness and shadow detail.

## Completed: v1.10.10-test6

- [x] Separate numbered FILMS folder per source folder, DSC-style filenames, persistent reservations and source mappings; never overwrite originals or versions.
- [x] Folder-aware playback lookup avoids confusing identical basenames; mapped copies are excluded from capture detection while unmapped camera originals remain detectable.
- [x] Rebuild EXIF with correct dimensions and a thumbnail of the finished effects on both processing paths. Retain standard capture details and orientation, omit stale MakerNotes/preview references.
- [x] Desktop regression coverage for naming, source lookup, folder exhaustion, capture exclusion, EXIF byte orders, dimensions, thumbnail decoding and metadata preservation. On-camera tests remain pending above.

## Completed: v1.10.10-test5 (superseded output layout)

- [x] Save new copies beside their originals in DCIM with eight-character basenames and non-overwriting version slots. Preserve legacy DEV playback lookup.
- [x] Exclude reserved developed-copy filenames from capture snapshots so background output is not mistaken for a new shutter capture. Tests cover output folders, names, duplicate numbers across folders and capture exclusion.

## Completed: v1.10.10-test4 and recent fixes

- [x] Fix half-shutter restoring autofocus during an AF/MF hold. Half-shutter and capture now cancel only focus zoom; held MF and peaking remain active until release. Regression coverage includes repeat events, stale/current ring callbacks, capture cancellation and restoration. Camera confirmation remains pending above.
- [x] Add Fn Metering, Focus area, AF mode, Focus peaking, Peaking colour and Auto/Manual Focus mode using camera-advertised capabilities.
- [x] Add held AF/MF manual-focus assist, ring-triggered native magnification and held AEL. Avoid toggling an already manual-focus camera into AF; restore prior state on release/close.
- [x] Fix black developed JPEGs by preserving opaque alpha in effect pixels. Confirmed working by the user on a6500 firmware 1.04; effect regression tests cover alpha.
- [x] Snapshot effect settings per capture, pair RAW/JPEG sources, wait for stable source metadata, and reject ambiguous capture matches.
- [x] Reserve available copy names and publish copies without overwriting existing outputs.
- [x] Restore flat `FILMSIM/DEV/` output (subsequently superseded by DCIM output in test5) after the per-photo folder regression; confirmed working by the user. Existing nested copies retain playback lookup support.
- [x] Decode developed-copy previews at bounded screen size on a background worker, avoiding full-size decoding/rotation on the UI thread.
- [x] Add reproducible Windows/Linux setup and builds with pinned dependencies, desktop regression tests, APK verification and GitHub Actions artifacts. See `docs/build.md`.

## Completed: v1.10.9

- [x] Soften and integrate Spektrafilm looks using BLEND 0.45 and FLOOR 0.025; the previous notes record that the user liked the result. This supersedes the old pending ConvertFilms/rebuild task.
- [x] Show the embedded ARW preview for plain RAW playback, preserving the saved look rather than using Sony's fresh RAW rendering.
- [x] Show Kelvin as off unless Kelvin white balance is selected.
- [x] Add the RAW format Fn row through `setRawRecMode`, defaulting to Uncompressed; hardware validation remains listed above.
- [x] Skip the identity-look pass in plain RAW playback.
