# Levoit TempSense 36 Pro Remote IR

Android IR remote for the Levoit TempSense 36 Pro tower fan.

This app is made for Android phones with a built-in infrared emitter, such as some Xiaomi and Redmi devices. It may also work with external IR transmitters if the accessory is compatible with Android's `ConsumerIrManager` infrared API.

![App screenshot](docs/screenshot.png)

## Features

- Power on/off
- Fan speed up/down
- Mode button
- Timer button
- Swing/oscillation
- Sleep mode
- Sound/mute button

## Device Support

The app sends NEC infrared commands through Android's native `ConsumerIrManager`.

It requires one of these:

- an Android phone with built-in IR hardware
- an external IR transmitter that exposes itself through Android's consumer IR API

It will not work on devices without compatible IR hardware.

## Tested Setup

- Device: Redmi phone with integrated IR
- Protocol: NEC
- Working bit order: MSB
- Carrier frequency: 38 kHz

## Install

Download the signed APK from the release page or from the `dist` folder if you built it locally:

```text
Levoit-TempSense-36-Pro-Remote-IR-v1.0-signed.apk
```

Then install it on an Android device that supports IR.

## Build From Source

Requirements:

- Android SDK
- Gradle
- JDK 21 or compatible JDK

Build debug APK:

```powershell
gradle assembleDebug
```

Build release APK:

```powershell
gradle assembleRelease
```

## Signing

The release APK in `dist` was signed with a local self-signed Android keystore.

Do not publish:

- `signing/`
- `*.jks`
- `keystore.properties`

Those files are intentionally ignored by Git.

## Disclaimer

This is an unofficial remote control app and is not affiliated with Levoit.
