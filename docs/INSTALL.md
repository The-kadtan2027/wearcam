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

### Option B2 — install from the phone itself (no PC, no store)

The phone can pair with the watch's wireless debugging and push the APK directly:

1. On the watch: **Developer options → Wireless debugging → Pair device with pairing code**.
   Note the pairing IP:port and the 6-digit code; the main Wireless debugging screen shows
   the connect IP:port.
2. Phone app → **WATCH APP → Install on watch via Wi-Fi**.
3. Enter the pairing IP:port, the code, and the connect IP:port → **Pair & install**.

The pairing is a one-time step per watch; afterwards the phone installs/updates the app
over Wi-Fi without any developer tool.

### Updating the watch app over Bluetooth (no PC)

Once the watch app is installed, updates travel over the existing RFCOMM link:

1. Phone app → **WATCH APP → Send update to watch**.
2. The APK streams over Bluetooth; the watch opens the system installer.
3. Confirm **Install** (one-time: allow WearCam to install unknown apps).

The transfer pauses the live preview for ~1 s while the APK is written.

### Option C — F-Droid (planned)

The repo is MIT-licensed and builds from source; the phone app is F-Droid compatible.
The watch APK is distributed alongside (F-Droid has no Wear OS client on the watch).

## Free distribution (no store fees)

- **Obtainium** (recommended for auto-updates): install Obtainium, add
  `https://github.com/The-kadtan2027/wearcam`, and it tracks GitHub releases and installs
  updates automatically. No account, no fee.
- **GitHub Releases**: the signed `phone-release.apk` / `watch-release.apk`.
- **F-Droid / IzzyOnDroid**: the recipe is in `fdroid/com.gajuk.wearcam.yml`; F-Droid's
  buildserver needs the `android-37.0` platform (canary) — until then, an F-Droid flavor
  without the Kadb installer can build on SDK 35.
- **The watch app**: the phone bundles it (export / install via Wi-Fi / Bluetooth updates),
  so no watch store is needed.

## Signing

Release APKs are signed with the project key (`CN=WearCam, O=WearCam, C=IN`). The keystore
lives outside the repo; CI reads it from the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS` and `KEY_PASSWORD` secrets. Every future release must use the same key, or
updates will be rejected.

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
