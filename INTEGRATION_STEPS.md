# DhwaniMitra Android ↔ Local Supabase integration

## What this update integrates

Working against the existing backend RPCs:

- Supabase Auth signup/login
- database-driven user/business lookup
- profile/shop setup
- profile/shop local cache for offline launch
- language/theme cache
- logout
- account-delete Edge Function client
- existing offline inventory prototype remains functional

## What is NOT yet cloud-synced

The current Android inventory table is still the old flat schema:

`Product(name, quantity, unit)`

The cloud backend uses:

`Product -> Variant/SKU -> Packaging -> Inventory -> Transaction`

Therefore this update intentionally does not upload the old flat inventory yet.
That migration is the next step. Faking a sync between incompatible schemas would corrupt data.

---

## 1. Apply

Extract this ZIP over the root of the Android project.

---

## 2. Confirm local Supabase is running

From the backend folder:

```powershell
npx supabase start
npx supabase status
```

Your API should be on:

`http://127.0.0.1:55321`

---

## 3. Copy the LOCAL anon/publishable key

From `npx supabase status`, copy the local anon/publishable key.

Edit:

`app/src/main/res/values/supabase.xml`

Replace:

`YOUR_LOCAL_ANON_OR_PUBLISHABLE_KEY`

Never use the service_role/secret key in Android.

---

## 4. Wi-Fi-debugging phone: reverse the API port

Make sure the phone is connected in `adb devices`.

Run:

```powershell
adb devices
adb reverse tcp:55321 tcp:55321
adb reverse --list
```

Expected mapping:

`tcp:55321 tcp:55321`

Now the phone can use:

`http://127.0.0.1:55321`

and the traffic is forwarded to the PC.

If the phone disconnects/restarts, run `adb reverse` again.

---

## 5. Apply backend migration

The local backend must contain the DhwaniMitra backend migration with:

- get_my_context()
- complete_business_setup()
- get_dashboard_summary()
- apply_inventory_transaction()

If needed:

```powershell
npx supabase db reset --local
```

Only run reset after `supabase start` is healthy.

---

## 6. Build

Android Studio:

- Sync Project with Gradle Files
- Build -> Rebuild Project
- Run on the physical phone

---

## 7. Test signup

1. Open app
2. Create account
3. Because local `enable_confirmations = false`, signup should return a session
4. Business Setup opens automatically
5. Enter merchant/shop details
6. Save
7. `complete_business_setup()` writes profile + shop to PostgreSQL
8. First welcome opens
9. Dashboard opens

Verify in local Studio:

`http://localhost:55323`

Check:

- Authentication -> Users
- Table Editor -> profiles
- Table Editor -> shops

---

## 8. Test returning login

1. Logout
2. Login with the same email/password
3. Android calls `get_my_context()`
4. Supabase returns profile/shop
5. Android caches those locally
6. setup_complete=true -> Dashboard

No "Are you a new user?" question exists.

---

## 9. Test offline launch

1. Login once successfully
2. Complete business setup
3. Close app
4. Stop local Supabase or disconnect network
5. Reopen app
6. Launcher reads cached authenticated merchant + local shop
7. Dashboard opens and local inventory still works

Cloud operations wait until connectivity returns.

---

## 10. Account deletion

For local testing, serve/deploy the function:

```powershell
npx supabase functions serve delete-account
```

The app calls:

`/functions/v1/delete-account`

Note:
The current local flat inventory is not user-scoped yet. Before treating account deletion as production-complete, migrate local inventory to `shop_id`-scoped UUID tables so only the deleted merchant's local records are removed.

---

## 11. Next required migration

The next code update must replace the local flat stock model with:

- local_products (UUID + shop_id)
- local_product_variants
- local_packaging_units
- local_inventory
- local_stock_transactions (UUID)
- sync_status

Then WorkManager can send each transaction UUID to:

`apply_inventory_transaction()`

and mark it SYNCED only after the backend confirms it.

That is the correct point to integrate Whisper stock writes with real cloud synchronization.
