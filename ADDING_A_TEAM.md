# Adding a team site

This app already serves two team sites:

- **Mentat** is the real release dashboard, at `/mentat` and `/mentat/api`. Its pages and controller are specific to Mentat.
- **Foobar** is the template for another team, at `/foobar` and `/foobar/api`. It has the same kind of pages and API paths as Mentat, with its own layout and its own controller.

To add a team, copy Foobar and register the new name in the same places Foobar is already registered. Copying a folder of React pages is not enough on its own. The new site needs routes, an API, a Spring controller, and a load-balancer path.

## The examples use a placeholder named Baz

**Baz is not a team in this repo.** It is a stand-in, in the same way "foobar" is a stand-in. This guide uses it so the examples have a concrete name:

| Placeholder | Where it shows up | Example |
| --- | --- | --- |
| `baz` | URL prefix and config keys | `/baz`, `/baz/api` |
| `Baz` | React components and Java classes | `BazLayout`, `BazController` |
| `BAZ` | constants | `TeamApis.BAZ`, `BAZ_API_BASE` |

Replace every `baz` / `Baz` / `BAZ` with the real team's short name. Use the same lowercase string everywhere: the URL, the React routes, the Java constant, and the config keys. Keep a separate controller and separate page components so that team can change its own UI later.

A few prefixes are already used by the app, so a team name cannot be one of these: `assets`, `webhook`, `healthcheck`, `readyz`, `livez`, `spa`.

Copy `frontend/src/teams/foobar/`, not Mentat's dashboard, release list, live deployments, or CMDB pages. Those Mentat pages call `/mentat/api` and link to `/mentat/...`. Mentat's Memory page reads `/debug/memory`, which is not a team API. Foobar's Memory page is a static placeholder, and the copy should start from that.

## 1. Frontend pages

Copy `frontend/src/teams/foobar/` to `frontend/src/teams/baz/` and rename the components (`BazLayout`, `BazHome`, and so on).

`BazLayout` only passes a title and links into the shared `TeamLayout`. `TeamLayout` draws the header, nav, main area, and footer. For the placeholder team, that looks like this:

```tsx
<TeamLayout
  title="Baz Team Release Dashboard"
  homeTo="/baz"
  links={[
    { to: '/baz/releases', label: 'All Releases' },
    { to: '/baz/live', label: 'Live Deployments' },
    { to: '/baz/cmdb', label: 'CMDB' },
    { to: '/baz/memory', label: 'Memory' },
  ]}
/>
```

Change the title and the link list. Leave `TeamLayout` itself alone.

Mount the routes in `frontend/src/App.tsx`, next to the existing `/foobar` route:

```tsx
<Route path="/baz" element={<BazLayout />}>
  <Route index element={<BazHome />} />
  <Route path="releases" element={<BazReleases />} />
  <Route path="live" element={<BazLive />} />
  <Route path="cmdb" element={<BazCmdb />} />
  <Route path="memory" element={<BazMemory />} />
</Route>
```

Add a card for the team to the `teams` array in `frontend/src/Home.tsx`. That list is written but not shown right now. Visiting `/` redirects to `/mentat` until that temporary redirect is removed.

**Links.** Paths in the copied files are absolute (`/foobar`, `/foobar/releases`, `/foobar/cmdb`). Search the new folder for `foobar` and replace it with `baz`. A leftover `/foobar` link sends the new team's nav to Foobar.

**React Query keys.** Foobar's pages use keys like `['foobar', 'stats']` and `['foobar', 'releases']`. Change the first element to `'baz'`. If it stays `'foobar'`, both teams share one cache.

## 2. Frontend API

`frontend/src/services/api.ts` already has helpers that take a base path (`fetchReleases`, `fetchStats`, and so on). Add a base for the placeholder team and one-line wrappers around those helpers:

```ts
export const BAZ_API_BASE = '/baz/api'

export const getBazReleases = (
  repository?: string,
  environment?: string,
  limit = 50,
  offset = 0
) => fetchReleases(BAZ_API_BASE, repository, environment, limit, offset)

export const getBazStats = () => fetchStats(BAZ_API_BASE)
export const getBazLiveServicesCatalog = () => fetchLiveServicesCatalog(BAZ_API_BASE)
```

`getBazCmdbItems` should `GET ${BAZ_API_BASE}/cmdb/items` and pass the body through `readCmdbItems`, the same way `getFoobarCmdbItems` does.

Point the new pages at `getBaz*`. Leave `getStats` and `getReleases` on Mentat.

In `frontend/vite.config.ts`, proxy `/baz/api` to `http://localhost:8080`, next to the existing `/foobar/api` entry:

```ts
'/baz/api': {
  target: 'http://localhost:8080',
  changeOrigin: true,
},
```

Leave Vite `base` set to `'/'`. One JavaScript bundle serves every team. A team-specific `base` breaks asset URLs for the other sites.

The proxy entry is only for local development. Without it, the Vite server does not forward `/baz/api/*` to Spring, so the pages fail on localhost even when production is fine.

## 3. Backend API

Add a compile-time constant in `backend/src/main/java/com/releasedash/api/TeamApis.java`:

```java
public static final String BAZ = "/baz/api";
```

`@RequestMapping` has to use that constant. A string built at runtime will not compile on the annotation.

Copy `backend/src/main/java/com/releasedash/foobar/FoobarController.java` to `com.releasedash.baz.BazController`. Map it with `@RequestMapping(TeamApis.BAZ)` and return `"team": "baz"`. Keep the same paths and JSON shapes Foobar already returns. Most of those methods start empty, which is the contract the new team fills in:

| Method | Path | Starting response |
| --- | --- | --- |
| GET | `/baz/api` | `{ "team": "baz", "api": "/baz/api" }` |
| GET | `/baz/api/stats` | counts from that team's JFrog folder; zeros until the folder is set |
| GET | `/baz/api/releases` | releases in that folder (accepts `repository`, `environment`, `limit`, `offset`) |
| GET | `/baz/api/releases/{repository}` | the same list, filtered to one repository |
| GET | `/baz/api/releases/{repository}/{prNumber}` | one release, or 404 |
| GET | `/baz/api/live-services` | empty `environments` and `services` |
| GET | `/baz/api/cmdb/items` | the team's own CMDB repo (see below) |

Leave Mentat's controllers unchanged. Foobar's Memory page does not call an API, so Baz's copy stays a static page until that team adds one. Live services still start empty.

**Release artifacts.** Each team has two settings: a JFrog repo key and a folder prefix. Files are stored at `{repo-key}/{prefix}/{repository}/{environment}/{release}/`.

Mentat's folder is `mentat.jfrog.repo-key` and `mentat.jfrog.prefix` (`JFROG_MENTAT_REPO_KEY` and `JFROG_MENTAT_PREFIX`):

```properties
mentat.jfrog.repo-key=ssa-generic
mentat.jfrog.prefix=generic-release-artifacts
```

Foobar is the example of a second folder. Its settings are `foobar.jfrog.repo-key` and `foobar.jfrog.prefix` (`JFROG_FOOBAR_REPO_KEY` and `JFROG_FOOBAR_PREFIX`). Both start blank, and while either one is blank `/foobar/api/releases` returns an empty list and does not call JFrog. Set both when the folder exists. For a prefix named `foobar-release-artifacts` in the same Artifactory repository Mentat uses:

```properties
foobar.jfrog.repo-key=ssa-generic
foobar.jfrog.prefix=foobar-release-artifacts
```

`FoobarReleaseCatalog` passes that pair into the shared release reader. Stats for `/foobar/api/stats` come from the same folder.

For the Baz placeholder, add the same pair under `baz.jfrog` and a catalog like `FoobarReleaseCatalog` that reads only those two values:

```properties
baz.jfrog.repo-key=ssa-generic
baz.jfrog.prefix=baz-release-artifacts
```

**CMDB.** Foobar is the example of a team repo. Its items come from `slingshot-aerospace-inc/foobarcmdb`, configured as `foobar.cmdb.github.repo`. While `CMDB_FOOBAR_GITHUB_TOKEN` is blank, `/foobar/api/cmdb/items` returns an empty list and does not call GitHub. Setting the token makes that endpoint read `foobarcmdb`.

For the placeholder team, add the same kind of settings under `baz.cmdb` (owner, repo, branch, token, index path) and a catalog that reads only those settings, the way `FoobarCmdbCatalog` reads `foobar.cmdb`. Creates, edits, and `MENTAT-*` numbering stay on Mentat's controller.

Copy `backend/src/test/java/com/releasedash/foobar/FoobarControllerTest.java` and point the assertions at the Baz paths.

## 4. Serve the pages

Spring only serves the shared `index.html` for prefixes listed in `backend/src/main/java/com/releasedash/config/WebConfig.java`. Add the placeholder prefix there:

```java
private static final String[] TEAMS = {"mentat", "foobar", "baz"};
```

That entry is what makes a direct load of `/baz` or `/baz/releases` return the app shell. A React route by itself is not enough: refreshing the URL returns a Spring 404. Paths under `/baz/api` are left for the controller. If the controller is missing, `/baz/api` is an API 404 rather than the HTML page.

Extend `backend/src/test/java/com/releasedash/config/WebConfigTest.java` so `/baz` and `/baz/cmdb` return the shell (`id="root"`), and `/baz/api` is not served as that page.

The load balancer already routes `/assets` and the exact path `/`. Every team uses that same JavaScript and CSS, so a new team does not add another assets path.

It does need its own path, or the load balancer never forwards the site. In both `helm/release-dash/values.preview.yaml` and `helm/release-dash/values.staging.yaml`, next to `/foobar`:

```yaml
- path: /baz
  pathType: Prefix
```

That one Prefix also covers `/baz/api`. Without it, the HTML 404s at the load balancer even when the app is correct.

## Checklist

Substitute the real team name for the Baz placeholder in each item.

- [ ] Pages copied from `frontend/src/teams/foobar/`, not from Mentat's components
- [ ] `TeamLayout` title and links use `/baz/...`
- [ ] No remaining `/foobar` links or `['foobar', ...]` query keys in the new folder
- [ ] Routes in `App.tsx`, and a card on `Home.tsx` (hidden while `/` redirects to `/mentat`)
- [ ] `BAZ_API_BASE` and `getBaz*` wrappers, and the new pages call those
- [ ] Vite proxy entry for `/baz/api` (local dev only)
- [ ] `TeamApis.BAZ` and `BazController` with Foobar's paths and JSON shapes
- [ ] `baz.jfrog.repo-key` and `baz.jfrog.prefix`, following `foobar.jfrog`
- [ ] The team's own `baz.cmdb` repo settings, following `foobar.cmdb`
- [ ] `"baz"` added to `WebConfig.TEAMS`
- [ ] Prefix `/baz` in preview and staging ingress
- [ ] Controller test and `WebConfigTest` cover `/baz` and `/baz/api`
