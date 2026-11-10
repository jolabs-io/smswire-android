# SMSWire for Android

The [SMSWire](https://smswire.app) phone app turns an Android phone into an SMS gateway: your web app sends texts through the SIM in your own phone, and gets delivery reports and replies on its webhook.

This repository only publishes the app's signed release builds. The app is developed in the SMSWire monorepo.

## Install

1. On the Android phone (Android 8.0 or newer), open the [latest release](https://github.com/jolabs-io/smswire-android/releases/latest) and download `smswire-<version>.apk`, or use the direct link: `https://github.com/jolabs-io/smswire-android/releases/latest/download/smswire.apk`.
2. Allow your browser to install unknown apps when Android asks, then open the file.
3. In the [SMSWire dashboard](https://smswire.app), go to **Phones → Pair a phone** and scan the code with the app.

## Check the download

Every release lists the SHA-256 of its APK and ships a `SHA256SUMS` file:

```sh
sha256sum -c SHA256SUMS
```

All releases are signed with the same key, so a new version installs over the old one and keeps its pairing. To compare the signing certificate:

```sh
apksigner verify --print-certs smswire-<version>.apk
```

## Help

Docs: [smswire.app/docs](https://smswire.app/docs) · Support: support@smswire.app · [Privacy Policy](https://smswire.app/privacy)
