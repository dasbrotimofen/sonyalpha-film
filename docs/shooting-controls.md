# v1.10.10-test1 shooting controls

The Fn menu adds Metering, Focus area, AF mode, Focus peaking and Peaking colour. Options come from the camera's supported parameters, not a fixed list of assumed a6500 features. Unsupported controls display Unavailable. Focus point movement is not included.

Changes apply immediately when the camera is idle. Choices survive reopening the camera for playback or recording during the same activity session. The previous camera values are restored when the camera is released; choices are not persisted across app restarts.

The dedicated Sony AF/MF and AEL key events activate temporary AF/MF switching and exposure lock. Holding does not repeatedly toggle; release, a selector change, or closing the camera restores the prior state. The exposure bar shows AEL or AF/MF HOLD. The combined-button event is handled only when the selector reports a recognized named AF/MF or AEL code. Unknown selector values are logged instead of assigning an unverified meaning to 0 or 1. Camera testing may therefore be needed to finish combined-button mapping.

Native peaking has no setter in the available Sony stubs. The adapter uses peaking-level/peaking-mode and peaking-color only if the camera explicitly advertises their `-values` entries and a corresponding current value. No undocumented parameter is written unconditionally. If these are absent, peaking remains unavailable; this build does not implement software edge highlighting. Peaking-related supported parameters and selector events are written to FILMSIM/LOG.TXT for diagnosis.

Desktop fake-backend regression tests exercise supported-mode cycling, unsupported controls, failed setters, repeat suppression, focus/exposure holds, previous-lock preservation, restoration on close, and choices on reopen. Existing alpha and file-handling regression tests also run. Actual metering, autofocus behaviour, selector codes, and native peaking must be verified on the camera.

Developed copies continue to save directly in FILMSIM/DEV with the fix3 behaviour.
