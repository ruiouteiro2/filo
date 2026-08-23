# Filo

A private Android app for two people in a long distance relationship. Never published to the
Play Store; it is sideloaded onto two phones.

English and Italian, chosen per person rather than by device locale.

## What it does

- **Us** — both faces, each in a presence ring: lit means awake, dim means asleep, dashed
  means they have not set their hours. Each ring carries their mood emoji, with their mood
  text and note underneath.
- **Countdown** to the next visit, and how long you have been together.
- **Distance** computed on device, with a dark map of where they are and a tap through to your
  own maps app.
- **Live location** — optional always-on sharing, with a permanent notification and an
  explicit four step permission flow.
- **Weather** where they are, and **battery**, because a flat battery is what explains silence.
- **Listening to** — what they are playing on Spotify, tappable to open the same track in
  yours.
- **Photo of the day**, one each, downscaled on device and stored in a private bucket.
- **Bucket list**, shared and realtime.
- **Thinking of you** — one tap sends a heart; a database trigger notifies the other phone in
  their own language.
- Four home screen widgets, including a clock ticked natively by Android in their timezone.

## Stack

Kotlin, Jetpack Compose (Material 3, dynamic colour off), Glance for widgets, Supabase
(Postgres with RLS on every table, anonymous auth, Realtime, Storage, Edge Functions),
Firebase Cloud Messaging, WorkManager, Open-Meteo, Preferences DataStore. minSdk 26.

## Setup

Everything you need to point this at your own Supabase and Firebase projects, build a signed
APK and get it onto a phone is in **[SETUP.md](SETUP.md)**.

Nothing secret is committed: `local.properties`, `keystore.properties`, the keystore itself,
`google-services.json` and any service account JSON are all gitignored.

## Licences

Bundled typefaces are Fraunces and Karla, both SIL Open Font License 1.1. The licences ship
with the app in `app/src/main/assets/font_licenses.txt`.

Map tiles are © OpenStreetMap contributors, served by CARTO.
