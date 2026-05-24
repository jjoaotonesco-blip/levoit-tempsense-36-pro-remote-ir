# Release Checks

## Current Public APK

- APK: `Levoit-TempSense-36-Pro-Remote-IR-v1.0.apk`
- Package: `pt.local.levoitir`
- Version name: `1.0`
- Version code: `1`
- Size: `1488511` bytes
- SHA-256: `B87E1B4A4A678674BED73DBF8E60292CFB40A6472A1D943D6151A4DFD4CC97B4`

## Verification Commands

```powershell
Get-FileHash .\dist\Levoit-TempSense-36-Pro-Remote-IR-v1.0-signed.apk -Algorithm SHA256
Get-FileHash .\Levoit-TempSense-36-Pro-Remote-IR-v1.0.apk -Algorithm SHA256
adb shell dumpsys package pt.local.levoitir | Select-String "versionName|versionCode|lastUpdateTime"
```

## Notes

The app installed on the test Redmi device should report `versionName=1.0` and `versionCode=1`. If the GitHub release asset is older than the repository APK, replace the release asset with the current signed APK.

This build adds a saved screen flip toggle for people using an external IR adapter.

