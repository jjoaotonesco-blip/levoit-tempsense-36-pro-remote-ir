# Release Checks

## Current Public APK

- APK: `Levoit-TempSense-36-Pro-Remote-IR-v1.0.apk`
- Package: `pt.local.levoitir`
- Version name: `1.0`
- Version code: `1`
- Size: `1488511` bytes
- SHA-256: `7C5E0236A3F9FA74D533C10A1957CD63E550794735E77D75AFB8428D7CEBDF1F`

## Verification Commands

```powershell
Get-FileHash .\dist\Levoit-TempSense-36-Pro-Remote-IR-v1.0-signed.apk -Algorithm SHA256
Get-FileHash .\Levoit-TempSense-36-Pro-Remote-IR-v1.0.apk -Algorithm SHA256
adb shell dumpsys package pt.local.levoitir | Select-String "versionName|versionCode|lastUpdateTime"
```

## Notes

The app installed on the test Redmi device should report `versionName=1.0` and `versionCode=1`. If the GitHub release asset is older than the repository APK, replace the release asset with the current signed APK.

