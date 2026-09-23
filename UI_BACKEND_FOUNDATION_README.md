# DhwaniMitra UI + Backend Foundation v1

This pack fixes the immediate backend configuration problem and refreshes
the app's visual foundation.

## Root cause of the current connection error

The repository currently contains:

http://127.0.0.1:55321

in:

app/src/main/res/values/supabase.xml

On a physical Android phone, 127.0.0.1 means the PHONE itself, not your PC.
It works only when ADB reverse is active.

The previous Start-DhwaniMitra.ps1 also rewrote supabase.xml to the local
127.0.0.1 URL each time it started the local stack.

## Production / normal-device setup

1. Supabase Dashboard -> open your project.
2. Open the Connect dialog.
3. Copy:
   - Project URL
   - Publishable key (sb_publishable_...)
4. From project root run:

   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
   .\Configure-DhwaniMitra-Backend.ps1

5. Paste the hosted URL and publishable key.
6. Build:

   .\gradlew clean
   .\gradlew :app:assembleDebug

7. Install:

   .\Start-DhwaniMitra.ps1 -BackendMode Hosted -Install

or build + install together:

   .\Start-DhwaniMitra.ps1 -BackendMode Hosted -Build -Install

## Local development mode

If you intentionally want the phone to use local Supabase on your PC:

   .\Start-DhwaniMitra.ps1 -BackendMode Local -Build -Install

The script starts local Supabase and creates the required ADB reverse tunnel.

## Security

Use ONLY the publishable key in Android.

Never put:
- sb_secret_...
- service_role
- database password
- direct Postgres connection string

inside the APK.

The publishable key is a client key. Protect database rows with RLS.

## UI changes

This pack replaces:
- Login
- Signup
- Business Setup
- Account & Settings
- Light/dark palette
- App icon foreground/background
- Android 12+ splash appearance
- Pre-Android-12 splash window background

The main dashboard layout is intentionally not replaced in this pack, because
it is large and tightly coupled to MainActivity IDs. The new palette already
updates its colors; the dashboard can be redesigned next as a separate,
ID-preserving pass.

## Verify backend before app testing

After Configure-DhwaniMitra-Backend.ps1 succeeds, the script has already
verified:

<Project URL>/auth/v1/health

Then the app should no longer attempt 127.0.0.1 unless you explicitly select
Local backend mode.
