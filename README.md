# SMSWire for Android

The [SMSWire](https://smswire.app) phone app turns an Android phone into an SMS gateway: your web app sends texts through the SIM in your own phone, and gets delivery reports and replies on its webhook.

<a href="https://www.youtube.com/watch?v=aA5PCVwdjEE"><img src="https://img.youtube.com/vi/aA5PCVwdjEE/maxresdefault.jpg" alt="Watch the SMSWire intro video" width="560"></a>

[▶ Watch the SMSWire intro](https://www.youtube.com/watch?v=aA5PCVwdjEE)

This is the app's full source, under the [GPL-3.0](LICENSE). The server it pairs with is the hosted SMSWire service; it isn't in this repository. Every release here is built from the source at its tag.

## What the app does with your phone

| Permission | Why |
|---|---|
| Send SMS | Sends the messages your projects ask for, from the SIM you choose. |
| Receive SMS | Forwards replies to your dashboard and webhooks (see below). Not in the send-only build. |
| Phone state | Reads which SIM cards are in the phone and their carriers, so you can pick one. |
| Internet, network state | Keeps one outbound WebSocket to the server; the phone needs no open port. |
| Notifications, foreground service, start at boot, ignore battery optimisations | Keeps the gateway running while the screen is off and after a restart. |

**Incoming SMS.** Each project on the dashboard chooses which received texts it takes: replies from people it texted in the last 30 days (the default), senders it lists (a number, a short code like 127, or a name like CBE), and every SMS on its SIM. STOP and START from people it texted, and texts that are exactly a group's join keyword, always get through. The phone sends up a text only when one of its projects takes it, and within its own setting under **Gateway settings → Incoming SMS**:

- **Rules** (the default): what the projects take, except "every SMS". Codes from banks, short codes and personal texts stay on the phone unless a project lists that sender.
- **All**: projects set to every SMS get every SMS on their SIM. Use it on a phone kept only for the gateway.
- **Off**: nothing leaves the phone.

Only someone holding the phone can change this setting; the dashboard shows it but can't widen it. What each project takes can be changed on the dashboard or in the app (Projects → a project), and each picks up the other's changes. The filter runs on the phone, before anything is stored for upload: see [`InboundSync.kt`](gateway/engine/src/main/kotlin/app/relay/gateway/engine/InboundSync.kt) and [`ReceiveFilter.kt`](gateway/engine/src/main/kotlin/app/relay/gateway/engine/ReceiveFilter.kt).

Crash reports go to Sentry only in builds made with a Sentry DSN (the official releases); they hold stack traces, not message text.

## Install

1. On the Android phone (Android 8.0 or newer), open the [latest release](https://github.com/jolabs-io/smswire-android/releases/latest) and download `smswire-<version>.apk`, or use the direct link: `https://smswire.app/app/download/android`.
2. Allow your browser to install unknown apps when Android asks, then open the file.
3. In the [SMSWire dashboard](https://smswire.app), go to **Phones → Pair a phone** and scan the code with the app.

**Blocked by Play Protect?** In some countries Play Protect blocks apps that can read incoming SMS when they're installed from a browser ("App blocked to protect your device", with no Install anyway). Each release also has a **send-only build**, `smswire-<version>-send-only.apk` (direct link: `https://smswire.app/app/download/android/send-only`). It's the same app without `RECEIVE_SMS` or the default-SMS-app components: it sends and gets delivery reports, but replies and STOP opt-outs don't reach SMSWire, and it can't lift Android's 30-a-minute limit by becoming the default SMS app. Both builds share the app id and signing key, so either installs over the other and keeps the pairing.

## Check the download

Every release lists the SHA-256 of its APKs and ships a `SHA256SUMS` file. With only the APK you downloaded next to it:

```sh
sha256sum -c --ignore-missing SHA256SUMS
```

All releases are signed with the same key, so a new version installs over the old one and keeps its pairing. To compare the signing certificate:

```sh
apksigner verify --print-certs smswire-<version>.apk
```

## Build it yourself

Needs JDK 17 and the Android SDK (set `ANDROID_HOME`, or `sdk.dir` in `local.properties`).

```sh
./gradlew :android:app:assembleTwoWayDebug -Prelay.serverUrl=https://api.smswire.app   # or assembleSendOnlyDebug
./gradlew :gateway:engine:test :shared:protocol:test :shared:sms-core:test
```

A build you make yourself is signed with your own debug key, so it installs as an update only over another build of yours. Uninstall the official app first, then pair again.

| Path | What it is |
|---|---|
| `android/app` | The app: navigation, the foreground service and wiring. |
| `android/core/*` | Storage (Room, DataStore), networking (OkHttp WebSocket), telephony and the design system. |
| `android/feature/*` | Screens: gateway, messages, bulk, projects, settings, pairing. |
| `gateway/engine` | The platform-free core, plain Kotlin and fully unit-tested: dispatch, throttling, retries, status sync, inbound sync. |
| `shared/protocol` | The frames the phone and the server exchange. `fixtures/` holds golden JSON that the server's tests check too. |
| `shared/sms-core` | SMS segment counting (GSM-7/UCS-2) and number parsing. |

## Contributing

Issues and pull requests are welcome: see [CONTRIBUTING.md](CONTRIBUTING.md). Report security problems privately, as [SECURITY.md](SECURITY.md) describes. "SMSWire" and its logo are trademarks: forks must use another name ([TRADEMARKS.md](TRADEMARKS.md)).

## Help

Docs: [smswire.app/docs](https://smswire.app/docs) · Support: support@smswire.app · [Privacy Policy](https://smswire.app/privacy)
