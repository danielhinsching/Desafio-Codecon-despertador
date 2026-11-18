-- Cobranças Pix. Acesso só pela service role do servidor (RLS ligado, sem políticas).
create table public.payments (
  id uuid primary key default gen_random_uuid(),
  mp_payment_id text unique,
  alarm_id text not null,
  device_id text not null,
  amount_cents int not null,
  status text not null default 'pending'
    check (status in ('pending', 'approved', 'expired', 'used')),
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  used_at timestamptz
);

create index payments_alarm_id_idx on public.payments (alarm_id);

alter table public.payments enable row level security;
-- Nenhuma política para anon/authenticated: só a service role (que ignora RLS) acessa.
