# Urban Reign — Android port build

This repository builds an ARM64 Android APK from the GPL-3.0 source of Urban Reign Recomp v1.3.0. The project is still experimental; compiling an APK does not yet prove the game is playable on a physical phone.

## Avoid uploading the 4 GB ISO to Actions

The code generator needs only the verified boot executable `SLUS_212.09` extracted from the user's own Urban Reign (USA) PS2 disc. The complete ISO is **not** downloaded to GitHub Actions, committed to the repository, or embedded in the APK.

1. Run Actions → **Prepare Urban Reign generated game code** and paste your existing HTTPS ISO share link into `iso_url`. The build fetches the ISO's PVD, root directory and `SLUS_212.09` executable extent using HTTP byte ranges, and refuses hosts that ignore range requests. It does not intentionally download or store the full ISO. A Google Drive share link in the usual `drive.google.com/file/d/…/view` form is converted to a range-download URL.
2. If the host does not support byte ranges, use the local helper [`urban-reign-android/tools/extract-boot-elf.html`](tools/extract-boot-elf.html) to extract `SLUS_212.09` on your device, then provide a small HTTPS executable link in `boot_elf_url` (or configure `URBAN_BOOT_ELF_URL`). The build checks the exact SHA-256 before generating code and uploads only a generated-code archive.
3. After successful preparation, **Urban Reign Android APK** starts automatically using that run's generated-code artifact. You don't need to transfer the ELF a second time. You can also start **Urban Reign Android APK** manually and enter the successful preparation run's numeric ID in `generated_code_run_id`. If the artifact has expired, repeat the preparation workflow.

The Android APK workflow also accepts `boot_elf_url` directly when `generated_code_run_id` is blank. Neither workflow accepts the complete ISO as a build input.

## Game disc on Android

The APK does **not** include the game disc. On first launch, select your own uncompressed Urban Reign USA ISO with Android's document picker. The Activity keeps an open file descriptor and passes its `/proc/self/fd/` path to native code, so it does not make a second DVD-sized copy in app storage. Keep the ISO at the same location and do not remove its storage permission after selecting it. Some cloud providers expose non-seekable streams; if direct opening fails, download the ISO to device storage first and select that local file.

## Current implementation

- `patches/apply_android_port.py` applies Android host, ARM64 intrinsic and touch-input compatibility patches to a temporary upstream checkout.
- `patches/generate_from_elf.py` validates the boot ELF and generates the game-specific C++ without requiring the ISO.
- `native/android_bridge.cpp` connects the Android Activity to native ISO selection and virtual touch-gamepad input.
- `android-app/` contains the NativeActivity manifest, Java activity and adjustable touch overlay.
- `.github/workflows/urban-reign-prepare-game-code.yml` generates and archives reusable game code from the small boot ELF.
- `.github/workflows/urban-reign-android-apk.yml` builds the ARM64 APK from that artifact, or generates code from the boot ELF in the same run.
- `.github/workflows/urban-reign-android-probe.yml` verifies native ARM64 and Java compilation without claiming a playable game build.

## Remaining validation

The APK needs a complete native build with the verified generated code and then testing on a real ARM64 Android device. The Android build does not use the Windows PCSX2 GS DLL; MPEG video decoding is disabled in this initial configuration, so cutscene-video support remains unvalidated.
