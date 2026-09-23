# DhwaniMitra RBAC Foundation v1

This package adds the first production RBAC layer without changing the working owner login flow.

## Added backend objects

- `permissions`
- `roles`
- `role_permissions`
- `shop_memberships`
- `audit_events`
- `is_shop_member(...)`
- `current_shop_role(...)`
- `has_shop_permission(...)`
- `get_my_access(...)`

It also upgrades current RLS and the inventory/dashboard RPC authorization from owner-only checks to permission checks.

## Android files

- `AccessControl.kt`
- `RbacClient.kt`
- `RbacCache.kt`

These are intentionally self-contained. They do not require replacing your currently working Login/Main activity yet.

The local permission cache is UI-only. PostgreSQL remains the security boundary.

## Install paths

Copy the package contents into `C:\Dev\GigPoint` so the final paths are:

```
C:\Dev\GigPoint\Backend\supabase\migrations\20260922100000_rbac_foundation.sql
C:\Dev\GigPoint\Backend\Test-RBAC.ps1
C:\Dev\GigPoint\app\src\main\java\com\example\gigpoint\AccessControl.kt
C:\Dev\GigPoint\app\src\main\java\com\example\gigpoint\RbacClient.kt
C:\Dev\GigPoint\app\src\main\java\com\example\gigpoint\RbacCache.kt
```

Keep `20260920_production_inventory_core.sql.disabled` disabled.

## Safe deployment

From `C:\Dev\GigPoint\Backend`:

```powershell
npx supabase migration list
npx supabase db push --dry-run
```

The dry run should show only:

```
20260922100000_rbac_foundation.sql
```

Then:

```powershell
npx supabase db push
npx supabase migration list
```

## Verify owner RBAC

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\Test-RBAC.ps1
```

For the canonical owner, expected values include:

- role = `OWNER`
- is_owner = `true`
- `business.delete` = ALLOW
- all seeded permissions are returned

## Build Android

From `C:\Dev\GigPoint`:

```powershell
.\gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs --no-configuration-cache
.\gradlew :app:assembleDebug --no-daemon --no-watch-fs --no-configuration-cache
```

## Deliberately not included yet

Staff invitation is not implemented in the APK because a Supabase secret/service-role key must never be embedded in a mobile application.

The next RBAC stage should add a server-side `invite-staff` Edge Function, followed by Employees & Roles UI, role-aware dashboard visibility, and AI action-policy enforcement.
