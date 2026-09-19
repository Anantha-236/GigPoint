-- DhwaniMitra MVP Backend
-- Supabase PostgreSQL + Auth + RLS
-- 2026-09-19
--
-- Design goals:
-- 1. Authenticated merchant identity comes from auth.users.
-- 2. Business setup is discovered from the database after login.
-- 3. Products support variants/SKUs and packaging conversions.
-- 4. Inventory transactions are immutable and applied atomically through RPC.
-- 5. Client-generated UUID transaction IDs make offline sync idempotent.
-- 6. RLS isolates each merchant's shop data.

begin;

create extension if not exists pgcrypto;

-- =========================================================
-- Utility: updated_at
-- =========================================================

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

-- =========================================================
-- Merchant identity / onboarding
-- =========================================================

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    owner_name text,
    phone text,
    preferred_language text not null default 'en'
        check (preferred_language in ('en', 'te', 'hi')),
    theme text not null default 'system'
        check (theme in ('system', 'light', 'dark')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.shops (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null unique references auth.users(id) on delete cascade,
    shop_name text,
    gstin text,
    business_type text,
    city text,
    area text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- =========================================================
-- Catalog
-- =========================================================

create table if not exists public.categories (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete cascade,
    name text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (shop_id, name)
);

create table if not exists public.brands (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete cascade,
    name text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (shop_id, name)
);

create table if not exists public.products (
    id uuid primary key,
    shop_id uuid not null references public.shops(id) on delete cascade,
    category_id uuid references public.categories(id) on delete set null,
    brand_id uuid references public.brands(id) on delete set null,
    name text not null,
    description text,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index if not exists idx_products_shop
    on public.products(shop_id);

create index if not exists idx_products_name
    on public.products(shop_id, name);

-- A product variant is the sellable SKU:
-- Coca-Cola -> 500 ml Bottle, 1 L Bottle, 2 L Bottle, etc.
create table if not exists public.product_variants (
    id uuid primary key,
    shop_id uuid not null references public.shops(id) on delete cascade,
    product_id uuid not null references public.products(id) on delete cascade,

    sku text,
    variant_name text not null,

    size_value numeric,
    size_unit text,
    package_type text,

    barcode text,

    base_unit text not null default 'piece',

    purchase_price numeric(14,2)
        check (purchase_price is null or purchase_price >= 0),

    selling_price numeric(14,2)
        check (selling_price is null or selling_price >= 0),

    mrp numeric(14,2)
        check (mrp is null or mrp >= 0),

    reorder_level numeric(14,3) not null default 0
        check (reorder_level >= 0),

    active boolean not null default true,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index if not exists idx_variants_shop
    on public.product_variants(shop_id);

create index if not exists idx_variants_product
    on public.product_variants(product_id);

create unique index if not exists idx_variants_sku_unique
    on public.product_variants(shop_id, sku)
    where sku is not null;

create unique index if not exists idx_variants_barcode_unique
    on public.product_variants(barcode)
    where barcode is not null;

-- Conversion from merchant-facing packaging to a canonical/base stock unit.
-- Example: Coke 500 ml Bottle -> carton = 24 pieces.
create table if not exists public.packaging_units (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete cascade,
    variant_id uuid not null references public.product_variants(id) on delete cascade,
    name text not null,
    conversion_to_base numeric(14,4) not null
        check (conversion_to_base > 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (variant_id, name)
);

-- =========================================================
-- Suppliers
-- =========================================================

create table if not exists public.suppliers (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete cascade,
    name text not null,
    phone text,
    email text,
    notes text,
    average_lead_days integer
        check (average_lead_days is null or average_lead_days >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_suppliers_shop
    on public.suppliers(shop_id);

-- =========================================================
-- Inventory and batches
-- =========================================================

-- Current quantity is maintained in the variant's base unit.
-- Example: 2 cartons of 24 bottles = quantity_base +48 pieces.
create table if not exists public.inventory (
    variant_id uuid primary key references public.product_variants(id) on delete cascade,
    shop_id uuid not null references public.shops(id) on delete cascade,
    quantity_base numeric(16,4) not null default 0
        check (quantity_base >= 0),
    updated_at timestamptz not null default now()
);

create index if not exists idx_inventory_shop
    on public.inventory(shop_id);

create table if not exists public.stock_batches (
    id uuid primary key,
    shop_id uuid not null references public.shops(id) on delete cascade,
    variant_id uuid not null references public.product_variants(id) on delete cascade,
    supplier_id uuid references public.suppliers(id) on delete set null,

    batch_number text,
    purchase_date date,
    expiry_date date,

    quantity_received_base numeric(16,4) not null default 0
        check (quantity_received_base >= 0),

    quantity_remaining_base numeric(16,4) not null default 0
        check (quantity_remaining_base >= 0),

    purchase_price numeric(14,2)
        check (purchase_price is null or purchase_price >= 0),

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_batches_shop_variant
    on public.stock_batches(shop_id, variant_id);

create index if not exists idx_batches_expiry
    on public.stock_batches(shop_id, expiry_date)
    where expiry_date is not null;

-- =========================================================
-- Immutable stock audit history
-- =========================================================

create table if not exists public.stock_transactions (
    id uuid primary key, -- generated on the phone before sync
    shop_id uuid not null references public.shops(id) on delete cascade,
    variant_id uuid not null references public.product_variants(id) on delete restrict,
    batch_id uuid references public.stock_batches(id) on delete set null,

    transaction_type text not null
        check (
            transaction_type in (
                'PURCHASE',
                'SALE',
                'CUSTOMER_RETURN',
                'SUPPLIER_RETURN',
                'DAMAGE',
                'EXPIRED',
                'ADJUSTMENT_IN',
                'ADJUSTMENT_OUT',
                'OPENING_STOCK',
                'TRANSFER_IN',
                'TRANSFER_OUT'
            )
        ),

    -- Canonical quantity used to update inventory.
    quantity_base numeric(16,4) not null
        check (quantity_base > 0),

    -- What the merchant actually said/entered.
    entered_quantity numeric(16,4),
    entered_unit text,

    before_quantity_base numeric(16,4) not null,
    after_quantity_base numeric(16,4) not null,

    unit_price numeric(14,2)
        check (unit_price is null or unit_price >= 0),

    total_amount numeric(16,2)
        check (total_amount is null or total_amount >= 0),

    source text not null default 'MANUAL'
        check (source in ('MANUAL', 'VOICE', 'OCR', 'IMPORT')),

    transcript text,
    notes text,

    device_id text,

    occurred_at timestamptz not null default now(),
    created_at timestamptz not null default now(),

    created_by uuid not null references auth.users(id) on delete restrict
);

create index if not exists idx_transactions_shop_time
    on public.stock_transactions(shop_id, occurred_at desc);

create index if not exists idx_transactions_variant_time
    on public.stock_transactions(variant_id, occurred_at desc);

create index if not exists idx_transactions_type
    on public.stock_transactions(shop_id, transaction_type);

-- =========================================================
-- Reminders / alerts
-- =========================================================

create table if not exists public.reminders (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete cascade,
    variant_id uuid references public.product_variants(id) on delete cascade,

    reminder_type text not null
        check (
            reminder_type in (
                'LOW_STOCK',
                'OUT_OF_STOCK',
                'REORDER',
                'OLD_STOCK',
                'EXPIRY',
                'SYNC',
                'CUSTOM'
            )
        ),

    title text not null,
    message text,
    due_at timestamptz,
    completed boolean not null default false,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_reminders_shop
    on public.reminders(shop_id, completed, due_at);

-- =========================================================
-- updated_at triggers
-- =========================================================

drop trigger if exists trg_profiles_updated_at on public.profiles;
create trigger trg_profiles_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

drop trigger if exists trg_shops_updated_at on public.shops;
create trigger trg_shops_updated_at
before update on public.shops
for each row execute function public.set_updated_at();

drop trigger if exists trg_categories_updated_at on public.categories;
create trigger trg_categories_updated_at
before update on public.categories
for each row execute function public.set_updated_at();

drop trigger if exists trg_brands_updated_at on public.brands;
create trigger trg_brands_updated_at
before update on public.brands
for each row execute function public.set_updated_at();

drop trigger if exists trg_products_updated_at on public.products;
create trigger trg_products_updated_at
before update on public.products
for each row execute function public.set_updated_at();

drop trigger if exists trg_variants_updated_at on public.product_variants;
create trigger trg_variants_updated_at
before update on public.product_variants
for each row execute function public.set_updated_at();

drop trigger if exists trg_packaging_updated_at on public.packaging_units;
create trigger trg_packaging_updated_at
before update on public.packaging_units
for each row execute function public.set_updated_at();

drop trigger if exists trg_suppliers_updated_at on public.suppliers;
create trigger trg_suppliers_updated_at
before update on public.suppliers
for each row execute function public.set_updated_at();

drop trigger if exists trg_batches_updated_at on public.stock_batches;
create trigger trg_batches_updated_at
before update on public.stock_batches
for each row execute function public.set_updated_at();

drop trigger if exists trg_reminders_updated_at on public.reminders;
create trigger trg_reminders_updated_at
before update on public.reminders
for each row execute function public.set_updated_at();

-- =========================================================
-- Security helper
-- =========================================================

create or replace function public.is_shop_owner(target_shop_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.shops s
        where s.id = target_shop_id
          and s.owner_id = (select auth.uid())
    );
$$;

revoke all on function public.is_shop_owner(uuid) from public;
grant execute on function public.is_shop_owner(uuid) to authenticated;

-- =========================================================
-- Row Level Security
-- =========================================================

alter table public.profiles enable row level security;
alter table public.shops enable row level security;
alter table public.categories enable row level security;
alter table public.brands enable row level security;
alter table public.products enable row level security;
alter table public.product_variants enable row level security;
alter table public.packaging_units enable row level security;
alter table public.suppliers enable row level security;
alter table public.inventory enable row level security;
alter table public.stock_batches enable row level security;
alter table public.stock_transactions enable row level security;
alter table public.reminders enable row level security;

-- Remove broad client privileges first.
revoke all on table public.profiles from anon, authenticated;
revoke all on table public.shops from anon, authenticated;
revoke all on table public.categories from anon, authenticated;
revoke all on table public.brands from anon, authenticated;
revoke all on table public.products from anon, authenticated;
revoke all on table public.product_variants from anon, authenticated;
revoke all on table public.packaging_units from anon, authenticated;
revoke all on table public.suppliers from anon, authenticated;
revoke all on table public.inventory from anon, authenticated;
revoke all on table public.stock_batches from anon, authenticated;
revoke all on table public.stock_transactions from anon, authenticated;
revoke all on table public.reminders from anon, authenticated;

-- Editable merchant-owned tables.
grant select, insert, update, delete on table public.profiles to authenticated;
grant select, insert, update, delete on table public.shops to authenticated;
grant select, insert, update, delete on table public.categories to authenticated;
grant select, insert, update, delete on table public.brands to authenticated;
grant select, insert, update, delete on table public.products to authenticated;
grant select, insert, update, delete on table public.product_variants to authenticated;
grant select, insert, update, delete on table public.packaging_units to authenticated;
grant select, insert, update, delete on table public.suppliers to authenticated;
grant select, insert, update, delete on table public.stock_batches to authenticated;
grant select, insert, update, delete on table public.reminders to authenticated;

-- Inventory + transaction history are read-only to the Android client.
-- Writes happen only through apply_inventory_transaction().
grant select on table public.inventory to authenticated;
grant select on table public.stock_transactions to authenticated;

-- Profiles
drop policy if exists profiles_select_own on public.profiles;
create policy profiles_select_own
on public.profiles
for select
to authenticated
using ((select auth.uid()) = id);

drop policy if exists profiles_insert_own on public.profiles;
create policy profiles_insert_own
on public.profiles
for insert
to authenticated
with check ((select auth.uid()) = id);

drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own
on public.profiles
for update
to authenticated
using ((select auth.uid()) = id)
with check ((select auth.uid()) = id);

drop policy if exists profiles_delete_own on public.profiles;
create policy profiles_delete_own
on public.profiles
for delete
to authenticated
using ((select auth.uid()) = id);

-- Shops
drop policy if exists shops_select_own on public.shops;
create policy shops_select_own
on public.shops
for select
to authenticated
using ((select auth.uid()) = owner_id);

drop policy if exists shops_insert_own on public.shops;
create policy shops_insert_own
on public.shops
for insert
to authenticated
with check ((select auth.uid()) = owner_id);

drop policy if exists shops_update_own on public.shops;
create policy shops_update_own
on public.shops
for update
to authenticated
using ((select auth.uid()) = owner_id)
with check ((select auth.uid()) = owner_id);

drop policy if exists shops_delete_own on public.shops;
create policy shops_delete_own
on public.shops
for delete
to authenticated
using ((select auth.uid()) = owner_id);

-- Reusable shop-owned policies.
-- categories
drop policy if exists categories_select_own on public.categories;
create policy categories_select_own
on public.categories for select to authenticated
using (public.is_shop_owner(shop_id));

drop policy if exists categories_insert_own on public.categories;
create policy categories_insert_own
on public.categories for insert to authenticated
with check (public.is_shop_owner(shop_id));

drop policy if exists categories_update_own on public.categories;
create policy categories_update_own
on public.categories for update to authenticated
using (public.is_shop_owner(shop_id))
with check (public.is_shop_owner(shop_id));

drop policy if exists categories_delete_own on public.categories;
create policy categories_delete_own
on public.categories for delete to authenticated
using (public.is_shop_owner(shop_id));

-- brands
drop policy if exists brands_select_own on public.brands;
create policy brands_select_own
on public.brands for select to authenticated
using (public.is_shop_owner(shop_id));

drop policy if exists brands_insert_own on public.brands;
create policy brands_insert_own
on public.brands for insert to authenticated
with check (public.is_shop_owner(shop_id));

drop policy if exists brands_update_own on public.brands;
create policy brands_update_own
on public.brands for update to authenticated
using (public.is_shop_owner(shop_id))
with check (public.is_shop_owner(shop_id));

drop policy if exists brands_delete_own on public.brands;
create policy brands_delete_own
on public.brands for delete to authenticated
using (public.is_shop_owner(shop_id));

-- products
drop policy if exists products_select_own on public.products;
create policy products_select_own
on public.products for select to authenticated
using (public.is_shop_owner(shop_id));

drop policy if exists products_insert_own on public.products;
create policy products_insert_own
on public.products for insert to authenticated
with check (public.is_shop_owner(shop_id));

drop policy if exists products_update_own on public.products;
create policy products_update_own
on public.products for update to authenticated
using (public.is_shop_owner(shop_id))
with check (public.is_shop_owner(shop_id));

drop policy if exists products_delete_own on public.products;
create policy products_delete_own
on public.products for delete to authenticated
using (public.is_shop_owner(shop_id));

-- product_variants
drop policy if exists variants_select_own on public.product_variants;
create policy variants_select_own
on public.product_variants for select to authenticated
using (public.is_shop_owner(shop_id));

drop policy if exists variants_insert_own on public.product_variants;
create policy variants_insert_own
on public.product_variants for insert to authenticated
with check (
    public.is_shop_owner(shop_id)
    and exists (
        select 1
        from public.products p
        where p.id = product_id
          and p.shop_id = shop_id
    )
);

drop policy if exists variants_update_own on public.product_variants;
create policy variants_update_own
on public.product_variants for update to authenticated
using (public.is_shop_owner(shop_id))
with check (public.is_shop_owner(shop_id));

drop policy if exists variants_delete_own on public.product_variants;
create policy variants_delete_own
on public.product_variants for delete to authenticated
using (public.is_shop_owner(shop_id));

-- packaging_units
drop policy if exists packaging_select_own on public.packaging_units;
create policy packaging_select_own
on public.packaging_units for select to authenticated
using (public.is_shop_owner(shop_id));

drop policy if exists packaging_insert_own on public.packaging_units;
create policy packaging_insert_own
on public.packaging_units for insert to authenticated
with check (public.is_shop_owner(shop_id));

drop policy if exists packaging_update_own on public.packaging_units;
create policy packaging_update_own
on public.packaging_units for update to authenticated
using (public.is_shop_owner(shop_id))
with check (public.is_shop_owner(shop_id));

drop policy if exists packaging_delete_own on public.packaging_units;
create policy packaging_delete_own
on public.packaging_units for delete to authenticated
using (public.is_shop_owner(shop_id));

-- suppliers
drop policy if exists suppliers_select_own on public.suppliers;
create policy suppliers_select_own
on public.suppliers for select to authenticated
using (public.is_shop_owner(shop_id));

drop policy if exists suppliers_insert_own on public.suppliers;
create policy suppliers_insert_own
on public.suppliers for insert to authenticated
with check (public.is_shop_owner(shop_id));

drop policy if exists suppliers_update_own on public.suppliers;
create policy suppliers_update_own
on public.suppliers for update to authenticated
using (public.is_shop_owner(shop_id))
with check (public.is_shop_owner(shop_id));

drop policy if exists suppliers_delete_own on public.suppliers;
create policy suppliers_delete_own
on public.suppliers for delete to authenticated
using (public.is_shop_owner(shop_id));

-- inventory (read only to app)
drop policy if exists inventory_select_own on public.inventory;
create policy inventory_select_own
on public.inventory for select to authenticated
using (public.is_shop_owner(shop_id));

-- batches
drop policy if exists batches_select_own on public.stock_batches;
create policy batches_select_own
on public.stock_batches for select to authenticated
using (public.is_shop_owner(shop_id));

drop policy if exists batches_insert_own on public.stock_batches;
create policy batches_insert_own
on public.stock_batches for insert to authenticated
with check (public.is_shop_owner(shop_id));

drop policy if exists batches_update_own on public.stock_batches;
create policy batches_update_own
on public.stock_batches for update to authenticated
using (public.is_shop_owner(shop_id))
with check (public.is_shop_owner(shop_id));

drop policy if exists batches_delete_own on public.stock_batches;
create policy batches_delete_own
on public.stock_batches for delete to authenticated
using (public.is_shop_owner(shop_id));

-- transaction history (read only to app; writes via RPC)
drop policy if exists transactions_select_own on public.stock_transactions;
create policy transactions_select_own
on public.stock_transactions for select to authenticated
using (public.is_shop_owner(shop_id));

-- reminders
drop policy if exists reminders_select_own on public.reminders;
create policy reminders_select_own
on public.reminders for select to authenticated
using (public.is_shop_owner(shop_id));

drop policy if exists reminders_insert_own on public.reminders;
create policy reminders_insert_own
on public.reminders for insert to authenticated
with check (public.is_shop_owner(shop_id));

drop policy if exists reminders_update_own on public.reminders;
create policy reminders_update_own
on public.reminders for update to authenticated
using (public.is_shop_owner(shop_id))
with check (public.is_shop_owner(shop_id));

drop policy if exists reminders_delete_own on public.reminders;
create policy reminders_delete_own
on public.reminders for delete to authenticated
using (public.is_shop_owner(shop_id));

-- =========================================================
-- RPC: Get merchant context immediately after login
-- =========================================================

create or replace function public.get_my_context()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_profile jsonb;
    v_shop jsonb;
    v_complete boolean := false;
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    select to_jsonb(p)
    into v_profile
    from public.profiles p
    where p.id = v_uid;

    select to_jsonb(s)
    into v_shop
    from public.shops s
    where s.owner_id = v_uid
    limit 1;

    v_complete :=
        v_profile is not null
        and nullif(trim(coalesce(v_profile ->> 'owner_name', '')), '') is not null
        and v_shop is not null
        and nullif(trim(coalesce(v_shop ->> 'shop_name', '')), '') is not null
        and nullif(trim(coalesce(v_shop ->> 'business_type', '')), '') is not null;

    return jsonb_build_object(
        'user_id', v_uid,
        'profile', v_profile,
        'shop', v_shop,
        'setup_complete', v_complete
    );
end;
$$;

revoke all on function public.get_my_context() from public;
grant execute on function public.get_my_context() to authenticated;

-- =========================================================
-- RPC: Complete business setup atomically
-- =========================================================

create or replace function public.complete_business_setup(
    p_owner_name text,
    p_shop_name text,
    p_business_type text,
    p_preferred_language text default 'en',
    p_theme text default 'system',
    p_phone text default null,
    p_gstin text default null,
    p_city text default null,
    p_area text default null,
    p_shop_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_shop_id uuid;
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    if nullif(trim(p_owner_name), '') is null then
        raise exception 'Owner name is required';
    end if;

    if nullif(trim(p_shop_name), '') is null then
        raise exception 'Shop name is required';
    end if;

    if nullif(trim(p_business_type), '') is null then
        raise exception 'Business type is required';
    end if;

    if p_preferred_language not in ('en', 'te', 'hi') then
        raise exception 'Unsupported language';
    end if;

    if p_theme not in ('system', 'light', 'dark') then
        raise exception 'Unsupported theme';
    end if;

    insert into public.profiles (
        id,
        owner_name,
        phone,
        preferred_language,
        theme
    )
    values (
        v_uid,
        trim(p_owner_name),
        nullif(trim(coalesce(p_phone, '')), ''),
        p_preferred_language,
        p_theme
    )
    on conflict (id)
    do update set
        owner_name = excluded.owner_name,
        phone = excluded.phone,
        preferred_language = excluded.preferred_language,
        theme = excluded.theme;

    select id
    into v_shop_id
    from public.shops
    where owner_id = v_uid
    limit 1;

    if v_shop_id is null then
        v_shop_id := coalesce(p_shop_id, gen_random_uuid());

        insert into public.shops (
            id,
            owner_id,
            shop_name,
            gstin,
            business_type,
            city,
            area
        )
        values (
            v_shop_id,
            v_uid,
            trim(p_shop_name),
            nullif(upper(trim(coalesce(p_gstin, ''))), ''),
            trim(p_business_type),
            nullif(trim(coalesce(p_city, '')), ''),
            nullif(trim(coalesce(p_area, '')), '')
        );
    else
        update public.shops
        set
            shop_name = trim(p_shop_name),
            gstin = nullif(upper(trim(coalesce(p_gstin, ''))), ''),
            business_type = trim(p_business_type),
            city = nullif(trim(coalesce(p_city, '')), ''),
            area = nullif(trim(coalesce(p_area, '')), '')
        where id = v_shop_id
          and owner_id = v_uid;
    end if;

    return v_shop_id;
end;
$$;

revoke all on function public.complete_business_setup(
    text, text, text, text, text, text, text, text, text, uuid
) from public;

grant execute on function public.complete_business_setup(
    text, text, text, text, text, text, text, text, text, uuid
) to authenticated;

-- =========================================================
-- RPC: Apply stock transaction atomically + idempotently
-- =========================================================

create or replace function public.apply_inventory_transaction(
    p_transaction_id uuid,
    p_shop_id uuid,
    p_variant_id uuid,
    p_transaction_type text,
    p_quantity_base numeric,
    p_entered_quantity numeric default null,
    p_entered_unit text default null,
    p_unit_price numeric default null,
    p_total_amount numeric default null,
    p_source text default 'MANUAL',
    p_transcript text default null,
    p_notes text default null,
    p_device_id text default null,
    p_occurred_at timestamptz default now(),
    p_batch_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_before numeric(16,4);
    v_after numeric(16,4);
    v_delta numeric(16,4);
    v_existing public.stock_transactions%rowtype;
    v_total numeric(16,2);
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    if not exists (
        select 1
        from public.shops s
        where s.id = p_shop_id
          and s.owner_id = v_uid
    ) then
        raise exception 'Shop does not belong to authenticated user';
    end if;

    if not exists (
        select 1
        from public.product_variants v
        where v.id = p_variant_id
          and v.shop_id = p_shop_id
          and v.active = true
          and v.deleted_at is null
    ) then
        raise exception 'Invalid or inactive product variant';
    end if;

    if p_quantity_base is null or p_quantity_base <= 0 then
        raise exception 'Quantity must be greater than zero';
    end if;

    if p_transaction_type not in (
        'PURCHASE',
        'SALE',
        'CUSTOMER_RETURN',
        'SUPPLIER_RETURN',
        'DAMAGE',
        'EXPIRED',
        'ADJUSTMENT_IN',
        'ADJUSTMENT_OUT',
        'OPENING_STOCK',
        'TRANSFER_IN',
        'TRANSFER_OUT'
    ) then
        raise exception 'Unsupported transaction type';
    end if;

    if p_source not in ('MANUAL', 'VOICE', 'OCR', 'IMPORT') then
        raise exception 'Unsupported transaction source';
    end if;

    -- Idempotency: if the phone retries an already-synced UUID,
    -- return the original result and do not change inventory twice.
    select *
    into v_existing
    from public.stock_transactions
    where id = p_transaction_id;

    if found then
        if v_existing.shop_id <> p_shop_id
           or v_existing.variant_id <> p_variant_id then
            raise exception 'Transaction ID already belongs to a different operation';
        end if;

        return jsonb_build_object(
            'status', 'already_applied',
            'transaction_id', v_existing.id,
            'before_quantity_base', v_existing.before_quantity_base,
            'after_quantity_base', v_existing.after_quantity_base
        );
    end if;

    insert into public.inventory (
        variant_id,
        shop_id,
        quantity_base
    )
    values (
        p_variant_id,
        p_shop_id,
        0
    )
    on conflict (variant_id) do nothing;

    select i.quantity_base
    into v_before
    from public.inventory i
    where i.variant_id = p_variant_id
      and i.shop_id = p_shop_id
    for update;

    v_delta :=
        case
            when p_transaction_type in (
                'PURCHASE',
                'CUSTOMER_RETURN',
                'ADJUSTMENT_IN',
                'OPENING_STOCK',
                'TRANSFER_IN'
            )
            then p_quantity_base

            else -p_quantity_base
        end;

    v_after := v_before + v_delta;

    if v_after < 0 then
        raise exception
            'Insufficient stock. Available %, requested outbound %',
            v_before,
            p_quantity_base;
    end if;

    v_total :=
        coalesce(
            p_total_amount,
            case
                when p_unit_price is not null
                then round((p_quantity_base * p_unit_price)::numeric, 2)
                else null
            end
        );

    insert into public.stock_transactions (
        id,
        shop_id,
        variant_id,
        batch_id,
        transaction_type,
        quantity_base,
        entered_quantity,
        entered_unit,
        before_quantity_base,
        after_quantity_base,
        unit_price,
        total_amount,
        source,
        transcript,
        notes,
        device_id,
        occurred_at,
        created_by
    )
    values (
        p_transaction_id,
        p_shop_id,
        p_variant_id,
        p_batch_id,
        p_transaction_type,
        p_quantity_base,
        p_entered_quantity,
        p_entered_unit,
        v_before,
        v_after,
        p_unit_price,
        v_total,
        p_source,
        p_transcript,
        p_notes,
        p_device_id,
        coalesce(p_occurred_at, now()),
        v_uid
    );

    update public.inventory
    set
        quantity_base = v_after,
        updated_at = now()
    where variant_id = p_variant_id
      and shop_id = p_shop_id;

    return jsonb_build_object(
        'status', 'applied',
        'transaction_id', p_transaction_id,
        'before_quantity_base', v_before,
        'after_quantity_base', v_after,
        'delta_base', v_delta,
        'total_amount', v_total
    );

exception
    when unique_violation then
        select *
        into v_existing
        from public.stock_transactions
        where id = p_transaction_id;

        if found then
            return jsonb_build_object(
                'status', 'already_applied',
                'transaction_id', v_existing.id,
                'before_quantity_base', v_existing.before_quantity_base,
                'after_quantity_base', v_existing.after_quantity_base
            );
        end if;

        raise;
end;
$$;

revoke all on function public.apply_inventory_transaction(
    uuid, uuid, uuid, text, numeric, numeric, text, numeric, numeric,
    text, text, text, text, timestamptz, uuid
) from public;

grant execute on function public.apply_inventory_transaction(
    uuid, uuid, uuid, text, numeric, numeric, text, numeric, numeric,
    text, text, text, text, timestamptz, uuid
) to authenticated;

-- =========================================================
-- RPC: Dashboard summary
-- =========================================================

create or replace function public.get_dashboard_summary(
    p_shop_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_total_variants bigint;
    v_low_stock bigint;
    v_out_of_stock bigint;
    v_stock_value numeric;
    v_today_sales numeric;
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    if not exists (
        select 1
        from public.shops s
        where s.id = p_shop_id
          and s.owner_id = v_uid
    ) then
        raise exception 'Unauthorized shop';
    end if;

    select count(*)
    into v_total_variants
    from public.product_variants v
    where v.shop_id = p_shop_id
      and v.active = true
      and v.deleted_at is null;

    select count(*)
    into v_low_stock
    from public.product_variants v
    left join public.inventory i
      on i.variant_id = v.id
    where v.shop_id = p_shop_id
      and v.active = true
      and v.deleted_at is null
      and coalesce(i.quantity_base, 0) > 0
      and coalesce(i.quantity_base, 0) <= v.reorder_level;

    select count(*)
    into v_out_of_stock
    from public.product_variants v
    left join public.inventory i
      on i.variant_id = v.id
    where v.shop_id = p_shop_id
      and v.active = true
      and v.deleted_at is null
      and coalesce(i.quantity_base, 0) <= 0;

    select coalesce(
        sum(
            coalesce(i.quantity_base, 0)
            * coalesce(v.purchase_price, 0)
        ),
        0
    )
    into v_stock_value
    from public.product_variants v
    left join public.inventory i
      on i.variant_id = v.id
    where v.shop_id = p_shop_id
      and v.active = true
      and v.deleted_at is null;

    select coalesce(sum(t.total_amount), 0)
    into v_today_sales
    from public.stock_transactions t
    where t.shop_id = p_shop_id
      and t.transaction_type = 'SALE'
      and t.occurred_at >= date_trunc('day', now());

    return jsonb_build_object(
        'total_variants', v_total_variants,
        'low_stock', v_low_stock,
        'out_of_stock', v_out_of_stock,
        'inventory_value_estimate', v_stock_value,
        'today_sales', v_today_sales
    );
end;
$$;

revoke all on function public.get_dashboard_summary(uuid) from public;
grant execute on function public.get_dashboard_summary(uuid) to authenticated;

commit;
