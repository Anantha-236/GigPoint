-- DhwaniMitra Production Inventory Core v1
-- Non-destructive extension of the existing Supabase schema.

create extension if not exists pgcrypto;

create table if not exists public.product_variants (
    id uuid primary key default gen_random_uuid(),
    product_id uuid not null references public.products(id) on delete restrict,
    sku text not null,
    variant_name text,
    package_quantity numeric,
    package_unit text,
    container_type text,
    selling_unit text not null,
    purchase_price numeric,
    selling_price numeric,
    barcode text,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(product_id, sku)
);

create index if not exists idx_product_variants_product
    on public.product_variants(product_id, active);

create table if not exists public.inventory (
    variant_id uuid primary key
        references public.product_variants(id) on delete restrict,
    quantity numeric not null default 0 check (quantity >= 0),
    reorder_level numeric not null default 0 check (reorder_level >= 0),
    updated_at timestamptz not null default now()
);

create table if not exists public.product_aliases (
    id uuid primary key default gen_random_uuid(),
    product_id uuid not null references public.products(id) on delete cascade,
    variant_id uuid references public.product_variants(id) on delete cascade,
    alias text not null,
    normalized_alias text not null,
    language text,
    source text not null default 'MERCHANT',
    confidence numeric not null default 1.0
        check (confidence >= 0 and confidence <= 1),
    created_at timestamptz not null default now()
);

create index if not exists idx_product_alias_normalized
    on public.product_aliases(normalized_alias);

create table if not exists public.stock_batches (
    id uuid primary key default gen_random_uuid(),
    variant_id uuid not null
        references public.product_variants(id) on delete restrict,
    quantity_received numeric not null check (quantity_received >= 0),
    quantity_remaining numeric not null check (quantity_remaining >= 0),
    purchase_price numeric,
    supplier text,
    received_at timestamptz not null default now(),
    manufactured_at timestamptz,
    expires_at timestamptz,
    created_at timestamptz not null default now()
);

create table if not exists public.voice_commands (
    id uuid primary key,
    merchant_id uuid,
    transcript text not null,
    intent text not null,
    status text not null,
    language_tag text,
    speaker_id text,
    speaker_confidence numeric
        check (
            speaker_confidence is null or
            (speaker_confidence >= 0 and speaker_confidence <= 1)
        ),
    created_at timestamptz not null default now(),
    executed_at timestamptz
);

create table if not exists public.voice_command_items (
    id uuid primary key default gen_random_uuid(),
    command_id uuid not null
        references public.voice_commands(id) on delete cascade,
    ordinal integer not null,
    product_mention text not null,
    product_id uuid references public.products(id) on delete restrict,
    variant_id uuid references public.product_variants(id) on delete restrict,
    operation text not null,
    quantity numeric,
    quantity_unit text,
    package_quantity numeric,
    package_unit text,
    packaging text,
    price numeric,
    confidence numeric not null default 0
        check (confidence >= 0 and confidence <= 1),
    status text not null default 'PENDING',
    clarification text,
    unique(command_id, ordinal)
);

create table if not exists public.audit_events (
    id uuid primary key default gen_random_uuid(),
    command_id uuid references public.voice_commands(id) on delete set null,
    event_type text not null,
    entity_type text not null,
    entity_id text,
    payload jsonb,
    created_at timestamptz not null default now()
);

create table if not exists public.idempotency_keys (
    command_id uuid primary key
        references public.voice_commands(id) on delete cascade,
    result_message text not null,
    created_at timestamptz not null default now()
);

alter table if exists public.stock_transactions
    add column if not exists variant_id uuid
        references public.product_variants(id);

alter table if exists public.stock_transactions
    add column if not exists command_id uuid
        references public.voice_commands(id);

alter table if exists public.stock_transactions
    add column if not exists reverses_transaction_id uuid
        references public.stock_transactions(id);

alter table if exists public.stock_transactions
    add column if not exists reversed_by_transaction_id uuid
        references public.stock_transactions(id);

create index if not exists idx_stock_transactions_command
    on public.stock_transactions(command_id);

alter table public.product_variants enable row level security;
alter table public.inventory enable row level security;
alter table public.product_aliases enable row level security;
alter table public.stock_batches enable row level security;
alter table public.voice_commands enable row level security;
alter table public.voice_command_items enable row level security;
alter table public.audit_events enable row level security;
alter table public.idempotency_keys enable row level security;

-- Intentionally no permissive policies here.
-- Before production deployment, add ownership policies tied to the exact
-- merchant/shop/product ownership columns already present in your backend.
