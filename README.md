# AirFile ✈️

Send any file from your phone to your Android TV. Open AirFile on the TV, scan the QR code with your phone's camera, pick files, done. No app is needed on the phone.

## How it works

1. AirFile runs a small web server on the TV (port 8080, or the next free port up to 8099).
2. The QR code encodes `http://<tv-ip>:<port>/?t=<random-token>`.
3. The page that opens on your phone uploads files straight to the TV over your Wi-Fi.

The token is generated every time the app starts, and the server rejects requests without it, so other devices on your network can't send files to your TV.

## Where files are saved

In order of preference:

1. A folder you choose with **Choose save folder** (pick one on the SD card if you like).
2. The SD card's app folder (`Android/data/com.airfile.tv/files/AirFile`), if an SD card is present.
3. `Downloads/AirFile` on the TV's internal storage (Android 10+).

The text on the TV screen always shows which location is active. Android 11+ doesn't let apps write to arbitrary SD card paths, which is why a folder pick or the app's own folder is used.

## Install

Download the latest `AirFile-vX.Y.Z.apk` from [Releases](../../releases) and sideload it:

```sh
adb connect <tv-ip>
adb install AirFile-vX.Y.Z.apk
```

Or put the APK somewhere your TV can download it and install it with a file manager / Downloader app.

## Build from source

Requires JDK 17, Gradle 8.9+ and the Android SDK (API 35). Open the folder in Android Studio, or run:

```sh
gradle test assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

### Testing without a phone

```sh
curl -X POST --data-binary @photo.jpg \
  "http://<tv-ip>:8080/upload?t=<token>&name=photo.jpg"
```

## Releasing

Push a tag like `v0.1.0`. The **Release** workflow builds an APK and attaches it to a GitHub Release. To sign with your own key, add these repository secrets: `KEYSTORE_BASE64` (base64 of your `.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the APK is signed with the debug key, which is fine for sideloading.

## Notes

- Phone and TV must be on the same network. Guest Wi-Fi with client isolation will block it.
- Transfers use plain HTTP on your local network (token-protected, not encrypted).

## License

MIT
