# Strict DEV and PROD on the existing OCI host

Strict follows the PUFF deployment shape: GitHub Actions builds a Spring Boot jar and an Angular admin bundle, then copies them over SSH to the same OCI VM. systemd runs the jar. nginx serves the admin and proxies the API. Cloudflare is the DNS, HTTPS, and tunnel edge. PUFF does not use Cloudflare Pages, Workers, or Wrangler, so Strict does not either.

Nothing in this repository changes the PUFF repository, its GitHub Actions, its containers, or its nginx files. The steps below are manual and have not been executed.

## What is already running for PUFF

Verified from `D:\PUFF\puff`:

- Backend: SSH upload of a jar, then `systemctl restart`.
- PROD backend: `/opt/puff/prod/puff-backend.jar`, unit `puff-backend-prod`, `127.0.0.1:8080`.
- DEV backend: `/opt/puff/dev/puff-backend.jar`, unit `puff-backend-dev`, `127.0.0.1:8081`.
- UI: `rsync` of the Angular build to `/opt/puff/{dev,prod}/public-ui/`, then `nginx -t` and reload.
- PROD edge: Cloudflare proxied DNS to nginx, SSL mode Full (strict), origin certificate under `/etc/puff/tls/`.
- DEV edge: Cloudflare Tunnel `puff-dev-api` to `127.0.0.1:8081` and to nginx on port 80, plus Zero Trust Access.
- Database: Docker container `puff-postgres` on host port `5432`, databases `puff` and `puff_dev`.
- Flyway equivalent: Liquibase runs on application startup. There is no separate migration job and no automatic jar rollback.

## Strict layout on the same VM

| | DEV | PROD |
| --- | --- | --- |
| Branch | `develop` | `prod` |
| systemd unit | `strict-backend-dev` | `strict-backend-prod` |
| Bind | `127.0.0.1:8083` | `127.0.0.1:8082` |
| Jar | `/opt/strict/dev/strict-backend.jar` | `/opt/strict/prod/strict-backend.jar` |
| Env file | `/etc/strict/strict-backend-dev.env` | `/etc/strict/strict-backend-prod.env` |
| Database | `strict_dev` | `strict` |
| Admin files | `/opt/strict/dev/admin-ui/` | `/opt/strict/prod/admin-ui/` |
| Admin URL | `https://admin-dev.strictworkout.eu` | `https://admin.strictworkout.eu` |
| API URL | `https://api-dev.strictworkout.eu` | `https://api.strictworkout.eu` |

PostgreSQL is a separate container, `strict-postgres`, published only on `127.0.0.1:5434`. Volume name: `strict_postgres_data`. Network name: `strict_internal`. User: `strict_app`. These do not reuse the PUFF container, volume, port, or database role.

Flyway runs when the jar starts (`spring.flyway.enabled` is already true, `ddl-auto` is `validate`). A failed migration stops startup, the health check fails, and the workflow fails. The previous jar is left at `strict-backend.jar.prev`. Restoring it is manual:

```sh
cd /opt/strict/prod   # or /opt/strict/dev
cp -a strict-backend.jar.prev strict-backend.jar
sudo systemctl restart strict-backend-prod
```

Do not run `docker compose down` against PUFF, `docker system prune`, or delete `puff-postgres` or its volume.

## Check the VM before the first start

The VM size is not in either repository. Run these on the OCI host and keep the output:

```sh
nproc
free -h
df -h
sudo ss -lntup | grep -E '5432|5434|8080|8081|8082|8083|:80|:443' || true
docker ps --format 'table {{.Names}}\t{{.Ports}}\t{{.Status}}'
systemctl list-units 'puff-*' 'nginx*' 'cloudflared*' --no-pager
```

Stop if any of these is true:

- `8082`, `8083`, or `5434` is already listening.
- A container or volume is already named `strict-postgres` or `strict_postgres_data`.
- Less than about 2 GB of RAM is available. Two Strict JVMs are capped at 768 MB each and the heap is 384 MB, and Postgres 16 adds a few hundred MB more. A 1 GB PUFF VM cannot take both Strict environments.
- `/` has less than about 5 GB free. The jars, admin bundles, Postgres volume, and local dumps all live on this disk.

Java 21 is already required by PUFF (`java-21-openjdk`). `java -version` must report 21. nginx and Docker are already installed for PUFF. Do not reinstall them.

## One-time host setup

PUFF's deploy user in its runbook is `opc`. The workflow uses the `DEPLOY_USER` secret and does not hardcode the account. The unit files in this repo use `User=opc`. If `DEPLOY_USER` is not `opc`, change that line in both unit files before installing them.

```sh
sudo mkdir -p /opt/strict/dev/admin-ui /opt/strict/prod/admin-ui /etc/strict/tls /etc/strict/cloudflared /opt/strict/postgres /opt/strict/db-backup
sudo chown -R opc:opc /opt/strict
sudo chmod 755 /opt/strict /opt/strict/dev /opt/strict/prod
```

Replace `opc` with `DEPLOY_USER` when they differ.

Copy from a checkout of this repository, without touching `/opt/puff` or `/etc/puff`:

```sh
sudo cp deploy/oci/systemd/strict-backend-dev.service /etc/systemd/system/
sudo cp deploy/oci/systemd/strict-backend-prod.service /etc/systemd/system/
sudo cp deploy/oci/nginx/strict-dev.conf /etc/nginx/conf.d/strict-dev.conf
sudo cp deploy/oci/nginx/strict-prod.conf /etc/nginx/conf.d/strict-prod.conf
cp deploy/oci/docker-compose.postgres.yml /opt/strict/postgres/docker-compose.postgres.yml
mkdir -p /opt/strict/postgres/postgres
cp -a deploy/oci/postgres/init /opt/strict/postgres/postgres/init
cp deploy/oci/postgres/backup-strict-db.sh /opt/strict/db-backup/
chmod +x /opt/strict/db-backup/backup-strict-db.sh
```

The compose file mounts `./postgres/init` relative to itself, which is `/opt/strict/postgres/postgres/init` after the copy above.

Create the env files from the examples. Generate the database password on the server. Do not put it in git or in GitHub.

```sh
sudo cp deploy/oci/env/strict-postgres.env.example /etc/strict/strict-postgres.env
sudo cp deploy/oci/env/strict-backend-dev.env.example /etc/strict/strict-backend-dev.env
sudo cp deploy/oci/env/strict-backend-prod.env.example /etc/strict/strict-backend-prod.env
sudo chmod 600 /etc/strict/*.env
```

Fill the three files. `STRICT_POSTGRES_PASSWORD` and both `SPRING_DATASOURCE_PASSWORD` values are the same Strict password. They are not the PUFF password.

Start Postgres once:

```sh
cd /opt/strict/postgres
docker compose --env-file /etc/strict/strict-postgres.env up -d
docker exec strict-postgres psql -U strict_app -d strict -c 'select current_database();'
docker exec strict-postgres psql -U strict_app -d strict_dev -c 'select current_database();'
```

The init script creates `strict_dev` only on an empty volume. If the volume already existed, create it by hand:

```sh
docker exec strict-postgres psql -U strict_app -d postgres -c 'CREATE DATABASE strict_dev OWNER strict_app;'
```

Do not start `backend/docker-compose.yml` on this VM. That file is the laptop database on port 5433 and uses the container name `strict-postgres`.

Install and start the units only after the env files and Postgres are in place. The first `systemctl start` will fail until a jar has been uploaded. That is expected. Enable them, then let the first GitHub deploy restart them:

```sh
sudo systemctl daemon-reload
sudo systemctl enable strict-backend-dev strict-backend-prod
```

Passwordless sudo used by the workflows, and nothing else:

```text
opc ALL=(root) NOPASSWD: /bin/systemctl restart strict-backend-dev, /bin/systemctl restart strict-backend-prod, /bin/systemctl status strict-backend-dev, /bin/systemctl status strict-backend-prod, /bin/systemctl reload nginx, /usr/sbin/nginx, /bin/journalctl
```

Confirm the real paths with `command -v systemctl`, `command -v nginx`, and `command -v journalctl` before writing the sudoers file. Do not grant `docker`, `systemctl restart puff-backend-prod`, or `systemctl restart puff-backend-dev`.

Optional daily dump, only after a manual run of the script succeeds. This cron does not replace PUFF's `/opt/puff/db-backup` job.

```text
15 3 * * * /opt/strict/db-backup/backup-strict-db.sh >> /opt/strict/db-backup/backup.log 2>&1
```

## nginx and the origin certificate

Copy `strict-prod.conf` only after both certificate files exist. A missing file makes `nginx -t` fail, and PUFF's own UI deploy also runs `nginx -t` before reload.

In the Cloudflare account that already fronts PUFF:

1. Add the zone `strictworkout.eu` if it is not already there. At the registrar, set the Cloudflare nameservers shown for that zone.
2. SSL/TLS mode: Full (strict), same as PUFF.
3. SSL/TLS → Origin Server → Create Certificate. Hostnames: `strictworkout.eu`, `*.strictworkout.eu`.
4. Save the certificate as `/etc/strict/tls/origin.pem` and the key as `/etc/strict/tls/origin.key`. Mode `600` on the key. Do not replace `/etc/puff/tls/certificate.pem` or `privatekey.pem`.

Then:

```sh
sudo nginx -t && sudo systemctl reload nginx
```

Admin cookies are `SameSite=Strict` and are not scoped to the parent domain. The admin bundle calls `/api` on its own host. nginx proxies that prefix to the local jar. Do not change the admin to call `https://api.strictworkout.eu` directly, or the session cookie will not be sent.

`/api/v1/health` is the unauthenticated health check. It is the Strict equivalent of PUFF's actuator URL.

## Cloudflare DNS, tunnel, and Access

PROD, proxied A/AAAA records to the same origin address PUFF already uses for `api.puff.hu`. Get that address from the Cloudflare DNS row or the OCI console. Do not invent it here.

- `api.strictworkout.eu` → proxied to the OCI origin
- `admin.strictworkout.eu` → proxied to the OCI origin

DEV should not open a new public port. PUFF reaches DEV through the existing `cloudflared` connector.

Preferred, and the smaller RAM option: in Zero Trust → Networks → Tunnels, open the existing tunnel and add two public hostnames. Do not delete or edit the `puff.hu` hostnames. Do not run `cloudflared service install` on the VM. That command rewrites the system unit and can disconnect PUFF.

- `admin-dev.strictworkout.eu` → `http://127.0.0.1:80`
- `api-dev.strictworkout.eu` → `http://127.0.0.1:8083`

Isolated alternative, only after the memory check: create a new tunnel named `strict-dev`, put its credentials at `/etc/strict/cloudflared/strict-dev.json`, copy `deploy/oci/cloudflared/strict-dev-config.yml` to `/etc/strict/cloudflared/config.yml`, and install `deploy/oci/systemd/cloudflared-strict-dev.service` under that distinct unit name. Point `ExecStart` at `command -v cloudflared`. Leave the existing `cloudflared.service` running.

Zero Trust Access, self-hosted, one-time PIN, allow the same operator emails PUFF uses for DEV:

- Application for `admin-dev.strictworkout.eu`

Do not put an Access application on `api-dev.strictworkout.eu`. The Android DEV app calls that host and cannot complete an Access login. The API is still bound to loopback, reached only through the tunnel, and protected by the existing bearer-token auth. PROD API is public through Cloudflare in the same way `api.puff.hu` is.

PROD admin is public in the same way as `admin.puff.hu`. Access is the Google subject allowlist in `STRICT_ADMIN_GOOGLE_SUBJECTS`. An empty list admits nobody. DEV and PROD use different env files, so the lists can differ. Changing the list requires a service restart.

## GitHub

Create environments named `dev` and `prod` in the Strict repository. Leave them without required reviewers if a push should deploy by itself. A required reviewer on `prod` pauses that deploy until someone approves it.

Repository or environment secrets, same OCI host, values taken from the existing PUFF deploy key and host. Store them in the Strict repo. Do not copy workflow files back into PUFF.

| Name | Where to get it |
| --- | --- |
| `DEPLOY_SSH_KEY` | Private key that already logs in as the OCI deploy user. The PUFF runbook names the key file on the operator workstation; the public half is in `authorized_keys` on the VM. |
| `DEPLOY_HOST` | OCI public IP. Same value as PUFF's `DEPLOY_HOST`. |
| `DEPLOY_USER` | SSH account. PUFF's runbook uses `opc`. |

Environment variable, one value per environment:

| Name | Environment | Value |
| --- | --- | --- |
| `STRICT_ADMIN_GOOGLE_CLIENT_ID` | `dev` | Web client id whose JavaScript origin includes `https://admin-dev.strictworkout.eu` |
| `STRICT_ADMIN_GOOGLE_CLIENT_ID` | `prod` | Web client id whose JavaScript origin includes `https://admin.strictworkout.eu` |

The client id is public. It is written into the admin bundle at build time and is not a client secret. The writer rejects any value that is not a `*.apps.googleusercontent.com` id.

Database passwords, the Play service-account JSON, and RTDN settings stay in `/etc/strict` only.

Push to `develop` runs `deploy-dev.yml` and `deploy-dev-admin.yml`. Push to `prod` runs `deploy-prod.yml` and `deploy-prod-admin.yml`. Each workflow has its own concurrency group, so a second push to the same target waits. DEV jobs never restart `strict-backend-prod`. A failed test, jar start, health check, `nginx -t`, or smoke check fails the workflow. `workflow_dispatch` reruns the same job without a new commit.

## Google and Play, still manual

Admin sign-in uses the web client above. In Google Cloud → APIs & Services → Credentials, add the authorized JavaScript origins for both admin hosts. The backend `STRICT_GOOGLE_CLIENT_ID` must be that same web client id, because it is the ID-token audience.

Android DEV sign-in needs a separate Android OAuth client:

- Package name: `com.strictworkout.app.dev`
- SHA-1: the certificate that signs `:app:assembleDev` (the existing release upload key when `keystore.properties` is present)

The app still sends the web client id, not the Android client id. `assembleDev` uses `strict.google.serverClientId` unless `strict.google.serverClientId.dev` or `STRICT_GOOGLE_SERVER_CLIENT_ID_DEV` is set. Use the override only when the DEV backend audience is a different web client. There is no DEV client id in this repository.

Play Billing does not exist for `com.strictworkout.app.dev` until that package is a separate Play application. The DEV env file sets `STRICT_PLAY_PACKAGE_NAME` to the DEV id so it does not verify purchases for `com.strictworkout.app`. Leave the Play JSON paths empty until that application exists.

## Android DEV build

`:app:assembleRelease` and `:app:bundleRelease` are unchanged. The DEV app is a release-like build type, not a product flavor, so those task names and the Play output path stay where they are. The DEV compile classpath uses `src/release/java`, which is the production Founder, entitlement, and billing selection. Debug overrides stay on the debug build type.

| Install | applicationId | Name | API |
| --- | --- | --- | --- |
| `assembleRelease` | `com.strictworkout.app` | Strict | `https://api.strictworkout.eu` |
| `assembleDebug` | `com.strictworkout.app.debug` | Strict | loopback, or `strict.api.baseUrl` |
| `assembleDev` | `com.strictworkout.app.dev` | Strict DEV | `https://api-dev.strictworkout.eu` |

`assembleDev` uses the existing release signing config and fails closed when the upload key or the web client id is missing. Backup rules are unchanged. Android isolates Room, DataStore, and files by application id, so the three installs do not share them. Backup was already enabled; this change does not turn it on.

```powershell
.\gradlew :app:assembleDev
adb install -r app\build\outputs\apk\dev\app-dev.apk
```

The DEV API must already answer on `https://api-dev.strictworkout.eu` before sign-in can succeed.
