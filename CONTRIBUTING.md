# Contributing

Thanks for helping. This repository is a read-only mirror of the app's folders in the SMSWire monorepo, refreshed at every release. Pull requests are still the way in: we apply an accepted one to the monorepo, credit you as its author, and it appears here with the next release.

## Before you start

- For anything bigger than a small fix, open an issue first so we can agree on the approach.
- Keep the engine (`gateway/engine`) free of Android code; it's plain Kotlin and runs on any JVM.
- A change to `shared/protocol` must keep the golden fixtures in `shared/protocol/fixtures` passing. The server checks the same files, so a frame change needs our side too: say so in the issue.

## Checks

```sh
./gradlew :gateway:engine:test :shared:protocol:test :shared:sms-core:test
./gradlew :android:app:assembleDebug
```

## Sign-off

Sign off every commit (`git commit -s`). That adds a `Signed-off-by` line and certifies the [Developer Certificate of Origin](https://developercertificate.org): you wrote the change, or have the right to submit it, under this project's license (GPL-3.0).
