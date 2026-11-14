# v1.10.10-test4: held focus assist

In the AF/MF selector position, hold the button to temporarily enter manual focus (unless already in MF/DMF) and enable native peaking when exposed. Focus peaking in Fn selects the level used during the hold; Off disables peaking during the hold as well. Outside the hold, supported peaking is off. Original camera peaking settings are restored when the app releases the camera.

Focus-position changes or ring-turn key events trigger the first supported native magnification step only while the assist is held. The API's magnification values are step identifiers, so this build does not claim a verified 5.9x ratio. Lens reporting and actual magnification must be checked on the camera. The first focus-position callback establishes a baseline rather than triggering zoom.

Button release, selector change, and closing the camera end the assist. Shutter half-press and capture cancel magnification while preserving held manual focus and peaking. Half-press must not request autofocus during the hold, even when Fn Focus mode is Auto. After half-press, movement cannot restart magnification until the AF/MF button is released and pressed again. Half-press does not release an independently held AEL. Stale focus callbacks cannot restart an old assist session.

The native-peaking and combined-selector limitations from test1 still apply: no software peaking fallback is included, unadvertised peaking parameters are not written, and unrecognized combined-button selector codes are logged rather than guessed. A fully mechanical lens cannot send electronic focus movement; ring-triggered assist requires events from the lens/camera.

Desktop regression tests verify hold-only peaking and zoom, repeated events, cancellation, stale callbacks, MF preservation, AEL preservation, and restoration. Hardware behavior is not covered by these tests. Metering/focus-area controls, the alpha fix, and flat FILMSIM/DEV output remain in place.
