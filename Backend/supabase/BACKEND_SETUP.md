# DhwaniMitra Backend Setup

## Selected backend

**Supabase**

Services used:

- Supabase Auth
- PostgreSQL
- Row Level Security
- PostgREST/Data API
- PostgreSQL RPC functions
- Edge Functions

There is no Spring Boot or Django service in the MVP.

## Selected databases

### Cloud
Supabase PostgreSQL

### Phone
Room/SQLite (the current prototype still uses SQLiteOpenHelper; Room migration comes next)

The phone remains the operational source of truth while offline. The cloud is the authenticated backup/synchronization source and enables future multi-device use.

---

## 1. Create a Supabase project

Create a normal Supabase project.

For fast prototype testing, you can either:

- disable email confirmation temporarily, or
- keep email confirmation enabled and verify each test account before login.

Do not put the service-role/secret key in Android.

The Android client only needs:

- project URL
- publishable/anon client key

---

## 2. Create the database

Open:

Supabase Dashboard -> SQL Editor -> New Query

Run:

`supabase/migrations/20260919173000_dhwani_mitra_backend.sql`

This creates:

- profiles
- shops
- categories
- brands
- products
- product_variants
- packaging_units
- suppliers
- inventory
- stock_batches
- stock_transactions
- reminders

It also creates:

- RLS policies
- merchant/shop ownership rules
- get_my_context()
- complete_business_setup()
- apply_inventory_transaction()
- get_dashboard_summary()

---

## 3. How login routing works

After authentication the Android app should call:

`get_my_context()`

Expected result:

```json
{
  "user_id": "...",
  "profile": {...},
  "shop": {...},
  "setup_complete": true
}
```

Routing:

```text
Login
  ↓
authenticated user_id
  ↓
get_my_context()
  ↓
setup_complete?
  ├── false -> Business Setup
  └── true  -> Dashboard
```

There is no "Are you a new user?" question.

---

## 4. Business setup

The app can call:

`complete_business_setup(...)`

This atomically creates/updates:

- merchant profile
- shop

Fields:

- owner name
- shop name
- business type
- optional GSTIN
- optional phone
- preferred language
- theme
- optional city/area

---

## 5. Product model

Do not store "Coca-Cola = 20".

Use:

```text
Product: Coca-Cola

Variants:
- 250 ml Can
- 500 ml Bottle
- 1 L Bottle
- 2 L Bottle
```

Each variant has its own:

- SKU
- barcode
- size
- package type
- purchase price
- selling price
- reorder level
- current inventory

---

## 6. Packaging conversion

Example:

```text
Coca-Cola 500 ml Bottle

base_unit = piece

Packaging:
carton = 24 pieces
```

If the merchant receives:

`2 cartons Coke 500 ml`

Android converts:

`2 × 24 = 48 base pieces`

and sends `quantity_base = 48` to the backend.

---

## 7. Inventory writes

The Android app must NOT directly update the cloud `inventory` table.

Call:

`apply_inventory_transaction(...)`

Supported operations:

- PURCHASE
- SALE
- CUSTOMER_RETURN
- SUPPLIER_RETURN
- DAMAGE
- EXPIRED
- ADJUSTMENT_IN
- ADJUSTMENT_OUT
- OPENING_STOCK
- TRANSFER_IN
- TRANSFER_OUT

The RPC:

1. verifies the user owns the shop
2. verifies the SKU belongs to the shop
3. locks the inventory row
4. rejects negative stock
5. inserts immutable transaction history
6. updates current inventory atomically
7. returns the before/after quantity

---

## 8. Offline sync

The phone creates transaction UUIDs BEFORE going online.

Example local record:

```text
id = 8f5c...
type = SALE
variant = Coke 500 ml
quantity_base = 3
sync_status = PENDING
```

When internet returns:

```text
WorkManager
  ↓
apply_inventory_transaction(id = 8f5c...)
  ↓
success
  ↓
local sync_status = SYNCED
```

If the same UUID is retried, the backend returns:

`status = already_applied`

and does NOT subtract stock twice.

This is critical for unreliable connectivity.

---

## 9. Dashboard

Call:

`get_dashboard_summary(shop_id)`

It returns:

- total active SKUs
- low-stock count
- out-of-stock count
- estimated inventory value
- today's sales

---

## 10. Account deletion

Backend function:

`supabase/functions/delete-account/index.ts`

Deploy through the Supabase CLI:

```bash
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase functions deploy delete-account
```

The Android app calls the function with the merchant's authenticated user JWT.

The service-role key stays on the server.

Because the database relationships use `ON DELETE CASCADE`, deleting the Auth user removes the merchant profile, shop, and dependent business data.

If Storage is added later, owned Storage objects must be handled before account deletion.

---

## 11. Important current Android work still required

Backend complete does not mean Android sync is complete.

The app still needs:

- Room schema matching cloud UUIDs
- shop_id on local records
- Product/Variant entities
- packaging conversion entities
- WorkManager sync
- RPC calls
- profile/settings screen
- logout
- delete-account UI
- Whisper integration

---

## 12. MVP backend rule

**Catalog CRUD** can use ordinary authenticated table APIs.

**Inventory quantity changes** must use the transaction RPC.

Never do:

```text
UPDATE inventory SET quantity = ...
```

from the Android application.

Always create a stock transaction.
