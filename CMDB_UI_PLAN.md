# CMDB UI in Release Dashboard

Plan for adding a configuration-item (CI) catalog to release-dash once the MENTAT CMDB Git repo is moved and populated.

**While that repo is still in flux:** do not call GitHub, do not add an ExternalSecret for a token that does not exist yet, and do not hard-code owner/repo. Feature-flagged scaffolding in this repo is OK — see [Safe prep while the CMDB repo is in flux](#safe-prep-while-the-cmdb-repo-is-in-flux).

Related current-state notes (as of the original private repo): Git is the database; one YAML file per CI; `cmdb/cis.yaml` is a generated index rebuilt by `scripts/sync_cis_yaml.py` on push.

## Goal

Operators can **view** the CMDB catalog and **create/edit** CIs from release-dash, without editing Git by hand.

Git remains the source of truth. The UI is a schema-aware Git client.

## Non-goals

- No webhook / push-to-dashboard “live” updates. After a Git change, the UI picks it up on the next fetch (page load, tab focus, or a modest React Query refetch interval).
- Do not edit `cmdb/cis.yaml` from the UI. That file stays generated.
- Do not store CIs in JFrog, a database, or in-memory as the source of truth.
- No GitHub PR review UI inside release-dash (reviews stay in GitHub). Creating a PR from the app is in scope for writes.

## Assumptions about the new CMDB repo

Fill these in after the move. The implementation should treat them as config, not hard-coded org/repo names.

| Placeholder | Value (fill in later) |
|---|---|
| Owner/repo | `slingshot-aerospace-inc/mentatcmdb` |
| Default branch | `main` |
| Index path | `cmdb/cis.yaml` |
| Schema path | `schema/ci-record.schema.json` |
| Per-CI folders | `systems/`, `environments/`, `docs/`, `releases/`, `demos/` |
| Sync Action | Rebuilds `cmdb/cis.yaml` from per-CI YAML on push to those folders |

If the new repo changes folder names or schema, update this table and the config keys below before coding.

Expected per-CI record (JSON Schema `draft-07`):

- Required: `name`, `category`, `ci_number`, `parent`, `status`
- `category`: `SYS` \| `ENV` \| `REL` \| `DOC` \| `DMO`
- `ci_number` pattern: `^MENTAT-(SYS\|ENV\|REL\|DOC\|DMO)-[0-9]{2}(\.[A-Z])?(-[A-Z]+-Rev[0-9]{3})?$`
- `status`: `active` \| `in works` \| `retired` \| `pre-baseline` \| `awaiting approval`
- `DOC` also requires `doc_type` (`PLAN` \| `PROCEDURE` \| `REPORT` \| `MATRIX`) and `revision` (integer ≥ 0)
- Optional: `notes`
- `additionalProperties: false`

Folder mapping for writes:

| category | directory | example path |
|---|---|---|
| SYS | `systems/` | `systems/MENTAT-SYS-02.yaml` |
| ENV | `environments/` | `environments/MENTAT-ENV-01.yaml` |
| DOC | `docs/` | `docs/MENTAT-DOC-01-PLAN-Rev000.yaml` |
| REL | `releases/` | `releases/MENTAT-REL-01.yaml` |
| DMO | `demos/` | `demos/MENTAT-DMO-01.yaml` |

Filename should match `ci_number` (keep the existing naming convention of the populated repo).

## Architecture

```
Browser (React)
    |  GET/POST /api/cmdb/*
    v
release-dash backend (Spring Boot)
    |  GitHub Contents / Pulls / Git Data API
    v
CMDB Git repo
    |  existing sync Action on push
    v
cmdb/cis.yaml (generated index)
```

- **Read path:** backend fetches `cmdb/cis.yaml` (and optionally the schema) from GitHub, parses YAML, returns JSON.
- **Write path:** backend validates the payload against the schema, then creates or updates **one** per-CI YAML file via GitHub. Default write mode: **open a pull request**. Direct commit to the default branch is a config fallback for early bring-up only.
- **Cache:** short TTL in the backend (suggest 30–60s) so list views are cheap. No webhook invalidation. “Refresh” in the UI bypasses cache.

Auth to GitHub: a **fine-grained personal access token** granted **only** to the CMDB repository. No GitHub App. No classic PAT. The token is stored in AWS Secrets Manager and injected at deploy time via External Secrets — same pattern as `/release-dash/artifact-repository/token`. The backend must only call the configured `owner/repo`; never take a repo name from the browser.

### Who does what (order)

`dev1` / `tvsim_ViewOnly` cannot create Secrets Manager secrets. Do not put the PAT in Helm, git, or a Kubernetes manifest.

| Step | Who | Where | What |
|---|---|---|---|
| **1. GitHub PAT** | You | GitHub fine-grained token UI | Token limited to `slingshot-aerospace-inc/mentatcmdb`. Contents R/W + Pull requests R/W. **Done.** |
| **2. AWS secret** | **DevOps** (Secrets Manager write) | Account `604741711296`, region `us-west-2` | Create secret **name** `/release-dash/cmdb-github/token` with JSON `{"token":"<PAT>"}`. This is **not** a Kubernetes ExternalSecret. Share the PAT via an approved secret channel, not Slack/Jira. |
| **3. IAM read access** | Us → `platform-infra` PR | `release_dash_irsa_iam.json` (staging + preview) | Allow `GetSecretValue` on `/release-dash/cmdb-github/token*`. Without this, External Secrets cannot read the secret even after step 2. |
| **4. Helm ExternalSecret** | Us → `release-dash` | `values.staging.yaml` / `values.preview.yaml` | Map AWS secret to env `CMDB_GITHUB_TOKEN`. **Done.** |
| **5. Enable CMDB** | Us → `release-dash` | Helm `CMDB_ENABLED` | Preview and staging are `true`. |

Do **not** skip to step 4 before 2. JFrog already had an AWS secret; Helm only syncs it.

#### DevOps request (step 2) — copy/paste

Please create an AWS Secrets Manager secret (commercial staging; preview uses the same secret):

- **Account:** `604741711296`
- **Region:** `us-west-2`
- **Secret name:** `/release-dash/cmdb-github/token`
- **Type:** Other (plaintext JSON)
- **Secret string:** `{"token":"<fine-grained GitHub PAT>"}`  
  Key **must** be `token` (same convention as JFrog’s JSON keys).
- **Purpose:** release-dash will sync this via External Secrets to `CMDB_GITHUB_TOKEN`, to read/write `slingshot-aerospace-inc/mentatcmdb` only.
- **Rotation:** ~90 days; we will send a replacement PAT.

I will send the PAT value out of band (not in this ticket).

#### platform-infra (step 3)

Already edited (needs a `platform-infra` PR / apply):

- `terraform/environments/staging/teams/platform/release_dash_irsa_iam.json`
- `terraform/environments/preview/teams/platform/release_dash_irsa_iam.json`

Add ARN: `arn:aws:secretsmanager:us-west-2:604741711296:secret:/release-dash/cmdb-github/token*`

#### release-dash Helm (step 4) — do not merge until the AWS secret exists

```yaml
      cmdb-github:
        secretName: /release-dash/cmdb-github/token
        keyMappings:
          CMDB_GITHUB_TOKEN: token
```

### GitHub token (locked decision)

GitHub does not mint a token as a side effect of `gh repo create`. The repo has to exist first so the fine-grained token can be limited to it. Do this once when the new CMDB repo is created, then Helm takes over.

**Bootstrap (once, when the repo is created):**

1. Create the CMDB Git repo and populate it.
2. Immediately create a fine-grained PAT whose **only** selected repository is that repo.
3. Write the token into the AWS secret store (JSON, same shape as JFrog’s `username`/`token` blob, but only `token` is required):

   - Path: `/release-dash/cmdb-github/token`
   - Value: `{ "token": "github_pat_..." }`

4. Deploy (or wait for External Secrets to sync). The pod gets `CMDB_GITHUB_TOKEN` from that secret. No token in Git, Helm values, or CI logs.

| Setting | Value |
|---|---|
| Token type | Fine-grained PAT (user or machine account) |
| Resource owner | Same org/user that owns the CMDB repo |
| Repository access | **Only** `slingshot-aerospace-inc/mentatcmdb` |
| Expiration | Set an expiry and calendar a rotation (90 days suggested) |
| AWS secret | `/release-dash/cmdb-github/token` (JSON key `token`) |

Repository permissions (nothing else):

| Permission | Access | Why |
|---|---|---|
| Metadata | Read (always included) | Required by GitHub |
| Contents | Read and write | Fetch YAML; create/update files and branches |
| Pull requests | Read and write | Open PRs when `CMDB_WRITE_MODE=pr` |

Do not grant Administration, Workflows, Secrets, or access to any other repository. If the token can list or write another repo, it is too broad — recreate it.

**Helm (same `externalSecrets.secretsJson` mechanism as JFrog).** Non-secret CMDB settings stay in `env`; only the token comes from External Secrets. Add to `values.yaml`, `values.staging.yaml`, and `values.preview.yaml`:

```yaml
  env:
    CMDB_ENABLED:
      value: "true"   # false until the repo and secret exist
    CMDB_GITHUB_OWNER:
      value: slingshot-aerospace-inc
    CMDB_GITHUB_REPO:
      value: mentatcmdb
    CMDB_GITHUB_BRANCH:
      value: main
    CMDB_WRITE_MODE:
      value: pr

  externalSecrets:
    imagePullSecrets:
      - jfrog-docker-imagepull   # staging/preview only; keep existing
    secretsJson:
      jfrog:
        secretName: /release-dash/artifact-repository/token
        keyMappings:
          JFROG_USERNAME: username
          JFROG_TOKEN: token
      cmdb-github:
        secretName: /release-dash/cmdb-github/token
        keyMappings:
          CMDB_GITHUB_TOKEN: token
```

Preview and staging can share the same AWS secret (one token, one repo). If an environment should not talk to the CMDB yet, set `CMDB_ENABLED: "false"` there and omit or ignore the mapping.

The GitHub client should send `Authorization: Bearer ${CMDB_GITHUB_TOKEN}` and `Accept: application/vnd.github+json`. On 401/403, fail clearly (“CMDB GitHub token rejected or missing Contents scope”) rather than treating it as an empty catalog.

Rotation: mint a new fine-grained token still limited to that repo, update the AWS secret’s `token` field, let External Secrets sync. No app redeploy required unless the operator does not reload env from a mutated Secret (if the chart only injects at pod start, bounce the deployment after rotation).

## Configuration

Add to `backend/src/main/resources/application.properties` and Helm env/secrets:

```
cmdb.enabled=${CMDB_ENABLED:false}
cmdb.github.owner=${CMDB_GITHUB_OWNER:}
cmdb.github.repo=${CMDB_GITHUB_REPO:}
cmdb.github.branch=${CMDB_GITHUB_BRANCH:main}
cmdb.github.token=${CMDB_GITHUB_TOKEN:}
cmdb.index.path=${CMDB_INDEX_PATH:cmdb/cis.yaml}
cmdb.schema.path=${CMDB_SCHEMA_PATH:schema/ci-record.schema.json}
cmdb.write.mode=${CMDB_WRITE_MODE:pr}
cmdb.cache.ttl-seconds=${CMDB_CACHE_TTL_SECONDS:45}
```

- Keep `CMDB_ENABLED=false` until the new repo **and** `/release-dash/cmdb-github/token` exist so current deploys are unchanged.
- `CMDB_WRITE_MODE`: `pr` (default) or `commit`.
- `CMDB_GITHUB_TOKEN` is **only** supplied via External Secrets in cluster (see Helm snippet above). Do not put it in Helm `env.value`, Git, or workflow files. Do not reuse the JFrog token.
- Local dev: `export CMDB_GITHUB_TOKEN=...` from a fine-grained token still limited to the CMDB repo. Document next to the existing JFrog block in README.

## Backend

Suggested package: `com.releasedash.cmdb` (controller, service, GitHub client, DTOs). Reuse the existing `WebClient.Builder` bean.

### GitHub client

Use Contents API (and Pulls API when `write.mode=pr`):

- `GET /repos/{owner}/{repo}/contents/{path}?ref={branch}` — read file + `sha`
- `PUT /repos/{owner}/{repo}/contents/{path}` — create/update file (direct commit, or commit on a feature branch)
- For PRs: create branch from default SHA → PUT file on that branch → `POST /repos/{owner}/{repo}/pulls`

Parse YAML with SnakeYAML (add dependency if not already present). Validate writes with the fetched JSON Schema (networknt or similar).

On GitHub 409 / SHA mismatch, return 409 to the UI (“CI was updated in Git; reload and retry”).

### API

All under `/api/cmdb`. If `cmdb.enabled=false`, return 503 with a short message.

| Method | Path | Behavior |
|---|---|---|
| GET | `/api/cmdb/items` | Parse `cmdb/cis.yaml`, return `{ items, fetched_at, sha, html_url }`. Query: `category`, `status`. |
| GET | `/api/cmdb/items/{ciNumber}` | One item from the index, plus Git path and `html_url` for the per-CI file. |
| GET | `/api/cmdb/schema` | Proxy/cache `schema/ci-record.schema.json` for the form. |
| GET | `/api/cmdb/next-number?category=` | Suggest next `MENTAT-{CAT}-NN` from existing IDs (suffix letters / DOC rev still user-editable). |
| POST | `/api/cmdb/items` | Create. Body = CI record. Writes per-CI YAML. Does not write `cis.yaml`. |
| PUT | `/api/cmdb/items/{ciNumber}` | Update. Require `base_sha` (blob SHA of the file being edited). |
| POST | `/api/cmdb/refresh` | Bypass cache and re-fetch the index. |

Create/update response should include: committed path, commit SHA or PR URL/number, and whether the index is stale until the CMDB repo’s sync Action runs.

Do not add a DELETE that removes Git history. Retire a CI by setting `status: retired`.

### Write payload → Git file

1. Validate JSON against schema (including DOC-only fields).
2. Resolve path from `category` + `ci_number`.
3. Reject create if that path already exists.
4. Dump YAML with stable key order: `name`, `category`, `ci_number`, `parent`, `status`, then optional fields (`doc_type`, `revision`, `notes`).
5. Commit message examples:
   - `chore(cmdb): add MENTAT-SYS-03`
   - `chore(cmdb): update MENTAT-ENV-02 status`
6. PR title/body should name the CI and status. Branch: `cmdb/{ci_number}-{short-slug}` (sanitize).

After merge, the existing CMDB sync Action rebuilds `cmdb/cis.yaml`. The list view may lag until that Action finishes **and** cache TTL expires (or the user hits Refresh). Document that lag; do not add a webhook to close it.

## Frontend

Match existing layout/nav (`Layout.tsx`, React Query, `frontend/src/services/api.ts`).

### Routes

| Path | Page |
|---|---|
| `/cmdb` | Catalog list (read) |
| `/cmdb/new` | Create form |
| `/cmdb/:ciNumber` | Detail + edit form |

Add a **CMDB** nav link. If `GET /api/cmdb/items` returns 503, show “CMDB not configured” instead of hiding the nav (easier to debug in preview).

### List (`/cmdb`)

- Table: `ci_number`, name, category, status, parent, notes
- Filters: category, status; text search on name / number
- Optional grouping by category
- Link to GitHub file; “Refresh” calls `POST /api/cmdb/refresh` then invalidates the query
- React Query: `staleTime` ~ 30s, `refetchOnWindowFocus: true`. No sub-10s polling.
- Banner if the user just opened a PR: “Index updates after the PR is merged and the CMDB sync Action runs.”

### Detail / edit

- Form fields driven by schema (show `doc_type` / `revision` only when category is `DOC`)
- Parent: dropdown of existing `ci_number`s plus empty/null
- Status: schema enum
- On save: PUT with `base_sha`; on 409, reload and show a conflict message
- After create/update in `pr` mode, show the PR URL

### Create

- Category first, then call `next-number` to prefill `ci_number` (user can override)
- Same validation as backend (fail closed)

Reuse existing visual language (header, tables, filters) rather than a new design system.

## Safe prep while the CMDB repo is in flux

The CMDB **shape** is stable enough to code against (YAML index, per-CI files, JSON schema, GitHub Contents API). The **location, token, and possibly schema details** are not. Land work that compiles and deploys with CMDB **off**, and that does not fail if AWS/GitHub are missing.

### Do now (no GitHub, no AWS secret)

These keep existing staging/preview behavior identical (`CMDB_ENABLED` defaults false).

| Work | Where | Why it is safe |
|---|---|---|
| Config keys, default off | `application.properties` | Empty owner/repo/token; `cmdb.enabled=false` |
| Helm **non-secret** env only | `values.yaml`, `values.staging.yaml`, `values.preview.yaml` | `CMDB_ENABLED: "false"`. Optional empty `CMDB_GITHUB_OWNER` / `REPO` / `BRANCH`. **Do not** add `externalSecrets.secretsJson.cmdb-github` until `/release-dash/cmdb-github/token` exists — a missing AWS secret can break the deploy |
| Disabled API | `CmdbController` | `GET /api/cmdb/items` (and other `/api/cmdb/*`) return **503** with `{ "error": "CMDB is not enabled" }` when disabled. No outbound HTTP |
| DTOs + path mapping | `com.releasedash.cmdb` | `CmdbItem`, category → folder map, `ci_number` helpers. Unit-test with a **fixture** `cis.yaml` / schema copied from current MENTAT docs, labeled as a snapshot that may need a refresh after the move |
| SnakeYAML (+ optional JSON Schema validator) | `backend/pom.xml` | Needed later; unused while disabled |
| Stub UI | Nav **CMDB** → `/cmdb` | Page explains CMDB is not configured yet. No create/edit forms yet (those assume a stable schema) |
| README | Env var table | Document `CMDB_*` and that cluster enablement waits on the repo + secret |

GitHub client can exist as a class that is **not called** unless enabled, or behind an interface with a no-op implementation. Prefer not wiring `WebClient` calls in the disabled path at all.

### Do not do yet

- `CMDB_ENABLED=true` in any Helm values
- Live GitHub fetches until the GitHub client (Phase 1) lands
- Live GitHub fetches, PRs, or caching against `msipalay/mentat-cmdb` or any guessed new URL
- Copying the live CMDB as “source of truth” into this repo (fixtures for tests only)
- Create/edit forms and write API (schema/`ci_number` pattern may still change)
- Filling the assumptions table with a guessed owner/repo

### After the repo lands (unchanged)

Phase 0 (token + AWS secret) → add ExternalSecret mapping → Phase 1 read path → set `CMDB_ENABLED=true` → Phase 2 writes.

If the new schema differs from the fixture, update DTOs/tests then; the flag and 503 stub do not need to change.

## Phased implementation (once the repo is populated)

Do this in order. Each phase should be shippable.

### Phase 0 — Repo + secret (no app code required)

- Create/populate the CMDB repo
- Mint the fine-grained token (Contents R/W, Pull requests R/W, **that repo only**)
- Put `{ "token": "..." }` at `/release-dash/cmdb-github/token` in AWS
- Confirm External Secrets can sync it in staging (and preview if desired)
- Confirm `GET` `cmdb/cis.yaml` with that token works, and the same token **cannot** read a different repo
- Fill Helm `CMDB_GITHUB_OWNER` / `CMDB_GITHUB_REPO`; leave `CMDB_ENABLED=false` until Phase 1 is deployed

### Phase 1 — Read catalog

- GitHub client + cache
- `GET /api/cmdb/items`, `GET /api/cmdb/items/{ciNumber}`, `POST /api/cmdb/refresh`
- `/cmdb` list page + nav
- Backend tests with a fixture `cis.yaml` (no live GitHub in unit tests)

### Phase 2 — Schema-backed create/edit

- `GET /api/cmdb/schema`, `GET /api/cmdb/next-number`
- POST/PUT writing **one** per-CI file
- Default `CMDB_WRITE_MODE=pr`
- Create/detail pages
- Tests: schema accept/reject, path mapping, SHA conflict → 409

### Phase 3 — Enable in cluster

- Helm: non-secret `CMDB_*` env + `externalSecrets.secretsJson.cmdb-github` as above (staging and preview)
- Set `CMDB_ENABLED=true` only after the AWS secret exists
- README: bootstrap steps, env vars, index lag after merge, token rotation
- Manual check: list matches GitHub; create a CI via UI → PR → merge → Refresh shows the new row

## Testing

- Unit: YAML parse of a checked-in fixture copied from the new repo’s `cmdb/cis.yaml`
- Unit: schema validation (valid SYS, valid DOC, invalid extra field, bad `ci_number`)
- Unit: category → path mapping
- Integration (optional, gated): GitHub API against a throwaway branch, or WireMock
- UI: list filters; create form hides DOC fields for SYS; 409 message

Do not call the real CMDB repo from CI unless a dedicated test token and fork are set up.

## Security

- Fine-grained GitHub token, **exclusive to the CMDB repo**, only on the backend; never send it to the browser
- Browser only talks to `/api/cmdb/*`; owner/repo come from server config, not the request
- Same origin as today (frontend served from Spring static). CORS unchanged for production
- Rotate the token before it expires; treat a leaked token as CMDB-repo-only blast radius (still enough to open PRs / commit YAML)
- Release-dash auth is currently open at the app layer (ingress/network). Writes will use this **shared** token, so anyone who can load the UI can act as that token on the CMDB repo. Before enabling writes in shared environments, either:
  - restrict the UI (SSO / ingress auth), or
  - keep `CMDB_WRITE_MODE=pr` and require GitHub reviewers on the CMDB repo, or
  - add a simple app-level gate later (out of scope for v1 unless required)

## Implementation checklist (code touchpoints)

Prep scaffolding (CMDB off, no GitHub, no ExternalSecret):

- [x] `backend/.../application.properties` — `cmdb.*` keys, default `enabled=false`
- [x] `com.releasedash.cmdb` — properties, DTOs, path mapping, index parser, number helper, 503/501 controller
- [x] Helm non-secret `CMDB_ENABLED=false` plus `CMDB_GITHUB_OWNER` / `CMDB_GITHUB_REPO` for `slingshot-aerospace-inc/mentatcmdb`
- [x] Helm `externalSecrets.secretsJson.cmdb-github` → `CMDB_GITHUB_TOKEN` (keep `CMDB_ENABLED=false` until the GitHub client lands)
- [x] `frontend/src/services/api.ts` — CMDB types and `getCmdbItems`
- [x] `Cmdb.tsx` stub + `App.tsx` / `Layout.tsx` nav
- [x] Schema-driven `CmdbForm.tsx`, `/cmdb/new`, `/cmdb/:ciNumber` (client validation; submit still 503/501)
- [x] `GET /api/cmdb/schema` serves classpath snapshot (no GitHub)
- [x] README env vars (cluster enablement still waits on repo + secret)

- [x] GitHub client + cache (Phase 1)
- [x] `CMDB_ENABLED=true` in preview and staging Helm
- [x] Create form opens a GitHub PR (`CMDB_WRITE_MODE=pr`); edit remains read-only

## Open items (resolve after the repo move)

1. Exact `owner/repo` and default branch (also Helm `CMDB_GITHUB_OWNER` / `CMDB_GITHUB_REPO`) — **done:** `slingshot-aerospace-inc/mentatcmdb` on `main`
2. Which identity mints the fine-grained token (machine user vs a person). Staging and preview share `/release-dash/cmdb-github/token` unless we later split secrets
3. Confirm folder names, schema, and `ci_number` pattern did not change in the move
4. Whether preview/staging should enable writes or read-only (`CMDB_WRITE_MODE` / a future `cmdb.read-only`)
5. Who must approve CMDB PRs (CODEOWNERS on the new repo)
6. Whether `parent` for TALOS-style children should be auto-set (e.g. `MENTAT-SYS-01.A` → `MENTAT-SYS-01`) — nice-to-have, not required for v1
7. Token expiry / rotation owner (suggested 90 days): new PAT → update AWS secret → External Secrets sync → bounce pods if env is only loaded at start
