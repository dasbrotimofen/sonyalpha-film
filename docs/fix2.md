# v1.10.9-fix2

- Captures snapshot film labels and development settings before the shutter is triggered. A background scan matches changed DCIM files against the pre-shot snapshot, groups RAW+JPEG pairs, and prefers JPEG. It refuses ambiguous matches rather than developing the wrong image.
- The app waits for two seconds of unchanged file metadata before allowing another capture. This deliberately limits rapid shooting; it is a file-stability heuristic, not a firmware write-completion callback. Association times out after roughly 30 seconds following the shutter callback. Leaving the app cancels pending association.
- New copies are stored under `FILMSIM/DEV/<DCIM folder>/<photo stem>/`, e.g. `FILMSIM/DEV/100MSDCF/DSC04845/DV04845.JPG`. Copy allocation excludes existing and queued names, reports exhausted slots, and never deletes an existing JPEG to replace it. Playback still finds legacy flat-folder copies; their original folder identity cannot be recovered from the old filenames alone.
- Shot log entries include the DCIM folder; old entries remain readable.
- Developed-copy playback uses the background preview worker and display-bounded decoding in both dimensions. This uses the app's bitmap overlay rather than Sony's full-colour viewer for developed copies. Original-photo playback is unchanged. Rotation and pixel-copy failures release the decoded bitmap.
- JPEG compression failure and source-file stability timeout are now reported rather than silently accepted.

Validation: desktop regression cases cover capture file identity, RAW+JPEG pairing, ambiguous matches, folder-specific copies, queued/occupied/exhausted slots, non-overwriting publication, preview bounds, and the earlier alpha fix. `python scripts/build.py all` runs these checks and verifies APK signatures and alignment. Camera capture callbacks, UI responsiveness, overlay rendering, and SD-card behavior still require testing on the camera.
