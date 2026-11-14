# Installing WearCam

## Phone app

1. Download `phone-debug.apk` from the [latest release](https://github.com/The-kadtan2027/wearcam/releases/latest).
2. Allow installs from the browser/file manager, then open the APK.
3. Open WearCam Phone → grant the camera permission → **Enable accessibility** → **Start streaming** → open the camera app.
4. Optional: **Calibrate camera** (one go: five taps) so the watch's modes match this camera app.

## Watch app

### Option A — Google Play (recommended once published)

On the watch: Play Store → search **WearCam** → Install. The watch app is delivered
directly; no PC and no developer mode.

### Option B — bundled APK + wireless debugging (works today)

The phone app ships the watch APK:

1. Phone app → scroll to **WATCH APP** → **Export & share APK**. The APK is saved to
   `Downloads/WearCam-watch.apk` and a share sheet opens (Bluetooth, email, Drive…).
2. On the watch: **Settings → About → tap Build number 7×**, then **Developer options →
   Wireless debugging → on**. Note the IP:port shown.
3. On a PC with adb:

   ```
   adb connect <watch-ip>:<port>
   adb install WearCam-watch.apk
   ```

4. Open the WearCam app on the watch; it connects to the phone over Bluetooth and Wi-Fi.

Any Wear OS sideload helper (for example a phone-side ADB tool) can replace the PC step.

### Option C — F-Droid (planned)

The repo is MIT-licensed and builds from source; the phone app is F-Droid compatible.
The watch APK is distributed alongside (F-Droid has no Wear OS client on the watch).

## Public release plan

| Channel | Phone app | Watch app | Notes |
|---|---|---|---|
| GitHub Releases | ✅ now | ✅ bundled + exported | zero gatekeeping, best for technical users |
| F-Droid | planned | via the phone export | FOSS build from source, no store account |
| Google Play | planned | ✅ Wear OS listing | one-tap watch install; the accessibility service needs a policy declaration and review |

The main Play risk is the accessibility service: Google restricts it to accessibility
tooling and core functions. WearCam uses it to press the stock camera's shutter, which
can be justified as remote camera control but may be rejected. Mitigations:

- a privacy policy stating that nothing leaves the two devices;
- a Data Safety form (camera, local network only);
- if Play rejects the accessibility approach, ship a "Play-friendly" in-app camera
  (CameraX) variant that does not need accessibility, while keeping the full
  stock-camera experience on GitHub/F-Droid.
