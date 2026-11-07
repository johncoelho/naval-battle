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
--
-- A cada release (ver .claude/skills/play-store-release/SKILL.md):
--   insert into public.app_releases (version_name, version_code, notes_pt, notes_en, notes_es, android_live)
--   values ('0.18.0', 47, '...', '...', '...', true);
--   update public.app_releases set ios_live = true where version_code <= 47 and ios_live = false;  -- só com o lookup mostrando a versão

create table if not exists public.app_releases (
  version_name text primary key,
  version_code integer not null unique,
  notes_pt     text not null,
  notes_en     text not null default '',
  notes_es     text not null default '',
  android_live boolean not null default false,
  ios_live     boolean not null default false,
  published_at timestamptz not null default now()
);

alter table public.app_releases enable row level security;

drop policy if exists "novidades: ler" on public.app_releases;
create policy "novidades: ler"
  on public.app_releases for select
  to anon, authenticated
  using (true);
