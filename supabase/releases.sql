-- Novidades de cada versão publicada — alimentam a janela "Versão nova disponível"
-- do app (ui/UpdatePopup.kt), que lista o que mudou desde a versão instalada.
-- Leitura pública (convidado também vê); escrita só pelo painel/SQL, a cada release.
--
-- android_live: liga no push da versão — o app ainda confere com a Play (In-App
-- Update API) se a atualização já chegou para aquele aparelho antes de avisar, então
-- a janela não aparece enquanto o Google revisa. ios_live: liga só quando a App Store
-- já oferece aquela versão (ou uma mais nova), conferido pela leitura pública
--   curl -s "https://itunes.apple.com/lookup?id=6827258132&country=br" | grep -o '"version":"[^"]*"'
-- Upload ao TestFlight não conta (fica false); o botão do app leva à página do jogo na App Store.
-- android_prod (#178): true só quando a versão chega a 100% na produção da Play (o Release marca
-- depois do `advance` fraction=1, só aquela versão: where version_code = N, nunca <=). Nenhum cliente
-- lê a coluna; o gatilho push_on_release não olha para ela.
--
-- A cada release (ver .claude/skills/play-store-release/SKILL.md):
--   insert into public.app_releases (version_name, version_code, notes_pt, notes_en, notes_es, android_live)
--   values ('0.18.0', 47, '...', '...', '...', true);
--   update public.app_releases set ios_live = true where version_code <= 47 and ios_live = false;  -- só com o lookup mostrando a versão
--   update public.app_releases set android_prod = true where version_code = 47;  -- só quando a produção chegou a 100%

create table if not exists public.app_releases (
  version_name text primary key,
  version_code integer not null unique,
  notes_pt     text not null,
  notes_en     text not null default '',
  notes_es     text not null default '',
  android_live boolean not null default false,
  ios_live     boolean not null default false,
  android_prod boolean not null default false,
  published_at timestamptz not null default now()
);

alter table public.app_releases enable row level security;

drop policy if exists "novidades: ler" on public.app_releases;
create policy "novidades: ler"
  on public.app_releases for select
  to anon, authenticated
  using (true);

-- #178: versão em 100% na produção da Play (migração app_releases_android_prod).
alter table public.app_releases add column if not exists android_prod boolean not null default false;
-- backfill: a 128 foi a última a chegar a 100% antes desta coluna (idempotente)
update public.app_releases set android_prod = true where version_code = 128;
