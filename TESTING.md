# Verification record

## Completed in this workspace

- Verified supplied PE identity, version/copyright, OpenGL/WGL imports, resource IDs, transparent composition and topmost call sites.
- CPU-emulated original geometry constructor with every external call intercepted; captured all display-list positions/normals and converted to GLES buffers. Checked triangle counts, finite values and exact float32 conversion of the captured doubles.
- CPU-emulated **both actual original response timer branches**, confirming their phase traces, 61-tick completion and all four phase/rotation accumulators. Retained trace fixtures in `analysis/timer-probe.json`.
- CPU-emulated original PRNG, then compared Kotlin output/yes-no decisions to its reference sequence.
- Verified original MP3 payloads byte-for-byte against WAVE resource `data` chunks. Parsed every MPEG frame: MPEG2.5 Layer III, mono, 11,025Hz, alternating 16/24kbps frames. Decoded to PCM and verified format/duration against WAVE `fact` sample counts.
- Executed Kotlin host tests against the original timer fixture: response returns to neutral, phase progression/peak, deterministic PRNG selection, periodic neutral breathing, and **2,525** slider/screen/density cases, with six edge/position restoration checks per case. Includes landscape, portrait, screens smaller than the usual minimum and an extreme 1×1 input.
- Built the APK from Kotlin and Android resources using Java 21, platform 36, native AAPT2 and D8; verified APK signing, ZIP integrity, alignment and that packaged assets match the final source assets. `build/build.log` and `build/check.log` retain output.
- ADB was made callable using an isolated temporary copy of its required libc++ library. `adb devices` returned an **empty device list**. Nothing was installed, no permission state was changed and no device UI action was performed.

Run `python3 tools/check.py` after `sh build.sh` to repeat host checks. The direct build uses a local debug key. Standard Gradle/Android Studio build files are provided but that separate route was not run here.

## Not run on an Android device/emulator

The APK has **not** been installed or run on an Android device or emulator. The host itself is Android/Termux, but compiling there does not establish app runtime correctness. Transparent SurfaceView composition, hardware lighting/rasterization, permission UX, touch delivery, actual media output, lifecycle callbacks and OEM background policies need the following device pass. Source inspection is not represented as a passed device test.

| Required scenario | Implemented handling / expected result | Device status |
|---|---|---|
| Permission granted | Return from overlay settings starts the service after the enable action. | Not run |
| Permission denied | Remains off; switch/status explain permission; cancellation does not start service. | Not run |
| Permission revoked | AppOps watcher, foreground checks and settings resume detect revocation and stop. Android may kill the process directly. | Not run |
| Overlay start/stop | User action starts special-use foreground service; switch/notification stop removes window, notification, listeners, audio and EGL resources. | Not run |
| Tap versus drag | Touch slop separates gestures; cancelled or multitouch gestures do not answer; dragging saves position. | Not run |
| Sound/geometry pairing | Odd PRNG → original 106/yellow octahedron; even → original 105/red-orange spiked form. Code/assets verified; actual playback/display still needs device check. | Not run |
| Return to neutral | Original 61-tick model verified on host; sample rendering at 20fps. | Not run visually |
| Live size and volume | Preferences listener resizes about current center/clamps; playback gain updates existing player. | Not run |
| Zero volume | No player/focus request for muted preview or response; current playback gain immediately becomes zero. | Not run acoustically |
| Persistence | Size/volume and normalized position in private preferences; restart/stop retains them. | Not run across process restart |
| Rotation/display resize | Display/configuration listeners remeasure usable area, restore relative placement and clamp. | Not run |
| Underlying app interaction | Small window, NOT_FOCUSABLE/NOT_TOUCH_MODAL; no full-screen touch window. | Not run |
| Screen off/lock | Frame callbacks removed, EGL paused, overlay hidden, audio stopped; unlock resumes. | Not run |
| Notification controls | Content/open-settings action opens settings; stop action stops service; settings includes notification settings. | Not run |
| Notification denial | Service can run; settings explains Active apps/Task Manager fallback. | Not run |

## Device checklist

1. Install `build/Bit-debug.apk`, open launcher. Confirm a scrollable settings screen, no full-screen Bit. Deny overlay permission, cancel the explanatory dialog and revisit: no floating object or foreground service should remain.
2. Enable and grant overlay permission. Return to settings; verify the switch is on, the object floats without a rectangular background, and the persistent notification offers both actions. Close settings and use another ordinary app.
3. Tap repeatedly until both responses occur: yellow octahedron with original yes sound, red-orange spikes with original no sound. Each appearance returns to neutral after about 0.85 seconds; yes audio can continue until about 1.25 seconds. Fast taps while responding should not restart it.
4. Drag from near the middle to each edge and beyond. No answer should sound while dragging. Release: Bit stays within usable bounds. Tap buttons and scroll in the underlying app outside the small overlay square.
5. Move the size slider through 0, 50, 100 while visible. Confirm immediate resize, stable center where bounds allow, nonzero minimum, no clipping at maximum. Repeat at an edge, then in landscape and split-screen/display resize where supported.
6. Preview both sounds at 0, 25, 100. Verify 0 is silent, current clip gain changes immediately, and Android's system media volume value does not change. Change system media volume separately; Bit should follow it. Test another app playing music, a call/focus loss, and a routed output if available.
7. Move Bit, change both sliders, stop/re-enable and restart the app/process. Verify settings and relative position restore. Reset position while stopped and running; both should produce the default visible location on the next/current overlay. Opening settings after a killed process does not auto-enable it.
8. Switch the screen off during neutral and during a response. Verify no rendering continues while off/locked (e.g. a system trace), audio stops and unlock resumes animation. Check repeated lock/unlock cycles.
9. Revoke Display over other apps from system settings while Bit runs, then reopen Bit: no stranded service/window and no crash. Regrant and enable again.
10. Test notification Open settings and Stop Bit. Deny notification permission on API 33+; verify the explanatory status and system Active apps listing. Re-enable notifications using the provided shortcut.
11. Check a protected system/permission surface. Bit must obey Android's suppression rather than cover it. Force-stop the app: Bit disappears and does not automatically resurrect or start at boot.

Suggested devices: API 26/29 for legacy bounds, API 30+ for modern cutouts/gestural navigation, API 34 for service-type enforcement, API 35/36 for current focus/start restrictions. Foldables, display scaling and OEM background policies need additional coverage before a production release.

## Visual update 1.1

Added user-supplied BIT splash artwork without pixel modification, Android 12+ starting-screen resources, and black/graphite/cyan/red native settings controls. Verified the packaged splash resource, theme resources, compilation and signing. PNG crunching is disabled so the packaged splash is byte-for-byte identical to the supplied image. The artwork uses aspect-fit scaling, not cropping. Custom splash lasts 850ms on a fresh launcher activity, then shows settings; it is skipped on activity recreation, permission return, or notification open. Runtime visual inspection on an Android device is still not performed.

## Edge-extension update 1.2

The original fully-on-screen placement checks remain. Additional host checks cover fixed left/right overhang examples, all 0–50% allowances, normalized off-screen position restoration, orientation/size changes, migration from old saved positions, immediate recovery when extension returns to zero, tiny screens, and invalid inputs. All 9,180 new extension/persistence/recovery cases passed, along with the existing 2,525 size/bounds/position cases and original animation/audio asset checks. These are placement-model tests, not actual Android window-manager tests.

Device checklist additions (not run; ADB lists no connected devices):

1. With Bit running, set Edge extension to 25%, then drag to all four edges and corners. Check that overhang is allowed and the remaining visible area can still be dragged/tapped.
2. Set 50%; confirm a side can clip half the window, but Bit cannot disappear entirely. Check response clipping and retained touch interaction with the underlying app outside the small visible window.
3. Set 0% while Bit is partly off-screen; it must immediately return fully inside the usable bounds. Increase again; Bit stays where it is until dragged.
4. Restart the service/app and rotate the screen with Bit parked at each edge. Check retained relative center, live size changes and correct clamping.
5. Reset position with Bit running, then while stopped. It must return to a visible default without changing the extension preference.
6. Install as an update over 1.1; confirm the previous saved position migrates without shifting at the current size. Confirm no regressions in permission, stop notification, screen-off behavior or gesture navigation.
