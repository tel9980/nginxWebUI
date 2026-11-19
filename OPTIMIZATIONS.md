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
2. The first run creates `./data` (nginxWebUI) and `./ddns-go-config` (ddns-go) next to the compose file; keep both directories for backups.
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
