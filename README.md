# DhwaniMitra One-Command Launcher

Copy these files into:

`C:\Users\anant\OneDrive\Desktop\GigPoint\`

## Normal startup

Double-click:

`Start-DhwaniMitra.cmd`

or:

```powershell
.\Start-DhwaniMitra.ps1
```

It will:

- check Docker Desktop
- start/check local Supabase
- read the local Project URL + Publishable key
- update `app/src/main/res/values/supabase.xml`
- start local Edge Functions when present
- detect your Android device
- create `adb reverse tcp:55321 tcp:55321`
- launch DhwaniMitra if installed

## Build + install + launch

```powershell
.\Start-DhwaniMitra.ps1 -Build -Install
```

## Build + install + open Supabase Studio + launch

```powershell
.\Start-DhwaniMitra.ps1 -Build -Install -OpenStudio
```

## Services only

```powershell
.\Start-DhwaniMitra.ps1 -NoLaunch
```

## Notes

Room/SQLite and Whisper run inside the Android app and do not need separate servers.
