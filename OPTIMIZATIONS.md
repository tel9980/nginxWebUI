# nginxWebUI — Dockge-optimized fork

This fork optimizes nginxWebUI 4.4.2 for personal/Dockge deployments.

## What changed (P0–P3)

### Round 2 hardening — security and reliability
- In-app self-update is disabled by default (`project.allowInAppUpdate=false`); upgrade by replacing the image instead. The "new version" prompt is hidden when disabled.
- Added a per-IP rate limit (5 attempts / 60s) to API token issuance.
- Added a session CSRF token for adminPage state-changing requests; the shared frontend sends it via `X-CSRF-Token` (configurable with `project.csrfProtection`).
- Sensitive settings (`nginxExe`, `nginxPath`, mail secrets, credentials, etc.) can no longer be read/written through the generic `getKey`/`setKey`/`getLastCmd` endpoints.
- nginx verification/reload/run command failures now return `renderError` rather than success.
- Config publishing stages a full config tree, preserves the previous config under `.nginxwebui-backup/`, and saves the **old** content in backup history; rollback also runs `nginx -t` before restoring.
- SQLite connections now enable WAL, `busy_timeout=5000`, and foreign keys.
- Compose adds `no-new-privileges`, drops all Linux capabilities except the minimum needed, sets `pids_limit: 256`, `/tmp` tmpfs, and a 1 CPU limit.

### P0 — safety and observability
- `--project.containerSafe=true`: startup no longer broad-scans/kills arbitrary `nginxWebUI`/`.jar` JVMs. It only stops a previous instance recorded in `nginxWebUI.pid`, and only if that PID is still an nginxWebUI JVM.
- Removed startup `pkill -9 nginx`; generated config is left for explicit reload (set `NGINXWEBUI_AUTOSTART_NGINX=false` disables nginx auto-start entirely).
- Confined the admin file picker to `--project.filesystemRoot=/home/nginxWebUI/` (empty value keeps legacy behavior).
- Java logs are no longer redirected to `/dev/null`; `docker logs`/Dockge logs now show startup and errors.
- Container uses an isolated PID namespace; do **not** add `pid: host`.
- acme.sh bundle is unpacked only when missing; restarting no longer overwrites certificate/account data.

### P1 — persistence and Dockge ergonomics
- Single persisted directory: `/home/nginxWebUI` (database, nginx.conf, acme.sh, logs).
- Added `dockge-compose.yaml` with localhost-only Web UI port, healthcheck and 512 MB memory limit.
- Bridge network is the default; host-network variant is documented in the compose file.

### P2 — slimmer image
- Replaced the all-modules Alpine image with a slim Alpine 3.21 base (stream, headers-more, lua only).
- Multi-arch GitHub Actions build (amd64/arm64) with GHCR push.
- Added OCI labels and Docker `HEALTHCHECK`.

### P3 — Java runtime
- Maven compiler and runtime upgraded from Java 8 to Java 17 (`openjdk17-jre-headless`).
- Removed unused `javax.swing`/`javax.script` imports; legacy Maven model reader still works under Java 17.

## Deploy with Dockge

1. Create a stack from `dockge-compose.yaml`. It starts both `nginxwebui` and the optimized `ddns-go` image in one Dockge stack.
2. The first run creates `./data` (nginxWebUI) and a named `ddns-go-config` volume for ddns-go; keep both for backups.
3. Open `http://<host>:8080` from the host itself or through a reverse proxy; the compose binds only `127.0.0.1:8080` by default.
4. ddns-go's Web UI listens on port 9876. Because it uses `network_mode: host`, its "block public access" protection sees the real client address. Keep that option enabled unless you deliberately put it behind nginxWebUI with TLS and Basic Auth.
5. To expose ddns-go through nginxWebUI's HTTPS entry, create a reverse-proxy server in nginxWebUI pointing to `http://host.docker.internal:9876` (the compose maps `host.docker.internal` to the host gateway). If nginxWebUI itself uses `network_mode: host`, use `http://127.0.0.1:9876` instead. Add these headers to that server's config:
   ```nginx
   proxy_set_header Host $host;
   proxy_set_header X-Real-IP $remote_addr;
   proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
   ```
   If you leave ddns-go's `NotAllowWanAccess` enabled, nginx's proxy request arrives from the host gateway address or loopback (both private), so it is considered local. Do not forward spoofed `X-Forwarded-For` values to ddns-go; it only logs those headers and still trusts the actual socket source.
6. Set a strong admin password; do not put `init.admin`/`init.pass` secrets in compose.
7. If the in-container nginx should serve host ports 80/443, uncomment those port mappings. If you manage the host's nginx, switch to `network_mode: host` (and keep credentials protected).

## Build from source

```bash
mvn -B clean package -DskipTests
docker build -t nginxwebui:4.4.2-optimized .
```

GitHub Actions builds and pushes `ghcr.io/tel9980/nginxwebui:4.4.2-optimized` and `:optimized` on every push to `master`.

## Compatibility

The image defaults to the optimized flags. Bare-metal users can omit the new flags and keep legacy behavior. The application still supports Java 17; no tests were added upstream.

## Security note

nginxWebUI can generate nginx config and execute privileged nginx/acme operations. Treat it as a high-privilege admin tool: keep it off the public Internet, use a reverse proxy with authentication/TLS, and back up the persisted data directory.

## Round 5 — dependency vulnerability fixes and README refresh

- **Java dependencies**: pinned patched transitive versions directly in `pom.xml` while keeping Solon `3.3.3` (a major upgrade would risk breaking this older codebase): `undertow-core` and `undertow-servlet` `2.2.37.Final → 2.2.39.Final` (fixes `GHSA-j382-5jj3-vw4j` / `GHSA-6h4f-pj3g-q8fq`), and `plexus-utils` `2.0.4 → 3.5.1` (fixes `GHSA-8vhq-qq4p-grq3` / `GHSA-g6ph-x5wf-g337`). Verified inside the fat jar via each artifact's `pom.properties`.
- **Base images**: both Dockerfiles now use **Alpine 3.22** and run `apk upgrade --no-cache` after package install. Alpine 3.21's repositories lacked the patched nginx/curl/luajit builds; 3.22 ships nginx `1.28.3-r8`, curl `8.14.1-r4` and luajit `r1`, and ddns-go's zlib/OpenSSL `Critical` findings are cleared. Java 17 (`openjdk17-jre-headless`) is available in the 3.22 community repo.
- **Remaining scan findings**: the Java `Critical` advisories (`undertow`, `plexus-utils`) are gone. A handful of Alpine `Critical` advisories remain on nginx/curl with **no fixed package available upstream** (Grype shows no fix version, EPSS < 2%); changing the base image further cannot clear them until Alpine ships a fix. They are recorded in observation mode only and do not block the build.
- **README**: `README.md` / `README_EN.md` in both repositories now lead with a short section describing the optimized fork, GHCR tags, Dockge usage, key safety switches, and upstream links.
- Remaining from the image scan: low-EPSS `High` advisories in Alpine packages (patched by `apk upgrade`), and `httpclient 4.5.12 → 4.5.13` (medium, from Solon's HTTP stack). No `Critical` findings are expected after this round; the scan remains in observation mode.

## Round 4 hardening — data safety, CSRF tightening, supply chain

- **Import safety**: `setAsycPack` now snapshots writable config/cert files before import, confines all imported file paths to the project home via `PathSafetyUtil`, rolls back on failure, and surfaces errors instead of swallowing them.
- **Safe export**: backups now default to secret-free (`includeSecrets=false`): TLS private keys, password files, ACME/cert archives and DNS/API credentials are omitted with restore notes; old behavior is available with `?includeSecrets=true`.
- **Certificate imports** are atomic: extract to a temp dir, validate, then rename-swap the old directory to `.bak-<ts>`; failures keep the previous certificates intact.
- **Proxy trust / SSRF**: `buildUrl` now uses URI parsing and ignores `X-Forwarded-Host/Port` unless `project.trustedProxyHeader=true` and the value matches `project.allowedHosts`.
- **CSRF tightened**: creditKey no longer bypasses CSRF checks, and initial-admin creation is exempt only while no admins exist.
- **Removed in-app self-update routes** entirely; upgrades are image-only.
- **SQLite backups**: a daily task writes `backup/sqlite-*.db` via `VACUUM INTO` (with a copy fallback) and keeps the newest N copies (`project.sqliteBackupEnabled/Keep`).
- **Port validation**: listen directives are validated as 1–65535 on both frontend and backend.
- **Supply chain**: both image workflows now run an Anchore vulnerability scan (observation mode) and publish a SPDX SBOM artifact. ddns-go additionally gets a weekly upstream-sync check workflow and Chinese rebasing guide.

### New config flags (Round 4)
- `project.trustedProxyHeader: false`, `project.allowedHosts:` (empty by default).
- `project.sqliteBackupEnabled: true`, `project.sqliteBackupKeep: 7`.
- ddns-go: `DDNS_TRUST_PROXY=true` only when running behind a trusted local reverse proxy.

## Round 6 — pure open-source edition

- Removed the only remaining "professional edition" traces: the red upgrade link in the admin header, the `commonStr.pro` i18n key in all three locale files, and the pro-vs-open-source comparison sections and links from both `README.md` and `README_EN.md`.
- This fork has always been the pure open-source codebase; the commercial edition is a separate upstream product whose code is not present here. No features were changed.

## Round 7 — remove upstream contact/donation blocks

- Removed upstream QQ groups, e-mail, official-site/Gitee/GitHub/video links and the WeChat donation QR from both `README.md` and `README_EN.md` (the functional Gitee jar download links in the install section are kept).
- Replaced the admin "About" page content with short fork information (repo, GHCR image, change-log pointer) and removed the `aboutStr.author/donation/email/qqGroup/gitee/github` i18n keys from all three locale files.
- The upstream original documentation below the fork banner is otherwise unchanged.

## Round 3 hardening — credentials, offline-by-default, ddns-go auth

### nginxWebUI
- Version check is now disabled unless `project.allowInAppUpdate=true`; with the default image config there are no external network calls to `nginxwebui.cn` from startup or the daily schedule.
- API tokens now honor a configurable lifetime (`project.apiTokenTtlMinutes`, default 60 minutes) instead of a fixed 24 hours.
- creditKey (remote-management credential) now supports `project.creditKeyEnabled` and `project.creditKeyTtlMinutes` (0 = legacy never-expire). Revoked/expired keys are rejected, and last-used time is tracked.
- Added "Revoke API" / "Revoke Remote" buttons on the admin list; changing a password now also revokes that admin's API token and all remote keys.
- Added `reverse-proxy-ddns-go.conf.example` — a ready-to-copy server block that proxies ddns-go through nginxWebUI via `host.docker.internal:9876`.

### ddns-go (optimized fork)
- Login is now rate-limited per client IP (5 failures → 30-minute lock), replacing the global counter; the limiter keys on `RemoteAddr` and ignores spoofable `X-Forwarded-For`/`X-Real-IP`.
- Added CSRF tokens: the login page and authenticated pages issue a `csrf_token` cookie, and all `POST`/`PUT`/`DELETE`/`PATCH` requests must echo it in the `X-CSRF-Token` header. `static/utils.js` sends it automatically.
- Initial setup no longer trusts the `Referer` header to decide public exposure: new installs default to `NotAllowWanAccess=true` (relax manually if you intentionally expose the UI).
- The optimized GHCR workflow now runs `go test ./...` before building.

### CI
- Both workflows use current action majors: `actions/checkout@v7`, `actions/setup-java@v5` / `actions/setup-go@v7`, Docker actions `@v4`–`@v6`, `docker/metadata-action@v5`.

### Config flags (all in `app.yml`, overridable via `BOOT_OPTIONS`)
- `project.allowInAppUpdate` (default `false`) — also gates version check.
- `project.csrfProtection` (default `true`).
- `project.apiTokenTtlMinutes` (default `60`).
- `project.creditKeyEnabled` (default `true`), `project.creditKeyTtlMinutes` (default `0` = no expiry).
