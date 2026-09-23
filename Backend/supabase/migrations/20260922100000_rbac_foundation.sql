-- DhwaniMitra RBAC Foundation
-- 2026-09-22
begin;

create table if not exists public.permissions (
    code text primary key,
    description text not null,
    created_at timestamptz not null default now()
);

insert into public.permissions (code, description)
values
    ('shop.read', 'View shop/business information'),
    ('settings.read', 'View business settings'),
    ('settings.update', 'Change business settings'),
    ('member.read', 'View shop members'),
    ('member.invite', 'Invite members'),
    ('member.update_role', 'Change member roles'),
    ('member.remove', 'Remove members'),
    ('product.read', 'View products, variants, brands and categories'),
    ('product.create', 'Create products and variants'),
    ('product.update', 'Edit products and variants'),
    ('product.delete', 'Delete/archive products and variants'),
    ('price.update', 'Change selling prices and MRP'),
    ('inventory.read', 'View current stock and batches'),
    ('inventory.adjust', 'Perform stock adjustments and stock receipts/issues'),
    ('inventory.transfer', 'Transfer inventory'),
    ('inventory.transaction.read', 'View full stock transaction history'),
    ('supplier.read', 'View suppliers'),
    ('supplier.manage', 'Create and edit suppliers'),
    ('sale.read', 'View sales'),
    ('sale.create', 'Create sales'),
    ('sale.refund', 'Create customer returns/refunds'),
    ('sale.void', 'Void eligible sales'),
    ('purchase.read', 'View purchases'),
    ('purchase.create', 'Create purchases and supplier returns'),
    ('purchase.approve', 'Approve purchases'),
    ('alert.read', 'View alerts and reminders'),
    ('alert.manage', 'Create, update and complete alerts/reminders'),
    ('report.read', 'View operational reports and dashboard analytics'),
    ('cost_price.read', 'View purchase/cost prices'),
    ('profit.read', 'View profit and margin information'),
    ('audit.read', 'View audit history'),
    ('business.delete', 'Delete the business')
on conflict (code) do update set description = excluded.description;

create table if not exists public.roles (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid references public.shops(id) on delete cascade,
    code text not null,
    name text not null,
    description text,
    is_system boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists ux_roles_system_code
    on public.roles(code) where shop_id is null;

create unique index if not exists ux_roles_shop_code
    on public.roles(shop_id, code) where shop_id is not null;

insert into public.roles (shop_id, code, name, description, is_system)
values
    (null, 'OWNER', 'Owner', 'Primary business owner with complete access.', true),
    (null, 'MANAGER', 'Manager', 'Runs day-to-day shop operations.', true),
    (null, 'CASHIER', 'Cashier', 'Billing and product availability access.', true),
    (null, 'INVENTORY_MANAGER', 'Inventory Manager', 'Catalog, purchasing and stock operations.', true),
    (null, 'ACCOUNTANT', 'Accountant', 'Financial reporting and transaction visibility.', true),
    (null, 'VIEWER', 'Viewer', 'Read-only operational access.', true)
on conflict (code) where shop_id is null
do update set
    name = excluded.name,
    description = excluded.description,
    is_system = true;

create table if not exists public.role_permissions (
    role_id uuid not null references public.roles(id) on delete cascade,
    permission_code text not null references public.permissions(code) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (role_id, permission_code)
);

insert into public.role_permissions (role_id, permission_code)
select r.id, p.code
from public.roles r
cross join public.permissions p
where r.shop_id is null and r.code = 'OWNER'
on conflict do nothing;

insert into public.role_permissions (role_id, permission_code)
select r.id, x.permission_code
from public.roles r
cross join (values
    ('shop.read'),('settings.read'),('settings.update'),('member.read'),('member.invite'),
    ('product.read'),('product.create'),('product.update'),('product.delete'),('price.update'),
    ('inventory.read'),('inventory.adjust'),('inventory.transfer'),('inventory.transaction.read'),
    ('supplier.read'),('supplier.manage'),('sale.read'),('sale.create'),('sale.refund'),('sale.void'),
    ('purchase.read'),('purchase.create'),('purchase.approve'),('alert.read'),('alert.manage'),
    ('report.read'),('cost_price.read'),('profit.read'),('audit.read')
) as x(permission_code)
where r.shop_id is null and r.code = 'MANAGER'
on conflict do nothing;

insert into public.role_permissions (role_id, permission_code)
select r.id, x.permission_code
from public.roles r
cross join (values
    ('shop.read'),('product.read'),('inventory.read'),('sale.read'),('sale.create'),('alert.read')
) as x(permission_code)
where r.shop_id is null and r.code = 'CASHIER'
on conflict do nothing;

insert into public.role_permissions (role_id, permission_code)
select r.id, x.permission_code
from public.roles r
cross join (values
    ('shop.read'),('product.read'),('product.create'),('product.update'),('product.delete'),('price.update'),
    ('inventory.read'),('inventory.adjust'),('inventory.transfer'),('inventory.transaction.read'),
    ('supplier.read'),('supplier.manage'),('purchase.read'),('purchase.create'),('purchase.approve'),
    ('alert.read'),('alert.manage'),('report.read'),('cost_price.read'),('audit.read')
) as x(permission_code)
where r.shop_id is null and r.code = 'INVENTORY_MANAGER'
on conflict do nothing;

insert into public.role_permissions (role_id, permission_code)
select r.id, x.permission_code
from public.roles r
cross join (values
    ('shop.read'),('product.read'),('inventory.read'),('inventory.transaction.read'),('supplier.read'),
    ('sale.read'),('purchase.read'),('alert.read'),('report.read'),('cost_price.read'),('profit.read'),('audit.read')
) as x(permission_code)
where r.shop_id is null and r.code = 'ACCOUNTANT'
on conflict do nothing;

insert into public.role_permissions (role_id, permission_code)
select r.id, x.permission_code
from public.roles r
cross join (values
    ('shop.read'),('product.read'),('inventory.read'),('alert.read'),('report.read')
) as x(permission_code)
where r.shop_id is null and r.code = 'VIEWER'
on conflict do nothing;

create table if not exists public.shop_memberships (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role_id uuid not null references public.roles(id) on delete restrict,
    status text not null default 'ACTIVE' check (status in ('PENDING','ACTIVE','SUSPENDED','REVOKED')),
    invited_by uuid references auth.users(id) on delete set null,
    joined_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (shop_id, user_id)
);

create index if not exists idx_shop_memberships_user on public.shop_memberships(user_id, status);
create index if not exists idx_shop_memberships_shop on public.shop_memberships(shop_id, status);

create table if not exists public.audit_events (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete cascade,
    actor_user_id uuid references auth.users(id) on delete set null,
    actor_role text,
    action text not null,
    resource_type text not null,
    resource_id text,
    source text not null default 'APP' check (source in ('APP','VOICE','OCR','IMPORT','SYSTEM','API')),
    before_data jsonb,
    after_data jsonb,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_audit_events_shop_time on public.audit_events(shop_id, created_at desc);
create index if not exists idx_audit_events_actor on public.audit_events(actor_user_id, created_at desc);

drop trigger if exists trg_roles_updated_at on public.roles;
create trigger trg_roles_updated_at before update on public.roles
for each row execute function public.set_updated_at();

drop trigger if exists trg_memberships_updated_at on public.shop_memberships;
create trigger trg_memberships_updated_at before update on public.shop_memberships
for each row execute function public.set_updated_at();

create or replace function public.is_shop_member(target_shop_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select
        exists (
            select 1 from public.shops s
            where s.id = target_shop_id and s.owner_id = (select auth.uid())
        )
        or
        exists (
            select 1 from public.shop_memberships m
            where m.shop_id = target_shop_id
              and m.user_id = (select auth.uid())
              and m.status = 'ACTIVE'
        );
$$;
revoke all on function public.is_shop_member(uuid) from public;
grant execute on function public.is_shop_member(uuid) to authenticated;

create or replace function public.current_shop_role(target_shop_id uuid)
returns text
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_role text;
begin
    if v_uid is null then return null; end if;

    if exists (
        select 1 from public.shops s
        where s.id = target_shop_id and s.owner_id = v_uid
    ) then
        return 'OWNER';
    end if;

    select r.code into v_role
    from public.shop_memberships m
    join public.roles r on r.id = m.role_id
    where m.shop_id = target_shop_id
      and m.user_id = v_uid
      and m.status = 'ACTIVE'
      and (r.shop_id is null or r.shop_id = target_shop_id)
    limit 1;

    return v_role;
end;
$$;
revoke all on function public.current_shop_role(uuid) from public;
grant execute on function public.current_shop_role(uuid) to authenticated;

create or replace function public.has_shop_permission(target_shop_id uuid, required_permission text)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then return false; end if;

    if exists (
        select 1 from public.shops s
        where s.id = target_shop_id and s.owner_id = v_uid
    ) then
        return true;
    end if;

    return exists (
        select 1
        from public.shop_memberships m
        join public.roles r on r.id = m.role_id
        join public.role_permissions rp on rp.role_id = r.id
        where m.shop_id = target_shop_id
          and m.user_id = v_uid
          and m.status = 'ACTIVE'
          and (r.shop_id is null or r.shop_id = target_shop_id)
          and rp.permission_code = required_permission
    );
end;
$$;
revoke all on function public.has_shop_permission(uuid, text) from public;
grant execute on function public.has_shop_permission(uuid, text) to authenticated;

create or replace function public.write_audit_event(
    p_shop_id uuid,
    p_action text,
    p_resource_type text,
    p_resource_id text default null,
    p_source text default 'APP',
    p_before_data jsonb default null,
    p_after_data jsonb default null,
    p_metadata jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_id uuid := gen_random_uuid();
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if not public.is_shop_member(p_shop_id) then raise exception 'Unauthorized shop'; end if;

    insert into public.audit_events (
        id, shop_id, actor_user_id, actor_role, action, resource_type,
        resource_id, source, before_data, after_data, metadata
    ) values (
        v_id, p_shop_id, auth.uid(), public.current_shop_role(p_shop_id),
        p_action, p_resource_type, p_resource_id,
        case when p_source in ('APP','VOICE','OCR','IMPORT','SYSTEM','API') then p_source else 'APP' end,
        p_before_data, p_after_data, coalesce(p_metadata, '{}'::jsonb)
    );
    return v_id;
end;
$$;
revoke all on function public.write_audit_event(uuid,text,text,text,text,jsonb,jsonb,jsonb) from public;

insert into public.shop_memberships (shop_id,user_id,role_id,status,invited_by,joined_at)
select s.id,s.owner_id,r.id,'ACTIVE',s.owner_id,now()
from public.shops s
join public.roles r on r.shop_id is null and r.code = 'OWNER'
on conflict (shop_id,user_id)
do update set role_id=excluded.role_id,status='ACTIVE',joined_at=coalesce(public.shop_memberships.joined_at,now());

create or replace function public.ensure_owner_membership()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare v_owner_role_id uuid;
begin
    select r.id into v_owner_role_id from public.roles r
    where r.shop_id is null and r.code='OWNER' limit 1;
    if v_owner_role_id is null then raise exception 'OWNER system role is missing'; end if;

    insert into public.shop_memberships (shop_id,user_id,role_id,status,invited_by,joined_at)
    values (new.id,new.owner_id,v_owner_role_id,'ACTIVE',new.owner_id,now())
    on conflict (shop_id,user_id)
    do update set role_id=excluded.role_id,status='ACTIVE',joined_at=coalesce(public.shop_memberships.joined_at,now());
    return new;
end;
$$;

drop trigger if exists trg_ensure_owner_membership on public.shops;
create trigger trg_ensure_owner_membership
after insert or update of owner_id on public.shops
for each row execute function public.ensure_owner_membership();

create or replace function public.guard_owner_membership()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner uuid;
    v_owner_role uuid;
begin
    select s.owner_id into v_owner from public.shops s where s.id=old.shop_id;
    select r.id into v_owner_role from public.roles r where r.shop_id is null and r.code='OWNER' limit 1;

    if old.user_id = v_owner then
        if tg_op='DELETE' then raise exception 'The canonical owner membership cannot be removed'; end if;
        if new.user_id<>old.user_id or new.shop_id<>old.shop_id or new.role_id<>v_owner_role or new.status<>'ACTIVE' then
            raise exception 'The canonical owner membership cannot be downgraded or suspended';
        end if;
    end if;
    return case when tg_op='DELETE' then old else new end;
end;
$$;

drop trigger if exists trg_guard_owner_membership on public.shop_memberships;
create trigger trg_guard_owner_membership
before update or delete on public.shop_memberships
for each row execute function public.guard_owner_membership();

alter table public.permissions enable row level security;
alter table public.roles enable row level security;
alter table public.role_permissions enable row level security;
alter table public.shop_memberships enable row level security;
alter table public.audit_events enable row level security;

revoke all on table public.permissions from anon,authenticated;
revoke all on table public.roles from anon,authenticated;
revoke all on table public.role_permissions from anon,authenticated;
revoke all on table public.shop_memberships from anon,authenticated;
revoke all on table public.audit_events from anon,authenticated;

grant select on table public.permissions to authenticated;
grant select,insert,update,delete on table public.roles to authenticated;
grant select,insert,delete on table public.role_permissions to authenticated;
grant select,insert,update,delete on table public.shop_memberships to authenticated;
grant select on table public.audit_events to authenticated;

drop policy if exists permissions_select_authenticated on public.permissions;
create policy permissions_select_authenticated on public.permissions for select to authenticated using (true);

drop policy if exists roles_select_available on public.roles;
create policy roles_select_available on public.roles for select to authenticated
using (shop_id is null or public.is_shop_member(shop_id));

drop policy if exists roles_insert_custom on public.roles;
create policy roles_insert_custom on public.roles for insert to authenticated
with check (shop_id is not null and is_system=false and public.has_shop_permission(shop_id,'member.update_role'));

drop policy if exists roles_update_custom on public.roles;
create policy roles_update_custom on public.roles for update to authenticated
using (shop_id is not null and is_system=false and public.has_shop_permission(shop_id,'member.update_role'))
with check (shop_id is not null and is_system=false and public.has_shop_permission(shop_id,'member.update_role'));

drop policy if exists roles_delete_custom on public.roles;
create policy roles_delete_custom on public.roles for delete to authenticated
using (shop_id is not null and is_system=false and public.has_shop_permission(shop_id,'member.update_role'));

drop policy if exists role_permissions_select_available on public.role_permissions;
create policy role_permissions_select_available on public.role_permissions for select to authenticated
using (exists (
    select 1 from public.roles r
    where r.id=role_id and (r.shop_id is null or public.is_shop_member(r.shop_id))
));

drop policy if exists role_permissions_insert_custom on public.role_permissions;
create policy role_permissions_insert_custom on public.role_permissions for insert to authenticated
with check (exists (
    select 1 from public.roles r
    where r.id=role_id and r.shop_id is not null and r.is_system=false
      and public.has_shop_permission(r.shop_id,'member.update_role')
));

drop policy if exists role_permissions_delete_custom on public.role_permissions;
create policy role_permissions_delete_custom on public.role_permissions for delete to authenticated
using (exists (
    select 1 from public.roles r
    where r.id=role_id and r.shop_id is not null and r.is_system=false
      and public.has_shop_permission(r.shop_id,'member.update_role')
));

drop policy if exists memberships_select_allowed on public.shop_memberships;
create policy memberships_select_allowed on public.shop_memberships for select to authenticated
using (user_id=(select auth.uid()) or public.has_shop_permission(shop_id,'member.read'));

drop policy if exists memberships_insert_allowed on public.shop_memberships;
create policy memberships_insert_allowed on public.shop_memberships for insert to authenticated
with check (
    public.has_shop_permission(shop_id,'member.invite')
    and exists (select 1 from public.roles r where r.id=role_id and (r.shop_id is null or r.shop_id=shop_id) and r.code<>'OWNER')
);

drop policy if exists memberships_update_allowed on public.shop_memberships;
create policy memberships_update_allowed on public.shop_memberships for update to authenticated
using (public.has_shop_permission(shop_id,'member.update_role'))
with check (
    public.has_shop_permission(shop_id,'member.update_role')
    and exists (select 1 from public.roles r where r.id=role_id and (r.shop_id is null or r.shop_id=shop_id) and r.code<>'OWNER')
);

drop policy if exists memberships_delete_allowed on public.shop_memberships;
create policy memberships_delete_allowed on public.shop_memberships for delete to authenticated
using (public.has_shop_permission(shop_id,'member.remove'));

drop policy if exists audit_select_allowed on public.audit_events;
create policy audit_select_allowed on public.audit_events for select to authenticated
using (public.has_shop_permission(shop_id,'audit.read'));

-- Existing shop-owned tables: replace owner-only policies with RBAC.
-- Shops
drop policy if exists shops_select_own on public.shops;
drop policy if exists shops_insert_own on public.shops;
drop policy if exists shops_update_own on public.shops;
drop policy if exists shops_delete_own on public.shops;
create policy shops_select_member on public.shops for select to authenticated using (public.has_shop_permission(id,'shop.read'));
create policy shops_insert_owner on public.shops for insert to authenticated with check (owner_id=(select auth.uid()));
create policy shops_update_settings on public.shops for update to authenticated
using (public.has_shop_permission(id,'settings.update')) with check (public.has_shop_permission(id,'settings.update'));
create policy shops_delete_business on public.shops for delete to authenticated using (public.has_shop_permission(id,'business.delete'));

-- Categories
drop policy if exists categories_select_own on public.categories;
drop policy if exists categories_insert_own on public.categories;
drop policy if exists categories_update_own on public.categories;
drop policy if exists categories_delete_own on public.categories;
create policy categories_select_rbac on public.categories for select to authenticated using (public.has_shop_permission(shop_id,'product.read'));
create policy categories_insert_rbac on public.categories for insert to authenticated with check (public.has_shop_permission(shop_id,'product.create'));
create policy categories_update_rbac on public.categories for update to authenticated using (public.has_shop_permission(shop_id,'product.update')) with check (public.has_shop_permission(shop_id,'product.update'));
create policy categories_delete_rbac on public.categories for delete to authenticated using (public.has_shop_permission(shop_id,'product.delete'));

-- Brands
drop policy if exists brands_select_own on public.brands;
drop policy if exists brands_insert_own on public.brands;
drop policy if exists brands_update_own on public.brands;
drop policy if exists brands_delete_own on public.brands;
create policy brands_select_rbac on public.brands for select to authenticated using (public.has_shop_permission(shop_id,'product.read'));
create policy brands_insert_rbac on public.brands for insert to authenticated with check (public.has_shop_permission(shop_id,'product.create'));
create policy brands_update_rbac on public.brands for update to authenticated using (public.has_shop_permission(shop_id,'product.update')) with check (public.has_shop_permission(shop_id,'product.update'));
create policy brands_delete_rbac on public.brands for delete to authenticated using (public.has_shop_permission(shop_id,'product.delete'));

-- Products
drop policy if exists products_select_own on public.products;
drop policy if exists products_insert_own on public.products;
drop policy if exists products_update_own on public.products;
drop policy if exists products_delete_own on public.products;
create policy products_select_rbac on public.products for select to authenticated using (public.has_shop_permission(shop_id,'product.read'));
create policy products_insert_rbac on public.products for insert to authenticated with check (public.has_shop_permission(shop_id,'product.create'));
create policy products_update_rbac on public.products for update to authenticated using (public.has_shop_permission(shop_id,'product.update')) with check (public.has_shop_permission(shop_id,'product.update'));
create policy products_delete_rbac on public.products for delete to authenticated using (public.has_shop_permission(shop_id,'product.delete'));

-- Variants
drop policy if exists variants_select_own on public.product_variants;
drop policy if exists variants_insert_own on public.product_variants;
drop policy if exists variants_update_own on public.product_variants;
drop policy if exists variants_delete_own on public.product_variants;
create policy variants_select_rbac on public.product_variants for select to authenticated using (public.has_shop_permission(shop_id,'product.read'));
create policy variants_insert_rbac on public.product_variants for insert to authenticated
with check (public.has_shop_permission(shop_id,'product.create') and exists (select 1 from public.products p where p.id=product_id and p.shop_id=shop_id));
create policy variants_update_rbac on public.product_variants for update to authenticated using (public.has_shop_permission(shop_id,'product.update')) with check (public.has_shop_permission(shop_id,'product.update'));
create policy variants_delete_rbac on public.product_variants for delete to authenticated using (public.has_shop_permission(shop_id,'product.delete'));

-- Packaging
drop policy if exists packaging_select_own on public.packaging_units;
drop policy if exists packaging_insert_own on public.packaging_units;
drop policy if exists packaging_update_own on public.packaging_units;
drop policy if exists packaging_delete_own on public.packaging_units;
create policy packaging_select_rbac on public.packaging_units for select to authenticated using (public.has_shop_permission(shop_id,'product.read'));
create policy packaging_insert_rbac on public.packaging_units for insert to authenticated with check (public.has_shop_permission(shop_id,'product.create'));
create policy packaging_update_rbac on public.packaging_units for update to authenticated using (public.has_shop_permission(shop_id,'product.update')) with check (public.has_shop_permission(shop_id,'product.update'));
create policy packaging_delete_rbac on public.packaging_units for delete to authenticated using (public.has_shop_permission(shop_id,'product.delete'));

-- Suppliers
drop policy if exists suppliers_select_own on public.suppliers;
drop policy if exists suppliers_insert_own on public.suppliers;
drop policy if exists suppliers_update_own on public.suppliers;
drop policy if exists suppliers_delete_own on public.suppliers;
create policy suppliers_select_rbac on public.suppliers for select to authenticated using (public.has_shop_permission(shop_id,'supplier.read'));
create policy suppliers_insert_rbac on public.suppliers for insert to authenticated with check (public.has_shop_permission(shop_id,'supplier.manage'));
create policy suppliers_update_rbac on public.suppliers for update to authenticated using (public.has_shop_permission(shop_id,'supplier.manage')) with check (public.has_shop_permission(shop_id,'supplier.manage'));
create policy suppliers_delete_rbac on public.suppliers for delete to authenticated using (public.has_shop_permission(shop_id,'supplier.manage'));

-- Inventory read-only
drop policy if exists inventory_select_own on public.inventory;
create policy inventory_select_rbac on public.inventory for select to authenticated using (public.has_shop_permission(shop_id,'inventory.read'));

-- Batches
drop policy if exists batches_select_own on public.stock_batches;
drop policy if exists batches_insert_own on public.stock_batches;
drop policy if exists batches_update_own on public.stock_batches;
drop policy if exists batches_delete_own on public.stock_batches;
create policy batches_select_rbac on public.stock_batches for select to authenticated using (public.has_shop_permission(shop_id,'inventory.read'));
create policy batches_insert_rbac on public.stock_batches for insert to authenticated with check (public.has_shop_permission(shop_id,'inventory.adjust'));
create policy batches_update_rbac on public.stock_batches for update to authenticated using (public.has_shop_permission(shop_id,'inventory.adjust')) with check (public.has_shop_permission(shop_id,'inventory.adjust'));
create policy batches_delete_rbac on public.stock_batches for delete to authenticated using (public.has_shop_permission(shop_id,'inventory.adjust'));

-- Stock transaction history
drop policy if exists transactions_select_own on public.stock_transactions;
create policy transactions_select_rbac on public.stock_transactions for select to authenticated using (public.has_shop_permission(shop_id,'inventory.transaction.read'));

-- Reminders
drop policy if exists reminders_select_own on public.reminders;
drop policy if exists reminders_insert_own on public.reminders;
drop policy if exists reminders_update_own on public.reminders;
drop policy if exists reminders_delete_own on public.reminders;
create policy reminders_select_rbac on public.reminders for select to authenticated using (public.has_shop_permission(shop_id,'alert.read'));
create policy reminders_insert_rbac on public.reminders for insert to authenticated with check (public.has_shop_permission(shop_id,'alert.manage'));
create policy reminders_update_rbac on public.reminders for update to authenticated using (public.has_shop_permission(shop_id,'alert.manage')) with check (public.has_shop_permission(shop_id,'alert.manage'));
create policy reminders_delete_rbac on public.reminders for delete to authenticated using (public.has_shop_permission(shop_id,'alert.manage'));

create or replace function public.get_my_access(p_shop_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_role text;
    v_membership_id uuid;
    v_is_owner boolean;
    v_permissions jsonb;
begin
    if v_uid is null then raise exception 'Authentication required'; end if;
    if not public.is_shop_member(p_shop_id) then raise exception 'Unauthorized shop'; end if;

    v_is_owner := exists (select 1 from public.shops s where s.id=p_shop_id and s.owner_id=v_uid);
    v_role := public.current_shop_role(p_shop_id);

    select m.id into v_membership_id
    from public.shop_memberships m
    where m.shop_id=p_shop_id and m.user_id=v_uid and m.status='ACTIVE'
    limit 1;

    if v_is_owner then
        select coalesce(jsonb_agg(p.code order by p.code),'[]'::jsonb)
        into v_permissions from public.permissions p;
    else
        select coalesce(jsonb_agg(rp.permission_code order by rp.permission_code),'[]'::jsonb)
        into v_permissions
        from public.shop_memberships m
        join public.roles r on r.id=m.role_id
        join public.role_permissions rp on rp.role_id=r.id
        where m.shop_id=p_shop_id and m.user_id=v_uid and m.status='ACTIVE'
          and (r.shop_id is null or r.shop_id=p_shop_id);
    end if;

    return jsonb_build_object(
        'shop_id',p_shop_id,
        'membership_id',v_membership_id,
        'role',v_role,
        'is_owner',v_is_owner,
        'permissions',coalesce(v_permissions,'[]'::jsonb)
    );
end;
$$;
revoke all on function public.get_my_access(uuid) from public;
grant execute on function public.get_my_access(uuid) to authenticated;

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
    v_shop_id uuid;
    v_access jsonb;
    v_complete boolean := false;
    v_is_owner boolean := false;
begin
    if v_uid is null then raise exception 'Authentication required'; end if;

    select to_jsonb(p) into v_profile from public.profiles p where p.id=v_uid;

    select s.id,to_jsonb(s) into v_shop_id,v_shop
    from public.shops s where s.owner_id=v_uid limit 1;

    if v_shop_id is null then
        select s.id,to_jsonb(s) into v_shop_id,v_shop
        from public.shop_memberships m
        join public.shops s on s.id=m.shop_id
        where m.user_id=v_uid and m.status='ACTIVE'
        order by m.created_at limit 1;
    end if;

    if v_shop_id is not null then
        v_is_owner := exists (select 1 from public.shops s where s.id=v_shop_id and s.owner_id=v_uid);
        v_access := public.get_my_access(v_shop_id);
    end if;

    if v_is_owner then
        v_complete := v_profile is not null
            and nullif(trim(coalesce(v_profile->>'owner_name','')),'') is not null
            and v_shop is not null
            and nullif(trim(coalesce(v_shop->>'shop_name','')),'') is not null
            and nullif(trim(coalesce(v_shop->>'business_type','')),'') is not null;
    else
        v_complete := v_shop is not null
            and nullif(trim(coalesce(v_shop->>'shop_name','')),'') is not null
            and nullif(trim(coalesce(v_shop->>'business_type','')),'') is not null;
    end if;

    return jsonb_build_object(
        'user_id',v_uid,
        'profile',v_profile,
        'shop',v_shop,
        'access',v_access,
        'setup_complete',v_complete
    );
end;
$$;
revoke all on function public.get_my_context() from public;
grant execute on function public.get_my_context() to authenticated;

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
    v_required_permission text;
begin
    if v_uid is null then raise exception 'Authentication required'; end if;

    if p_transaction_type not in ('PURCHASE','SALE','CUSTOMER_RETURN','SUPPLIER_RETURN','DAMAGE','EXPIRED','ADJUSTMENT_IN','ADJUSTMENT_OUT','OPENING_STOCK','TRANSFER_IN','TRANSFER_OUT') then
        raise exception 'Unsupported transaction type';
    end if;

    v_required_permission := case
        when p_transaction_type='SALE' then 'sale.create'
        when p_transaction_type='CUSTOMER_RETURN' then 'sale.refund'
        when p_transaction_type in ('PURCHASE','SUPPLIER_RETURN') then 'purchase.create'
        when p_transaction_type in ('TRANSFER_IN','TRANSFER_OUT') then 'inventory.transfer'
        else 'inventory.adjust'
    end;

    if not public.has_shop_permission(p_shop_id,v_required_permission) then
        raise exception 'Permission denied: % is required',v_required_permission;
    end if;

    if not exists (
        select 1 from public.product_variants v
        where v.id=p_variant_id and v.shop_id=p_shop_id and v.active=true and v.deleted_at is null
    ) then raise exception 'Invalid or inactive product variant'; end if;

    if p_quantity_base is null or p_quantity_base<=0 then raise exception 'Quantity must be greater than zero'; end if;
    if p_source not in ('MANUAL','VOICE','OCR','IMPORT') then raise exception 'Unsupported transaction source'; end if;

    select * into v_existing from public.stock_transactions where id=p_transaction_id;
    if found then
        if v_existing.shop_id<>p_shop_id or v_existing.variant_id<>p_variant_id then
            raise exception 'Transaction ID already belongs to a different operation';
        end if;
        return jsonb_build_object('status','already_applied','transaction_id',v_existing.id,'before_quantity_base',v_existing.before_quantity_base,'after_quantity_base',v_existing.after_quantity_base);
    end if;

    insert into public.inventory (variant_id,shop_id,quantity_base)
    values (p_variant_id,p_shop_id,0)
    on conflict (variant_id) do nothing;

    select i.quantity_base into v_before
    from public.inventory i
    where i.variant_id=p_variant_id and i.shop_id=p_shop_id
    for update;

    v_delta := case
        when p_transaction_type in ('PURCHASE','CUSTOMER_RETURN','ADJUSTMENT_IN','OPENING_STOCK','TRANSFER_IN') then p_quantity_base
        else -p_quantity_base
    end;

    v_after := v_before + v_delta;
    if v_after<0 then raise exception 'Insufficient stock. Available %, requested outbound %',v_before,p_quantity_base; end if;

    v_total := coalesce(p_total_amount,case when p_unit_price is not null then round((p_quantity_base*p_unit_price)::numeric,2) else null end);

    insert into public.stock_transactions (
        id,shop_id,variant_id,batch_id,transaction_type,quantity_base,entered_quantity,entered_unit,
        before_quantity_base,after_quantity_base,unit_price,total_amount,source,transcript,notes,device_id,occurred_at,created_by
    ) values (
        p_transaction_id,p_shop_id,p_variant_id,p_batch_id,p_transaction_type,p_quantity_base,p_entered_quantity,p_entered_unit,
        v_before,v_after,p_unit_price,v_total,p_source,p_transcript,p_notes,p_device_id,coalesce(p_occurred_at,now()),v_uid
    );

    update public.inventory set quantity_base=v_after,updated_at=now()
    where variant_id=p_variant_id and shop_id=p_shop_id;

    perform public.write_audit_event(
        p_shop_id,
        'inventory.'||lower(p_transaction_type),
        'stock_transaction',
        p_transaction_id::text,
        case when p_source='VOICE' then 'VOICE' when p_source='OCR' then 'OCR' when p_source='IMPORT' then 'IMPORT' else 'APP' end,
        jsonb_build_object('quantity_base',v_before),
        jsonb_build_object('quantity_base',v_after),
        jsonb_build_object('variant_id',p_variant_id,'transaction_type',p_transaction_type,'delta_base',v_delta,'required_permission',v_required_permission)
    );

    return jsonb_build_object('status','applied','transaction_id',p_transaction_id,'before_quantity_base',v_before,'after_quantity_base',v_after,'delta_base',v_delta,'total_amount',v_total);
exception
    when unique_violation then
        select * into v_existing from public.stock_transactions where id=p_transaction_id;
        if found then
            return jsonb_build_object('status','already_applied','transaction_id',v_existing.id,'before_quantity_base',v_existing.before_quantity_base,'after_quantity_base',v_existing.after_quantity_base);
        end if;
        raise;
end;
$$;
revoke all on function public.apply_inventory_transaction(uuid,uuid,uuid,text,numeric,numeric,text,numeric,numeric,text,text,text,text,timestamptz,uuid) from public;
grant execute on function public.apply_inventory_transaction(uuid,uuid,uuid,text,numeric,numeric,text,numeric,numeric,text,text,text,text,timestamptz,uuid) to authenticated;

create or replace function public.get_dashboard_summary(p_shop_id uuid)
returns jsonb
language plpgsql
stable
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
    if v_uid is null then raise exception 'Authentication required'; end if;
    if not public.has_shop_permission(p_shop_id,'report.read') then
        raise exception 'Permission denied: report.read is required';
    end if;

    select count(*) into v_total_variants from public.product_variants v
    where v.shop_id=p_shop_id and v.active=true and v.deleted_at is null;

    select count(*) into v_low_stock
    from public.product_variants v left join public.inventory i on i.variant_id=v.id
    where v.shop_id=p_shop_id and v.active=true and v.deleted_at is null
      and coalesce(i.quantity_base,0)>0 and coalesce(i.quantity_base,0)<=v.reorder_level;

    select count(*) into v_out_of_stock
    from public.product_variants v left join public.inventory i on i.variant_id=v.id
    where v.shop_id=p_shop_id and v.active=true and v.deleted_at is null and coalesce(i.quantity_base,0)<=0;

    select coalesce(sum(coalesce(i.quantity_base,0)*coalesce(v.purchase_price,0)),0)
    into v_stock_value
    from public.product_variants v left join public.inventory i on i.variant_id=v.id
    where v.shop_id=p_shop_id and v.active=true and v.deleted_at is null;

    select coalesce(sum(t.total_amount),0) into v_today_sales
    from public.stock_transactions t
    where t.shop_id=p_shop_id and t.transaction_type='SALE' and t.occurred_at>=date_trunc('day',now());

    return jsonb_build_object(
        'total_variants',v_total_variants,
        'low_stock',v_low_stock,
        'out_of_stock',v_out_of_stock,
        'inventory_value_estimate',v_stock_value,
        'today_sales',v_today_sales
    );
end;
$$;
revoke all on function public.get_dashboard_summary(uuid) from public;
grant execute on function public.get_dashboard_summary(uuid) to authenticated;

commit;
