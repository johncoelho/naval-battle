---
name: play-store-release
description: Full checklist and known pitfalls for cutting a new Android release of Naval Battle Classic (aigamesfactory.navalbattleclassic) and getting it live on Google Play Console closed testing. Use this whenever pushing a code/config change that needs to reach a real device via Play Console, or when debugging why a published build behaves differently than a local/debug build.
---

# Naval Battle Classic — Android release pipeline

This app publishes to Google Play Console as **"Naval Battle Classic"** under developer
**"AI Games Factory"** (`applicationId = aigamesfactory.navalbattleclassic`, internal Kotlin
`namespace = br.com.navalbattle`). Every step below was learned the hard way — skipping one
wastes a full CI cycle and/or a Play Console review cycle, so follow the checklist in order.

## 0. Before touching anything: is this a release-bound change?

If the change needs to be tested on a real device via Play Console (not just `assembleDebug`
sideloaded locally), it needs steps 1–5 below. If it's local-only iteration, skip this skill.

## 1. Bump the version in the SAME commit as the code change

In `composeApp/build.gradle.kts`:
```kotlin
versionCode = <previous + 1>   // MANDATORY, every single release, no exceptions
versionName = "<bump patch>"
```
**This is the #1 recurring mistake.** A pure bugfix commit with no other release-config
changes still needs this. Play Console gives NO build-time warning — it only rejects the
upload later with "version code has already been used", after a full CI build cycle has
already run. See [[feedback-android-release-checklist]].

**Also update the release notes in the SAME commit**, in
`composeApp/src/main/play/release-notes/pt-BR/default.txt` — the Gradle Play Publisher plugin
picks this file up automatically and ships it as the "What's new" text on every automatic
publish, so testers can see what changed without asking. Plain text, ~2-4 short lines,
user-facing language (what changed for them, not implementation detail), under 500 chars
(Play Store limit). This is mandatory per the user's standing instruction — a release with no
release notes update is an incomplete release, not just a style nit.

## 2. Signing config sanity (only if touching signing/build.gradle.kts)

- `signingConfigs.create("release")` needs `storeType = "PKCS12"` explicitly (AGP defaults to
  JKS; the repo's keystore is PKCS12 and reading it as JKS fails with a `BadPaddingException`
  that looks like a wrong password but isn't).
- **PKCS12 has only ONE real password.** If `ANDROID_KEY_PASSWORD` (keypass) secret differs
  from `ANDROID_KEYSTORE_PASSWORD` (storepass), `keytool` silently ignored that at creation
  time and the key is actually protected by storepass only. Both GitHub secrets must hold the
  **same value** for this keystore. If a signing `BadPaddingException` shows up again, verify
  this before anything else.

## 3. applicationId / targetSdk / compileSdk are pinned by prior Play Console decisions

- `applicationId` must stay `aigamesfactory.navalbattleclassic` — that's what's registered.
  Never let it drift back to the internal `br.com.navalbattle` namespace.
- `targetSdk`/`compileSdk` (`gradle/libs.versions.toml`) must meet whatever minimum Play
  Console currently enforces (was bumped 35→36 in 2026-09). If Play Console rejects an
  upload citing target SDK, bump both here AND add the matching
  `platforms;android-<N> build-tools;<N>.0.0` package to `.github/workflows/android.yml`'s
  `setup-android` step, or CI can't compile against the new SDK.

## 4. Push and let CI build AND publish automatically

- `git push origin main` works directly from this machine (see
  [[project-naval-battle-git-push]]) — no need for GitHub web-upload, including for
  `.github/workflows/*.yml` changes.
- **Fixed 2026-09-17**: pushes used to hang because `git-credential-manager` had two stored
  GitHub identities and popped a native "Select an account" dialog Claude can't see or click.
  Removed the extra identity and pinned the account (`git config
  credential.https://github.com.username johncoelho`) — push should now return instantly. If
  it ever hangs again, run `git-credential-manager github list` and check for a re-added
  second identity before assuming anything else.
- **As of 2026-09-16 the pipeline is end-to-end automatic**: CI builds the signed `.aab` AND
  publishes it straight to the Play Console closed-testing track (`alpha`) via the Play
  Developer API (Gradle Play Publisher plugin, `./gradlew :composeApp:publishBundle`), using
  the `PLAY_SERVICE_ACCOUNT_JSON` GitHub secret. **No browser, no manual download, no manual
  upload needed** — once versionCode is bumped (step 1) and the commit is pushed, the release
  is submitted automatically. The user only needs to update the app on their device and test.
  See "One-time setup" below if this secret is ever missing/rotated.
- CI still also uploads `naval-battle-release-aab` as a plain workflow artifact (backup /
  manual fallback) — only needed if the automatic publish step fails or the service account
  isn't configured.

### One-time setup (already done as of 2026-09-16 — reference only, e.g. if rotating the key)

This part cannot be automated by Claude — Google requires the account owner to grant this
access:
1. Google Cloud Console (project `aigamesfactory`) → IAM & Admin → Service Accounts → create
   one (e.g. `play-publisher-ci`), then create a JSON key for it.
2. Play Console → **Users and permissions → Invite new users** (the old dedicated "Setup →
   API access" page is gone as of 2026-09 — service accounts are now invited exactly like a
   human user, by email). Paste the service account's email
   (`<name>@<project>.iam.gserviceaccount.com`), go to **App permissions**, pick this app,
   and grant release-management permission (at minimum: manage testing track releases).
3. Add the JSON key's raw content as a GitHub repo secret named `PLAY_SERVICE_ACCOUNT_JSON`
   (Settings → Secrets and variables → Actions) — **the user does this themselves**, the raw
   key should never be pasted into chat/logs.
4. Track name: the plugin is configured with `track.set("alpha")`, matching this app's first
   (default) closed-testing track. If a track is renamed/added later, run
   `./gradlew :composeApp:tracks` (from the plugin) to list the real API track names before
   assuming "alpha" still applies.

## 5. Manual upload path (fallback only — normally not needed anymore)

Only do this if the automatic publish step in CI fails or the service account isn't set up yet:
- Path: Play Console → app → **Test and release → Testing → Closed testing → Manage track →
  Create new release**.
- The `.aab` is inside the downloaded `naval-battle-release-aab.zip` artifact — extract first.
- **Only one draft release per track at a time.** If "Create new release" is greyed out, a
  draft already exists — use **"Edit release"** to resume that same draft instead; the draft
  is stored server-side on the account, so it's visible from any logged-in session/browser,
  not just the one that created it.
- **Browser choice matters here**: this flow depends on the user (they need to be logged in,
  select a local file). Use the user's real Chrome (`claude-in-chrome`), never Claude's
  isolated built-in browser pane — the built-in pane has its own separate login session the
  user can't see, and driving it while narrating steps to the user as if it's their screen
  causes confusion. See [[feedback-browser-choice]].
- An update to an app already approved in closed testing typically reviews much faster than
  the very first submission — don't assume it needs a full multi-day review cycle before
  testers can pick it up.

## 6. Google Sign-In / OAuth — only relevant if signing cert or package changed

Play App Signing means Google **re-signs** the app before distribution. The SHA-1 that
matters for OAuth is the **App Signing key certificate**, never the local upload keystore's
SHA-1. Get it from: Play Console → **Protected with Play → Play Store protection → Manage
Play app signing → SHA-1 certificate fingerprint**.

**CRITICAL — register EVERY app signing key, not just the current one.** The Play Console
App signing page has a **"Previous app signing keys"** section (easy to miss — it sits between
the current key and the upload key certificate, and its fingerprints are hidden behind a ⋮
menu → "Copy SHA-1 certificate fingerprint"). If the app signing key was ever upgraded, the
APK actually delivered to a device may be signed with the *previous* key. Registering only
the current key's SHA-1 makes Google reject token minting with
`status=UNREGISTERED_ON_API_CONSOLE`, which the Credential Manager surfaces to the app as
error `[16] Account reauth failed` → delivered to Kotlin as
`GetCredentialCancellationException` → the app treats it as "user cancelled" and shows
**nothing at all**. This cost hours on 2026-09-16. Create one Android OAuth client per
signing certificate (current + every previous one), all with the same package name.

Also note the current key section shows both a **"Classical key"** and a **"Post-quantum
cryptography key"** SHA-1 — OAuth wants the *Classical* one.

If package name or signing cert ever changes:
1. Register a new **Android**-type OAuth client in Google Cloud Console (Google Auth
   Platform → Clients → Create client) for the new (package, SHA-1) pair.
2. Add that client's ID to Supabase → Authentication → Providers → Google → **Client IDs**
   (comma-separated allow-list of every valid token `aud`). Must also keep the Web Client ID
   used as `serverClientId` in the Android app.
3. **Supabase's dashboard input is a controlled React field** — setting `.value` directly (or
   via a generic form-fill tool) can look like it saved but silently not persist. Verify with
   a fresh full page reload before trusting it. If it doesn't stick, use the native-setter +
   dispatched `input`/`change` events technique, then reload a THIRD time to confirm.
4. Google's own UI warns new OAuth clients can take "5 minutes to a few hours" to propagate —
   don't assume a fix is broken if tested immediately after registration.

## 7. Debugging a published build that misbehaves

**Focus on what changed since it last worked, not on tangential setups.** If a feature (e.g.
login) worked before publishing and broke after, the fastest path is real evidence, not
re-guessing config:

- Rule out server-side first if applicable (e.g. check Supabase Auth Logs for whether any
  request even reached the server — zero requests means the failure is 100% client-side,
  before any network call, which redirects the whole investigation).
- Get a real device trace via wireless `adb`:
  ```bash
  adb pair <ip>:<pairing-port> <6-digit-code>   # one-time trust, Android 11+
  adb connect <ip>:<debug-port>                  # separate port from pairing
  adb -s <ip>:<port> logcat -c
  adb -s <ip>:<port> logcat -v time > logcat.txt &
  ```
  Then have the user reproduce the bug, and grep the capture for the package name,
  `CredentialManager`, `Auth.Api.Credentials`, `AndroidRuntime`, `FATAL EXCEPTION`.
- **Grep the logcat by timestamp window, not just by keyword.** The decisive line in the
  2026-09-16 Google Sign-In bug (`This android application is not registered to use OAuth2.0`)
  came from tag `Auth`, not from any tag containing "Google" or "Credential" — it was only
  found by dumping every line in the 1.5s window right after the account picker closed:
  `awk '/^09-16 21:05:0[1-4]\./' logcat.txt | grep -iE "auth|credential|error|fail"`.
- **A Kotlin `catch` block can swallow a real error into a silent no-op.** E.g.
  `GetCredentialException.message` can be an empty string (`""`), not `null` — the `?:` elvis
  operator does NOT catch that, and downstream code checking `message.isBlank()` to detect
  "user cancelled" will misinterpret a real failure as a cancel, with nothing printed anywhere
  in logcat because the exception is caught inside the app's own code. If OS-level logs (e.g.
  `CredentialManager`/`Auth.Api.Credentials`) show success/normal flow but the app visibly does
  nothing and no server request fires, suspect exactly this pattern before anything else.

## 8. Docs

Every build gets its README/CHANGELOG/`docs/` updated — see
[[project-naval-battle-docs]].
