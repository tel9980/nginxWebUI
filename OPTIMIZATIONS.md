# nginxWebUI — Dockge-optimized fork

This fork optimizes nginxWebUI 4.4.2 for personal/Dockge deployments.

## What changed (P0–P3)

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

1. Create a stack from `dockge-compose.yaml`.
2. The first run creates `./data` next to the compose file; keep that directory for backups.
3. Open `http://<host>:8080` from the host itself or through a reverse proxy; the compose binds only `127.0.0.1:8080` by default.
4. Set a strong admin password; do not put `init.admin`/`init.pass` secrets in compose.
5. If the in-container nginx should serve host ports 80/443, uncomment those port mappings. If you manage the host's nginx, switch to `network_mode: host` (and keep credentials protected).

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
