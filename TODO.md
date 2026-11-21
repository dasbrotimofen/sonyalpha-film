# To do

Current build: **v1.10.10-test14** (version code 55). Camera validation is tracked separately below.

## Test14: remove refresh experiments and reduce development work

- [x] Remove MediaReload and all activity scheduling, timeout/polling and post-save wait messaging. Retain normal gallery queries; automatic registration remains unresolved.
- [x] Allocate only grain tables used by the selected effects: omit float tiles without halation/leaks and chroma tiles for monochrome. Produce integer values in the existing preparation pass. Verify exact output against 32 SHA-256 pixel fixtures captured from test13, covering all grain levels, mono/colour, halation/leak combinations, tile wrapping and a partial final strip.
- [x] Use one getPixels call per streaming strip with the same edge padding and guaranteed recycling. Add separate metadata/thumbnail and publication timings.
- [ ] Compare on-camera development times and battery usage with test13 under matched settings. Reduced allocation is verified by code, not a measured battery-life improvement. Native/streaming execution still needs hardware validation.
- [ ] Profile the slow native decode stage before changing decoder selection or mutable-bitmap strategy. Keep the confirmed thumbnail/EXIF path until new stage timings justify changing it.

## Test13: Sony database-update experiment

- [x] Confirm media-ID argument from the decompiled Digital Filter `AvindexStoreFiles` adapter. Replace loadMedia with runtime-checked `Files.waitAndUpdateDatabase(ContentResolver, String)`; log missing API, invocation errors, return value and index presence independently.
- [x] Retain background execution, pause cancellation, 12-second observation watchdog and single in-flight native-call guard. Observation timeout does not terminate a native call; no further request starts until it returns. JPEG output is unchanged.
- [ ] Validate on a6500 FW1.04: one developed photo, wait 15 seconds, check gallery before recovery and then after recovery, without USB or card reinsertion. Inspect LOG.TXT afterward. Desktop tests cannot establish Sony runtime behavior.
- [x] Test13 failed to resolve visibility: DSC04891.JPG saved in 117FILMS; Files.waitAndUpdateDatabase was available and returned true immediately for media 1000, but the copy was still absent from the queried Sony index after ten seconds. User confirmed gallery visibility also failed after manual database recovery. Neither this call nor test12's loadMedia reproduces the successful card-reinsertion refresh.

## Test12: Sony media-load experiment

- [x] Request `AvindexStore.loadMedia(mediaId, CONTENT_TYPE_LOAD_STILL)` on a background thread after the development queue empties and capture/video are idle. Check exact DCF folder/file numbers in the index and log outcomes. Query failures do not prevent the load request.
- [x] Bound observation to 12 seconds, cancel observation on pause, and prevent overlapping native requests even if a call blocks. Do not use the native unbounded wait, unmount storage, or modify the database directly. Saved JPEGs remain intact on refresh errors.
- [ ] Confirm gallery behavior before recovery and after recovery without USB, card reinsertion or battery removal. Index presence alone does not prove gallery display. Hardware execution cannot be validated by desktop tests.
- [x] Test12 camera result: copies DSC04889 and DSC04890 were saved in 115FILMS. For both, the log reports `indexed before=false, media=1000`, `loadMedia returned true`, then `copy not found in Sony index` after ten seconds. User confirmed neither normal playback nor subsequent database recovery revealed them before USB connection. The load API call ran but did not solve discovery; investigate external-volume scanning/media remount behavior rather than repeating loadMedia alone.
- [x] Test11 user result: recovery alone was insufficient; USB disconnect, battery removal or SD-card reinsertion made copies visible. Waiting one or five minutes did not substitute for media reset. This narrows the investigation to media state but does not identify the exact cache/service.

## Test11: direct JPEG publication experiment

- [x] Replace final TMP-to-JPG rename with exclusive creation of the final JPG, byte-for-byte copy, flush, file-descriptor sync and close. Remove the temporary only after successful publication; remove partial output on failure and report cleanup failure if it cannot be removed.
- [x] Add regression coverage for multiple copy buffers, collision protection, no rename, injected read failure after a partial write, cleanup and retry. Log the direct JPG publication method on successful development.
- [ ] On camera, take one new photo, wait for the saved message, exit and recover the image database. Confirm the developed copy appears without a PC copy. This is an experiment, not confirmed gallery compatibility.

## Completed: v1.10.10-test10

- [x] Address test9's `Cannot read image numbers in .../106FILMS` allocation failure. Null listings in FilmSim-owned output folders now use direct candidate-file and reservation checks; unreadable unowned folders still fail safely.
- [x] Add regression coverage for null listings, ownership, occupied JPEG/RAW/temporary files and reservations. Build and verify the signed APK.
- [x] Test10 development succeeds on the a6500: two 1500x1000 copies and their 160x120 / 4:2:2 thumbnails decode independently. EXIF dimensions match the main image.
- [ ] Resolve automatic output remaining invisible after Recover Image DB, including after card formatting. Local `build/gallery-test10.zip` contains unchanged copies in fresh folders, a folder with an original control, and the previously visible developed diagnostic file to distinguish JPEG compatibility from file/folder registration.
- [x] User confirms all five test10 diagnostic files are visible after PC copying/recovery, including a folder containing only developed copies. The current JPEG bytes can display; an original camera JPEG is not needed in the same folder. App publication currently renames `.TMP` to `.JPG` without media registration. Investigate publication/registration and folder state next; this test also changed names and folder numbers, so it does not prove rename is the cause.
- [ ] Revisit fresh-number allocation: it avoids existing numbers but does not reserve numbers in Sony's own capture counter. On test10 the next original reused 04879 in a different folder. There was no overwrite; copy 04880 was also invisible despite having no duplicate, so this alone does not explain the failure.

## Priority: validate DCIM export and Imaging Edge Mobile

- [ ] On the a6500, develop a new photo and confirm that the copy is in its separate numbered FILMS folder under DCIM, displays in FilmSim, and leaves the original unchanged.
- [x] Confirm manual DSC renaming plus database recovery makes a developed copy visible and transferable in Imaging Edge.
- [x] Verify corrected EXIF/thumbnail and test magnification: originals magnify, developed diagnostic copies display without magnification; accepted limitation.
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

## Completed: v1.10.10-test9

- [x] Retain tested test8 JPEG format; no speculative metadata changes after all diagnostic variants displayed.
- [x] Allocate fresh image numbers across DCIM and persistent FilmSim reservations, matching the successful diagnostics more closely. Preserve source mappings and non-overwrite checks.
- [x] Show output folder/filename and database recovery reminder after development; document original-only magnification.
- [ ] Confirm repeated automatic save/recovery cycles with test9. Diagnostic copies worked, but the cause of earlier same-number/folder playback failures remains unproven.

## Completed: v1.10.10-test8

- [x] Inspect actual test7 JPEG: thumbnail present with valid offsets, but 160x106 / 4:2:0, with no interoperability IFD. Original Sony thumbnail is 160x120 / 4:2:2.
- [x] Generate fixed 160x120 black-padded thumbnails with baseline 4:2:2 encoding and standard Huffman tables; restore R98 / 0100 interoperability tags. Main-image processing remains unchanged.
- [x] Add tests for thumbnail dimensions, sampling, padding, decoded colours/MCU placement and interoperability tags.
- [x] Inspect test8 output on card: actual thumbnail decodes as 160x120 / 4:2:2, R98/0100 present, dimensions correct; main and thumbnail decode with Pillow. User still reports camera playback failure.
- [x] All four diagnostic files display after copy/recovery, including unchanged test8 output. Only DSC04878 (original) magnifies. User accepts original-only magnification. Exact cause of earlier playback failures is not isolated.

## Completed: v1.10.10-test7

- [x] Address test6 ENOENT during output setup: use 8.3 OWNER.TXT / DSCxxxxx.SRC records under FILMSIM/INDEX instead of DCIM dotfiles. Use an 8.3 .MET temporary filename for metadata rewriting.
- [x] Include index paths and development stack traces in failure logs.
- [x] Run metadata regression tests against a real a6500 JPEG from the failed session, in addition to the synthetic endian/thumbnail tests.
- [x] Development succeeds with test7; copies register after recovery and are visible in Imaging Edge, but camera playback reports Unable to display. Exact storage-layer rejection of dotfiles remains inferred from the empty output folders and ENOENT log.

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
