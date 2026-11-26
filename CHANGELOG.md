# Changelog

What changed in each release of the SMSWire Android app. Releasing a version publishes its
section as the release notes; the app, the dashboard and the download page show it.

<!--
Each release gets a section before its android-vX.Y.Z tag is pushed (the release fails without one):

    ## 1.2.0 — 2026-12-01
    critical: true        (optional: every phone older than this is asked to update right away)

    ### New
    - One line per change, written for the person holding the phone.

    ### Fixed
    - …

Use "### New", "### Changed" and "### Fixed". A beta gets its own section ("## 1.2.0-beta.1").
-->

## 1.8.0 — 2026-11-26

### Changed
- On a phone with two SIMs set to take turns, each person now keeps getting texts from the SIM that
  last texted them (for 30 days), so they see one number instead of two. People you haven't texted
  yet still go to the SIMs in turn.

## 1.7.0 — 2026-11-21

### Changed
- The pairing code on the web now shows a QR you can scan with the phone's camera before the app is
  installed: it opens the download page with the code already there, instead of the phone saying
  nothing can handle it. "Scan pairing code" in the app reads that QR as well as the older one.

## 1.6.0 — 2026-11-19

### New
- Use every SIM at once, under Settings on a phone with two SIMs. Each SIM sends on its own, so a
  batch goes out about twice as fast. It is off until you turn it on, because each SIM then carries
  twice the traffic it did while they took turns, which a carrier may notice.
- A SIM can be given a name on its phone's page on the web ("safaricom-main"), and its own number
  typed in. A send can then ask for that SIM by name instead of naming a phone and a slot.

### Changed
- A project with several phones now shares its queue between them instead of using the first one
  that is online, so they send a batch together. Each phone takes only what its own send rate has
  room for, and a phone that drops out mid-batch no longer holds its share: the others carry on.
- A number keeps being texted from the phone that texted it last, so replies and STOP stay in one
  thread. Set on the web, per project.

## 1.6.0-beta.1 — 2026-11-19

### New
- Use every SIM at once, under Settings on a phone with two SIMs. Each SIM sends on its own, so a
  batch goes out about twice as fast. It is off until you turn it on, because each SIM then carries
  twice the traffic it did while they took turns, which a carrier may notice.
- A SIM can be given a name on its phone's page on the web ("safaricom-main"), and its own number
  typed in. A send can then ask for that SIM by name instead of naming a phone and a slot.

### Changed
- A project with several phones now shares its queue between them instead of using the first one
  that is online, so they send a batch together. Each phone takes only what its own send rate has
  room for, and a phone that drops out mid-batch no longer holds its share: the others carry on.
- A number keeps being texted from the phone that texted it last, so replies and STOP stay in one
  thread. Set on the web, per project.

## 1.5.0 — 2026-11-17

### New
- Change what a project receives right here: open it under Projects, then "Change what it receives" (replies, every SMS, and the senders it always takes). The web and the project's other phones get the change at once, and changes made on the web show up here.

## 1.4.0 — 2026-11-17

### New
- People can join your contact groups by texting a keyword (like A2 or OK) to this phone, even if it never texted them. Only a text that is exactly one of your keywords goes to the web.
- Each project now chooses on the web which received SMS it takes: replies to it, senders you list (a number, a short code like 127 or a name like CBE), or every SMS. This phone sends up only what one of its projects takes. Settings and each project's page show what that is.

### Changed
- Incoming SMS: "Replies" is now "Rules", which follows your projects. "All" now gives every SMS only to projects set to take it; "Off" still keeps everything on the phone.
- A project pinned to one SIM receives only the SMS that arrive on that SIM.

## 1.3.2 — 2026-11-15

### Fixed
- The gateway's notification comes straight back if it's swiped away, so you can always see the gateway is running.
- "Exit" in the notification now asks first, so a stray tap in the notification shade can't take the gateway offline.

## 1.3.1 — 2026-11-14

### Fixed
- On some Samsung phones (seen on a Galaxy A55) the app closed after the SMS permission prompt. It now shows how to allow SMS instead.

## 1.3.0 — 2026-11-14

### New
- The app updates itself: when a new version is out it says so (a notification and a banner on the Gateway), shows what changed, and installs it with a tap. The gateway pauses for a moment and comes back on its own.
- Settings → Updates: "Ask me" (the default) or "Automatic", which installs once nothing is waiting to send. It can also be changed from the dashboard.
- Settings → App → Check for updates.
- The send-only build updates to the send-only build.

## 1.3.0-beta.2

### Changed
- A test build: it exists so 1.3.0-beta.1 has something to update to. Nothing else changed.

## 1.3.0-beta.1

### New
- The app updates itself: when a new version is out it says so (a notification and a banner on the Gateway), shows what changed, and installs it with a tap. The gateway pauses for a moment and comes back on its own.
- Settings → Updates: "Ask me" (the default) or "Automatic", which installs once nothing is waiting to send. It can also be changed from the dashboard.
- Settings → App → Check for updates.
- The send-only build updates to the send-only build.

## 1.2.0 — 2026-11-14

### New
- Switch workspace (Settings → Workspace): scan or type another workspace's pairing code and the phone moves there. It joins the new workspace first, so a wrong or expired code changes nothing. Opening a pairing QR while paired goes there too.
- Three tabs: Gateway, Messages and Settings. Projects and batches open from them.
- Phone problems (battery restrictions, missing permissions, SMS limits) show as a card on the Gateway with a one-tap fix, and a dot on the Settings tab.

### Changed
- Unpairing and switching clear what the phone kept for the old workspace: unsent messages and texts waiting to upload never reach the new one. You choose whether to keep the message history.
- The send-only build says so on the Pair screen and the home header.

### Fixed
- Unpairing without a connection no longer forgets the pairing silently: try again, or forget anyway knowing the old workspace still lists the phone until it's removed on the web.

## 1.1.0 — 2026-11-13

### New
- Send-only build (smswire-1.1.0-send-only.apk): in some countries Play Protect blocks apps that can read incoming SMS ("App blocked to protect your device"). This build can't read SMS, so it installs; it sends and gets delivery reports, but replies and STOP opt-outs don't reach SMSWire.
- The app's source code is public (GPL-3.0) at github.com/jolabs-io/smswire-android, so you can check exactly what runs on your phone.

### Changed
- Only replies to messages this phone sent go to your inbox by default (texts from numbers it sent to in the last 30 days), so bank codes and personal texts stay on the phone. Settings → Incoming SMS can forward all of them, or none.
- Right after pairing, the app asks to run unrestricted in the background, so Android doesn't hold back sends while the screen is off.

### Fixed
- When Android blocks SMS for an app installed from the browser ("App was denied access"), the app now shows how to allow it.

## 1.0.0 — 2026-11-10

### New
- Pair from the dashboard by scanning the QR code.
- Sends SMS from the phone's SIM, with delivery reports and replies on your webhook.
- Crash reports to help us fix problems (no message text or phone numbers).
