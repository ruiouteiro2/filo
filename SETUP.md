# Filo — setting it up for two real phones

The app is built and working. What is left is creating the two cloud accounts it talks to,
because those need your email and card details, not mine.

Everything below is a one-off. After it, building a new APK is one command.

---

## What already works

Verified on a device against a real database:

- Anonymous sign in, create a link, join with a code, third person rejected
- Row Level Security: neither person can read or write anything outside their own couple,
  and neither can edit the other's row
- Realtime: a change on one phone appears on the other without a refresh
- Both faces with their day rings, live clocks in each timezone, awake/asleep
- Distance (computed on device), weather, battery, mood, notes, countdowns, bucket list
- Avatar and photo-of-the-day upload: downscaled to 1080px / JPEG 80 on the phone, stored
  in a **private** bucket, read back through signed URLs
- All four widgets on the home screen, including the native-ticking clock
- The heart widget sends a ping without opening the app
- Italian throughout (152 strings, no gaps), switchable per person in Settings
- Signed release APK, R8 enabled, verified working as a **fresh install**

## What needs your accounts

- **Supabase cloud project** — the two phones cannot reach the test database on this PC.
- **Firebase project** — needed only for the push notification when the heart is tapped.
  Without it, everything else works and the ping is still recorded; it just does not buzz
  the other phone.

---

## 1. Supabase (required)

1. Go to supabase.com, create a project. Any region in Europe is fine; `eu-west-3` (Paris)
   is closest to both of you.
2. **Settings → API**: copy the *Project URL* and the *anon public* key.
3. Put them in `local.properties` in the project root (this file is gitignored and never
   ends up in the APK's source, only its values are baked in):

   ```properties
   sdk.dir=C:/Users/Rui/AppData/Local/Android/Sdk
   supabase.url=https://YOUR-PROJECT.supabase.co
   supabase.anonKey=eyJhbGciOi...
   ```

4. **Authentication → Providers → Anonymous sign-ins: turn it ON.** Nothing works without
   this and the error it gives is unhelpful.
5. Push the schema. From the project folder:

   ```bash
   npx supabase link --project-ref YOUR-PROJECT-REF
   npx supabase db push
   ```

   That applies both migrations in `supabase/migrations/`: the schema with RLS, and the
   `service_role` grants the Edge Function needs.
6. **Database → Replication**: confirm `members`, `countdowns` and `bucket_items` are in the
   `supabase_realtime` publication. The migration adds them, so this is just a check.

That is enough for the whole app except the push notification.

## 2. Firebase (optional — only the heart notification)

1. Create a Firebase project, add an Android app with package name `com.filo.app`.
2. Download `google-services.json` into `app/`. The build detects it automatically; without
   it the app builds fine and simply skips push.
3. **Project settings → Service accounts → Generate new private key.** That downloads a JSON
   file.
4. Deploy the function and give it that key:

   ```bash
   npx supabase functions deploy ping-notify --no-verify-jwt
   npx supabase secrets set FIREBASE_SERVICE_ACCOUNT="$(cat path/to/service-account.json)"
   ```

5. Tell the database trigger where the function lives. In the SQL editor:

   ```sql
   insert into private.config (key, value) values
     ('functions_url',     'https://YOUR-PROJECT.supabase.co/functions/v1'),
     ('service_role_key',  'YOUR-SERVICE-ROLE-KEY')
   on conflict (key) do update set value = excluded.value;
   ```

   `private.config` has RLS on and no policies, so neither phone can ever read it.

The function localises the notification body itself from the recipient's `locale` column, so
she gets Italian and you get English from the same ping.

---

## 3. Building the APK

```bash
cd C:\Users\Rui\Desktop\Filo
gradlew.bat assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (about 3.8 MB).

### The keystore — read this once

`keystore/filo-release.jks` and `keystore.properties` were generated for you and are
gitignored. **Back both up somewhere you will not lose them** (a password manager, a private
repo, an encrypted drive).

If you ever sign an update with a different key, Android refuses to install it over the old
one. She would have to uninstall first, which wipes her pairing and her local state. The
invite code in Settings is the way back from that, but it is better not to need it.

The passwords are in `keystore.properties`. The certificate fingerprint of the key that
signed the current APK is:

```
SHA-256: 34:C6:CD:29:62:3D:68:CC:A6:AF:F8:1B:EE:FF:58:10:53:15:52:15:F6:60:B5:5B:5C:32:9C:B5:FD:82:E7:61
```

---

## 4. Getting it onto her phone

Send her the APK on WhatsApp, then send her this:

> Apri il file che ti ho mandato. Android ti dirà che non può installarlo: tocca
> **Impostazioni**, attiva **Consenti da questa origine**, poi torna indietro e tocca
> **Installa**.

Then, in the app: she taps **Entra con un codice** and types the six characters from your
screen. That is the whole pairing.

### First run

- It asks for approximate location, then notifications, each with an explanation first.
  Saying no to either is fine; the rest of the app carries on and the distance card offers a
  way into settings.
- Widgets: long-press the home screen → Widgets → Filo. There are four.
- If widgets go stale on her phone (some manufacturer skins kill background work), there is a
  button in Settings that asks Android to stop doing that. It is not nagged about anywhere
  else.

---

## Notes for later

- **Language** follows the per-person setting in Settings, not the phone's language. Either
  of you can change your own.
- **Location** is read only while the app is open, never in the background, and only ever
  to approximate accuracy.
- **The photo bucket is private.** Photos are served through signed URLs that expire; there
  is no public link to anything either of you uploads.
- **Both people's names, moods and notes** are the only free text in the app. There is no
  chat and no history by design.
