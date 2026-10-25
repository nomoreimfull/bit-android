# Reverse-engineering record

Input: `.codexchat-attachments/8a336949bf124636952088ae93fef4ff/1-bit-1.exe`

SHA-256: `05e892dac55891e19f4741e4034a94e39d22b810bff7dae42c5e1107c48b9b45`.

## Verified identity and platform

The version resource identifies **Bit**, file/product version **1, 0, 0, 2**, original filename `bit.exe`, company **SK Soft**, copyright **2005 Anders Hovmöller, SK Soft**, and historical URL `http://soft.killingar.net`. PE32, Intel i386, Windows GUI subsystem, preferred image base `0x400000`, MSVC 6 linker, timestamp June 11, 2005. Four sections: `.text`, `.rdata`, `.data`, `.rsrc`.

Imports and call sites confirm actual OpenGL geometry: `glBegin`, vertex/normal calls, display lists, matrix transforms, fixed-function lighting, WGL context management. `UpdateLayeredWindow`, layered/tool window extended styles at `0x40121d`, and `SetWindowPos(HWND_TOPMOST)` at `0x40124f` confirm transparency and configurable always-on-top behavior. Settings are stored in the Windows registry. The Android port uses local preferences.

## Method and isolation

Static inspection came first: PE headers/imports/resources and complete x86 disassembly are retained in `analysis/`. Static table/routine tracing identified scene creation (`0x4024e0`), triangle normals (`0x402060`), star construction (`0x402110`, `0x4023d0`), rendering (`0x4032d0`), timer (`0x4019ee`), click selection (`0x401bfe`) and PRNG (`0x403afa`). Addresses are original virtual addresses.

To recover procedurally generated vertices without approximating them, `tools/recover.py` maps the image into Unicorn's x86 CPU emulator and runs **only** the display-list constructor, intercepting every import. The constructor can call only explicitly whitelisted OpenGL functions, which record arguments without rendering or accessing a Windows environment; unexpected external calls fail. No full EXE startup, Windows installation, host Win32 APIs, filesystem code or networking code is executed. The timer probe similarly executes only the window procedure's timer path with invalidation intercepted, plus the PRNG.

`analysis/meshes.json` records doubles; `mesh1.bin`–`mesh4.bin` store float32 positions/normals for OpenGL ES. Convex GL_POLYGON faces are triangulated as fans. GL_TRIANGLES remain groups of three vertices. Display-list matrix scales are baked into positions and inverse-transposed normals, including the red star’s inner scale. Triangle order and original face normals are preserved.

## Geometry

| Display list / Android asset | Recovered purpose | Triangles | Detail |
|---|---|---:|---|
| 1 / `mesh1.bin` | First neutral shell | 20 | Icosahedron, 12 vertices from table `0x40c060`, faces at `0x40c180`. |
| 2 / `mesh2.bin` | Second neutral shell | 36 | Dodecahedron; 12 pentagonal faces, 20 unique positions; golden-ratio calculations and 0.85 scale. |
| 3 / `mesh3.bin` | Yes | 8 | Octahedron; six axis-aligned vertices at ±1. |
| 4 / `mesh4.bin` | No | 120 | Original faceted/spiked construction derived from the icosahedron; includes an inner scaled component. |

Neutral is two independently breathing, overlapping solids, not a substituted single generic polyhedron. The yes/no meshes grow while neutral shells shrink; the original continuously blends the shared surface color during the response. No independent translation/bobbing was found: the floating appearance comes from rotation and changing scale.

## Colors, lighting and camera

The original neutral RGB is approximately `(0.372549, 0.623529, 0.917647)`, yes `(0.988, 0.800, 0.250)`, no `(0.788, 0.350, 0.075)`. The no color is the original warm red/orange, not an invented saturated red. All have alpha 1. Colors at a transition are `neutral × (1−blend) + response × blend`.

Scene initialization at `0x4024ff` sets a frustum `[-1,1] × [-1,1]`, near 1.5, far 20; model view translation `(0,0,-3)`. Rendering applies X, Y, Z rotation in that order and a global uniform scale 0.9. Fixed-function OpenGL lighting, light 0, color material, depth test and face culling are enabled. Light ambient is `(0.1,0.1,0.1,1)`, specular `(0.7,0.7,0.7,1)`; remaining values use OpenGL defaults. The GLES1 renderer uses these settings and the recovered normals, retaining the original lack of normalization under scaling. It clears to transparent instead of Windows’ magenta color-key background. Driver precision, tessellation and edge rasterization can differ from 2005 Windows OpenGL.

## Motion and response timing

The default rate scalar at `0x40c040` is **4.0**. Requested SetTimer interval is integer conversion of `100 / (4 × 1.7)` = **14ms**. On each timer tick:

- Breathing phase advances 1.25 degrees (nominal 89.2857°/second).
- X rotation advances 0.125°, Y 0.25°, Z 0.375° (nominal 8.9286, 17.8571, 26.7857°/second).
- The executable uses **0.017444444444444446** radians per degree, which the port preserves.
- With `b = sin(responsePhase × RAD)`, neutral shell scales are `abs(sin(breath × RAD)) × 0.5 + 0.9 − yesBlend × 0.5 − noBlend` and `abs(sin((breath+90) × RAD)) × 0.5 + 1.0 − yesBlend × 0.5 − noBlend`. Response meshes scale by `1.7 × b`.

Both response timer branches were verified by executing the original instructions: phase advances **5.5° for 17 ticks** (through 93.5°), then **2° per tick** until the 180° threshold, where the response flag and phase reset. This takes **61 ticks**, nominally **0.854 seconds**. `analysis/timer-probe.json` contains both full phase traces and rotation results. Windows timer scheduling and actual original wall-clock timing were not measured; the nominal requested clock is preserved.

The Android renderer samples that logical 14ms clock at **20fps** to limit power use for a small overlay. Timing and phases follow the original clock, with up to one render interval of visual quantization. It stops scheduling frames and releases the EGL context on screen-off/lock. Logical time freezes while hidden; a response already underway can finish visually after unlock, while its audio has been stopped.

## Selection and input

The click path calls MSVC `rand()` at `0x403afa` and chooses by `rand() % 2`. Its recovered LCG is `seed = seed × 214013 + 2531011` (32-bit wrap), result `(seed >> 16) & 0x7fff`. The app seeds it from epoch seconds, matching the EXE’s `srand(time(NULL))`. Odd chooses flag `0x40ee18`, resource **106**, the yellow octahedron; even chooses flag `0x40ee19`, resource **105**, the warm red/orange star. This is the verified code-path pairing; yes/no labels follow those matching Bit forms. Repeated taps are ignored until the response ends, as in the original.

Android uses touch slop for drag-versus-tap, cancels response on multitouch/cancel, and persists normalized usable-area position after dragging. This deliberately adapts Windows mouse behavior for touch. Rendering angles restart when the service is restarted, as Windows process restarts do; saved position/size/volume survive.

## Audio

| Windows resource | Response | Original WAVE size | MP3 payload | Decoded PCM duration |
|---|---|---:|---:|---:|
| WAVE 105 | No / red-orange | 1,720 bytes | 1,560 bytes | 6,912 samples / 11,025Hz = 0.62694s |
| WAVE 106 | Yes / yellow | 3,280 bytes | 3,120 bytes | 13,824 samples / 11,025Hz = 1.25388s |

Both WAVE format tags are **0x0055, MPEG Layer III**, mono, 11,025Hz, bits-per-sample field zero. Frame inspection confirms MPEG 2.5 Layer III with alternating 16/24kbps frames, averaging approximately 20kbps. The WAVE `fact` chunk sample counts exactly match decoded PCM. These are not PCM WAVE files despite their container.

The `data` chunks were extracted byte-for-byte into `yes.mp3` and `no.mp3`. For reliable Android playback the streams were decoded with libmpg123 1.33.7 to **16-bit signed little-endian mono PCM WAVE at the original 11,025Hz**, with no resampling, gain alteration, speech synthesis or lossy re-encoding. Runtime plays `yes.wav` and `no.wav`. Originals and converted copies remain in the asset/provenance record.

The full yes clip outlasts the nominal visual response, just as the original asynchronous PlaySound call allows; the port does not tie return-to-neutral to audio completion. Another accepted response stops/replaces the previous clip. Audio focus loss, stopping Bit and screen-off stop playback.

Both original RIFF files also contain metadata: `ICOP = Walt Disney Pictures`, `INAM = Tron`, `ISRC = Go to ---> SciFlicks.com`. These credits are preserved in the About section and documentation; source resource WAVs retain their metadata. They were discovered in the supplied EXE, not downloaded from those parties.

## Differences and limits

Recovered: all actual geometry, original colors/normals, lighting/camera settings, breathing, rotation order/rates, nominal response timing, PRNG and clip/shape pairing. No mesh was replaced with an approximation.

Adapted: 20fps drawing, float32 GLES coordinates instead of desktop doubles, transparent EGL surface instead of Windows color-key composition, Android audio focus and per-app gain, touch slop and screen-relative positioning, sensible 56–280dp size range, settings/notification UI, lock/screen-off behavior. Old Android 8–10 uses a legacy status-bar-height fallback for bounds; modern Android uses current window metrics and system/cutout insets.

Not measured: original Windows appearance on a live desktop, original timer jitter, acoustic output through the original Windows codec, and device-specific Android rendering/audio/overlay behavior. No pixel-identical or perpetual-process guarantee is claimed. See `TESTING.md`.

## Reproduce extraction (optional)

Ordinary APK builds use the included assets and need none of this tooling. To rerun the analysis on a supported host:

```sh
python3 -m pip install --target analysis/python pefile unicorn
python3 tools/recover.py
python3 tools/probe_timer.py
python3 tools/decode_audio.py /path/to/libmpg123.so
```

Termux needs its native Unicorn library. In this session the Python Unicorn 2.1.4 bindings were paired with the Termux native `libunicorn.so` via `LIBUNICORN_PATH`; native libraries were unpacked into temporary storage rather than installing or changing system packages. Static disassembly used `llvm-objdump -p` and `llvm-objdump -d --x86-asm-syntax=intel`. Regenerating assets requires rebuilding the APK and rerunning `tools/check.py`.
