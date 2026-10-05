-- Novidades de cada versão publicada — alimentam a janela "Versão nova disponível"
-- do app (ui/UpdatePopup.kt), que lista o que mudou desde a versão instalada.
-- Leitura pública (convidado também vê); escrita só pelo painel/SQL, a cada release.
--
-- android_live: liga no push da versão — o app ainda confere com a Play (In-App
-- Update API) se a atualização já chegou para aquele aparelho antes de avisar, então
-- a janela não aparece enquanto o Google revisa. ios_live: liga quando o .ipa da
-- versão já está na release "latest" (CI do iOS verde) — o botão leva ao site.
--
-- A cada release (ver .claude/skills/play-store-release/SKILL.md):
--   insert into public.app_releases (version_name, version_code, notes_pt, notes_en, notes_es, android_live)
--   values ('0.18.0', 47, '...', '...', '...', true);
--   update public.app_releases set ios_live = true where version_name = '0.18.0';  -- após o CI do iOS

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
