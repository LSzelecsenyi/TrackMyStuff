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
# Backup scripts, the timer, and /etc/strict/strict-backup.env are installed
# from the "Database backup" section. Do not copy only backup-strict-db.sh.
```

The compose file mounts `./postgres/init` relative to itself, which is `/opt/strict/postgres/postgres/init` after the copy above.

On the current production host the running file is `/opt/strict/oci/docker-compose.postgres.yml`. `/opt/strict/postgres` is not present there. `/opt/strict/oci` is mode 707, so it is world-writable. The logging block in this repository is valid Compose. It does nothing until `strict-postgres` is recreated, and that directory mode has to be corrected before the live file is edited. Both steps are in "Database backup" and are a maintenance window, not part of copying the backup script. Do not set a Docker daemon log limit; that would change every container on the host.

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

## Database backup

The files in this repository are not installed on the VM by committing them. The bucket and the instance-principal check already exist. The daily job keeps using whatever is already on the host until the checklist at the end of this section is run. Do not run that checklist as part of editing the repository. Do not delete `strict-anna-test-cleanup-20261108T190018Z.dump` or `automatic/connection-test.txt`.

The job dumps only the database `strict`, in PostgreSQL custom format, from the container `strict-postgres`. It does not dump `strict_dev` and it does not read the PUFF backup profile or any AWS credentials. Manual dumps stay in `/opt/strict/db-backup/manual`. Automatic dumps stay in `/opt/strict/db-backup/automatic` and use the name `strict-automatic-YYYYMMDDTHHMMSSZ.dump`, with the timestamp in UTC. Do not install the earlier seven-day `strict*-*.sql.gz` cron.

### Object Storage

| | |
| --- | --- |
| Region | `eu-frankfurt-1` |
| Namespace | `frdlgn4i5lzy` |
| Bucket | `strict-prod-backups` |
| Object prefix | `automatic/` |
| Authentication | Instance principal. The CLI flag is `--auth instance_principal`. There is no API key and no `~/.oci` user. |
| CLI | `/usr/bin/oci`, version 3.95.1 on the host that was checked |

The bucket is private, Standard tier, encrypted with Oracle-managed keys, and versioning is off. The VM reaches it with the dynamic group `strict-prod-backup-instances` and the policy `strict-prod-backup-objectstorage`. The script talks only to this namespace and bucket.

The object name is `automatic/` plus the local file name, for example `automatic/strict-automatic-20261109T031500Z.dump`. A partial file is never uploaded. A manual dump is never uploaded. `--no-overwrite` is always set. The script never calls object delete.

The env file uses these names: `STRICT_BACKUP_OFFHOST`, `STRICT_BACKUP_REGION`, `STRICT_BACKUP_NAMESPACE`, `STRICT_BACKUP_BUCKET`, `STRICT_BACKUP_PREFIX`. `STRICT_BACKUP_PREFIX` must be `automatic/`. An unknown key, including a credential variable, is rejected.

### Permissions

The instance principal is expected to read the bucket, create a new object, and read object metadata and bytes. It must not have `OBJECT_DELETE` or `OBJECT_OVERWRITE`, and it must not have access to other buckets.

`PutObject` of a new name needs `OBJECT_CREATE` only. `HeadObject` needs `OBJECT_INSPECT` or `OBJECT_READ`. `GetObject`, used by the off-host restore check, needs `OBJECT_READ`.

Multipart upload is a different API. `CreateMultipartUpload`, `UploadPart`, and `CommitMultipartUpload` require `OBJECT_OVERWRITE` even when the object name is new, and the commit also requires `BUCKET_READ` and `OBJECT_READ`. That permission is not granted, and this design does not ask for it. The script therefore uploads with `--no-multipart` for every dump up to the `PutObject` limit of 50 GiB. The multipart path is in the script so the checksum rules can be tested, and `multipart_enabled` returns failure so a production run cannot select it. A dump larger than 50 GiB is left on local disk, retention does not run, and the log says `permission=OBJECT_OVERWRITE`. No IAM change is required for the path that actually runs.

### What one run does

The script takes an exclusive `flock` on `/opt/strict/db-backup/automatic/backup.lock`. A second run exits 3.

1. For each verified local dump that does not yet have a matching upload receipt, oldest first, try the upload once. At most 30 of those are attempted. The rest are logged as `retry_deferred` and the run is already a failure.
2. `pg_dump` writes a temporary file in the automatic directory.
3. The file must start with `PGDMP`, be at least 1024 bytes, and `pg_restore --list` must show table data for `app_user`, `external_identity`, `auth_session`, and `flyway_schema_history`.
4. The temporary file is renamed into place and is not modified again.
5. When off-host upload is required, the script checks whether `automatic/<filename>` already exists.
6. A missing object is uploaded. An existing object is not uploaded again.
7. `HEAD` must show the same byte length. For a single-part object, `content-md5` must match the file. That value is not the `etag`. After this script's own upload, `opc-content-sha256` and the user metadata `sha256` must match as well.
8. A receipt named `<filename>.uploaded`, mode 600, is written only after that check. The receipt is not a backup.
9. Local retention runs only when every required upload in this run succeeded.
10. The lock is released when the process exits.

`local_success=yes` means the new dump passed the local checks. `offhost_success=yes` means every required upload in the run was verified. `full_success=yes` requires both. `offhost_success=disabled` with `full_success=no` means the env file left upload off; the local dump still counts as a successful local run and the process exits 0. A required upload that fails exits non-zero. systemd marks the service failed. The local dump is kept.

A zero exit from `oci os object put --no-overwrite` is not proof. When the name already exists, that flag exits 0 and does not compare bytes. The script always `HEAD`s the object and compares length and checksum.

### Checksums

Single-part uploads, which are the ones this host will perform, send `--content-md5` and `--opc-checksum-algorithm SHA256` with `--opc-content-sha256`. Object Storage checks the body. The following `HEAD` must agree on `content-length`, `content-md5`, and `opc-content-sha256`.

If multipart were turned on later, `content-md5` is not the MD5 of the whole file and `opc-content-sha256` is not returned. The comparable values are `content-length`, `opc-multipart-md5`, and `opc-multipart-sha256`: the hash of the concatenated part hashes, base64, then `-` and the part count. The part size would be 128 MiB. The script does not treat `etag` as either checksum.

### Retry

A local dump with no valid receipt is pending. The next run tries it again before taking a new dump. One attempt per file per run. There is no sleep loop.

- Remote object missing: upload it.
- Remote object present and the length and checksum match: write the receipt. Do not upload again.
- Remote object present and the bytes differ: do not upload, do not delete either copy, exit non-zero. The VM cannot delete the object. Someone with delete permission has to remove that object name before a later run can upload the local file. Until then every daily run reports `offhost_mismatch` and keeps the local file.
- Upload or `HEAD` failure: keep the local file, skip retention, exit non-zero. The next timer tries again.

Pending files are not deleted by retention. A failed upload therefore cannot remove the only remaining copy. If uploads stay broken, a new local file is added every day and none of the pending ones are removed, so the disk can fill. That shows up as a failed `strict-db-backup.service`.

### Retention

Local automatic files use a 20-day rule inside `/opt/strict/db-backup/automatic` only. The timestamp is the UTC time in the file name. A file is eligible when that time is strictly older than 20 days. A file whose timestamp is exactly 20 days old is kept. The newest automatic file is kept even when it is older than that. A pending upload is kept even when it is older than that. Files that do not match `strict-automatic-YYYYMMDDTHHMMSSZ.dump` are ignored. `/opt/strict/db-backup/manual` is never listed. `--retention-dry-run` prints the same decisions and deletes nothing. A failed run does not reach retention.

Twenty days is not a maximum. The newest local dump, and any dump whose upload has not been verified, can remain until a later run both succeeds and is allowed to delete them.

Remote objects under `automatic/` are removed by the lifecycle rule `delete-automatic-backups-after-20-days` (delete at 20 days). A second rule deletes uncommitted multipart uploads after 7 days. This script does not start those uploads and does not delete objects. Lifecycle deletion is asynchronous and uses the object's age in the bucket, which is the upload time, not the timestamp in the file name. An object can remain past 20 days while the rule is catching up. `automatic/connection-test.txt` is not a database backup. The same prefix rule will delete it. Leave it in place.

### Install the scripts

On the VM, from a checkout, as root:

```sh
install -d -m 0700 /opt/strict/db-backup/automatic
install -m 0700 deploy/oci/postgres/backup-strict-db.sh /opt/strict/db-backup/backup-strict-db.sh
install -m 0700 deploy/oci/postgres/verify-strict-backup.sh /opt/strict/db-backup/verify-strict-backup.sh
install -d -m 0700 /etc/strict
install -m 0600 deploy/oci/env/strict-backup.env.example /etc/strict/strict-backup.env
chown root:root /etc/strict/strict-backup.env /opt/strict/db-backup/backup-strict-db.sh /opt/strict/db-backup/verify-strict-backup.sh
```

`/etc/strict/strict-backup.env` must be owned by root and mode 600. The script refuses to read it otherwise. It is not committed. The example in git already contains the namespace, bucket, region, and `STRICT_BACKUP_OFFHOST=required`. Those are not secrets. `python3`, `openssl`, and `oci` must be on `PATH` when upload is required. `flock` comes from util-linux.

Confirm before the first dump:

```sh
stat -c '%U %a' /etc/strict/strict-backup.env
/opt/strict/db-backup/backup-strict-db.sh --self-test
```

`--self-test` does not touch the database or the bucket.

### Timer

`strict-db-backup.timer` fires at 03:15:00 UTC, with `Persistent=true`, `RandomizedDelaySec=300`, and `AccuracySec=1min`. A start is therefore between 03:15 and 03:20 UTC, and a missed day runs after the VM boots. The service is `Type=oneshot`, root, `UMask=0077`, `Nice=10`, `TimeoutStartSec=6h`, and both streams go to the journal under `SyslogIdentifier=strict-db-backup`. The default start timeout is 90 seconds, which is shorter than a dump, so the unit file sets 6 hours. The script reads the env file itself. Do not add `EnvironmentFile=`; a missing file would then fail the unit instead of leaving upload disabled.

```sh
install -m 0644 deploy/oci/systemd/strict-db-backup.service /etc/systemd/system/strict-db-backup.service
install -m 0644 deploy/oci/systemd/strict-db-backup.timer /etc/systemd/system/strict-db-backup.timer
systemctl daemon-reload
```

Enable the timer only after the first manual run and both restore checks below have succeeded.

### First manual run

```sh
/opt/strict/db-backup/backup-strict-db.sh
```

The journal and `/opt/strict/db-backup/automatic/backup.log` (mode 600) should contain `local_success=yes`, `offhost_success=yes`, and `full_success=yes`, then `result=verified` and the file name. `offhost_success=disabled` means the env file was not `required`. That is a local dump only.

Take the file name from that line and check the object. Replace the file name; do not invent one.

```sh
oci os object head \
  --auth instance_principal \
  --region eu-frankfurt-1 \
  --namespace-name frdlgn4i5lzy \
  --bucket-name strict-prod-backups \
  --name automatic/strict-automatic-YYYYMMDDTHHMMSSZ.dump
```

`content-length` must equal `wc -c` of the local file. `content-md5` must equal `openssl dgst -md5 -binary` of that file, base64-encoded on one line. Do not compare `etag` to the MD5.

### Restore checks

Both checks use a new container `strict-restore-test`, a new volume `strict_restore_test_data`, `--network none`, and the image already used by `strict-postgres`. They do not mount `strict_postgres_data` and they do not restore into `strict-postgres`. They print table names and a Flyway success count, not row contents. The container and the volume are removed at the end. If either name already exists, the script stops.

Local file:

```sh
/opt/strict/db-backup/verify-strict-backup.sh \
  /opt/strict/db-backup/automatic/strict-automatic-YYYYMMDDTHHMMSSZ.dump
```

Object Storage, after the local check has succeeded. The object is downloaded to a mode 600 file under `/tmp`, the size and checksum are checked, and that file is removed even when the restore fails.

```sh
/opt/strict/db-backup/verify-strict-backup.sh \
  --object automatic/strict-automatic-YYYYMMDDTHHMMSSZ.dump
```

A manual dump can be passed as a local path. It is not a substitute for the automatic object check. `verify-strict-backup.sh --self-test` checks names and checksum parsing and does not start Docker.

### Failure monitoring

```sh
systemctl status strict-db-backup.service --no-pager
journalctl -u strict-db-backup.service -n 200 --no-pager
```

A failed upload leaves `local_success=yes offhost_success=no full_success=no` and `result=failed stage=offhost_put`, `offhost_verify`, `offhost_mismatch`, `offhost_head`, or `offhost_pending`. The log lines are file names, byte counts, and stages. They do not include rows from the database. `backup.log` is mode 600.

### Log rotation maintenance window

`deploy/oci/docker-compose.postgres.yml` sets the `json-file` driver, `max-size` 10m, and `max-file` 3 on the Strict Postgres service. That block is valid. The running container was created from `/opt/strict/oci/docker-compose.postgres.yml` and does not pick up the limit until that container is recreated.

`/opt/strict/oci` is mode 707. Correct that before editing the live Compose file, so another user cannot replace the file in a world-writable directory. Owner root and mode 755 removes the world write. Do not change `/etc/docker/daemon.json`.

Recreate only `strict-postgres`, during a window where a short database outage is acceptable. Do not recreate PUFF containers and do not run `docker compose down` against PUFF. Do this separately from the first backup. The backup script does not need the logging change.

### Rollback

Stop the timer, put `STRICT_BACKUP_OFFHOST=disabled` in the env file or remove that file, and restore the previous `backup-strict-db.sh` if the new script misbehaves. Leave every local dump and every object in the bucket. The lifecycle rule deletes `automatic/` objects. The script will not. Do not delete `strict-anna-test-cleanup-20261108T190018Z.dump`.

```sh
systemctl disable --now strict-db-backup.timer
```

### Rollout checklist

Run this on the VM after the repository files are copied. It is not part of the change that added them.

```sh
install -d -m 0700 /opt/strict/db-backup/automatic /etc/strict
install -m 0700 deploy/oci/postgres/backup-strict-db.sh /opt/strict/db-backup/backup-strict-db.sh
install -m 0700 deploy/oci/postgres/verify-strict-backup.sh /opt/strict/db-backup/verify-strict-backup.sh
install -m 0600 deploy/oci/env/strict-backup.env.example /etc/strict/strict-backup.env
chown root:root /etc/strict/strict-backup.env
stat -c '%U %a %n' /etc/strict/strict-backup.env
/opt/strict/db-backup/backup-strict-db.sh --self-test
/opt/strict/db-backup/verify-strict-backup.sh --self-test
/opt/strict/db-backup/backup-strict-db.sh
# Replace the file name from the result=verified line.
oci os object head --auth instance_principal --region eu-frankfurt-1 \
  --namespace-name frdlgn4i5lzy --bucket-name strict-prod-backups \
  --name automatic/strict-automatic-YYYYMMDDTHHMMSSZ.dump
/opt/strict/db-backup/verify-strict-backup.sh /opt/strict/db-backup/automatic/strict-automatic-YYYYMMDDTHHMMSSZ.dump
/opt/strict/db-backup/verify-strict-backup.sh --object automatic/strict-automatic-YYYYMMDDTHHMMSSZ.dump
install -m 0644 deploy/oci/systemd/strict-db-backup.service /etc/systemd/system/strict-db-backup.service
install -m 0644 deploy/oci/systemd/strict-db-backup.timer /etc/systemd/system/strict-db-backup.timer
systemctl daemon-reload
systemctl enable --now strict-db-backup.timer
systemctl list-timers strict-db-backup.timer --no-pager
```

Confirm the manual dump is still present:

```sh
ls -l /opt/strict/db-backup/manual/strict-anna-test-cleanup-20261108T190018Z.dump
```

The logging change and the `/opt/strict/oci` mode fix are a later window. They are not in this list.

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

## Marker HMAC key

One-time Founder and promotion protection, and the Play obfuscated account id, use HMAC-SHA-256. The key is not in source control. DEV and PROD each need their own key. Do not reuse the test key from `backend/src/test/resources/application.properties`.

Generate 32 bytes and put them in the env file, mode `600`:

```sh
openssl rand -base64 32
```

```
STRICT_MARKER_HMAC_KEYS=1:<that base64 value>
STRICT_MARKER_HMAC_ACTIVE_KEY=1
```

Back up the same value in a password manager or other offline store that is not the server and not git. Losing it does not reveal Google subjects, but the server can no longer tell a deleted account from a new one. After the first deletion has stored a key checksum, a missing or wrong key fails closed: one-time benefits are refused and account deletion is refused, rather than granting those benefits again. Restore the backed-up value to return to normal checks. Do not invent a new key for an existing key id.

To rotate, add a second key and keep the old one in the list:

```
STRICT_MARKER_HMAC_KEYS=1:<old>,2:<new>
STRICT_MARKER_HMAC_ACTIVE_KEY=2
```

New markers use key 2. An old marker is rewritten to key 2 when that Google account signs in or is deleted again. The raw subject is not stored, so there is no bulk rewrite. Do not remove key 1 while any marker written with it might remain. Removing a key id that still has a stored checksum fails closed for everyone until that key is restored.

Account deletion unlinks a Play subscription and keeps the purchase token, token hash, package, product, state, expiry, order id, and the time the subscription ended. It does not cancel the subscription at Google. A still-active purchase, including one made before an account tag existed, can be verified onto one new Strict account. Realtime notifications and reconciliation refresh that row and do not move it to a different account. The two-day reconciliation lookback is not a retention period.

A scheduled job deletes an ended purchase row 24 calendar months after `ended_at`. `ended_at` is the expiry for an expired or lapsed subscription, or the first time a revoked purchase was confirmed. Entitled, paused, pending, grace, and account-hold rows are not deleted. A row with `retention_hold` true is kept. The application does not set that flag. Set it in the database only when a specific row must be kept for a dispute or another reason decided outside this product rule. Rows whose end cannot be verified are left in place and counted in the cleanup log. The log does not include tokens or account ids. Play notification ids in `play_rtdn_message` are kept so a repeated notification is ignored. They do not contain the purchase token or a Strict account. This document does not state that 24 months satisfies a tax, accounting, or legal duty.

A one-time marker flag is cleared only after that program is RETIRED. The row is deleted when every flag is false. Pausing a program does not clear the marker.

Pro Discovery activity dates and device snapshots are deleted when a trial row exists, when the account is deleted, and when synchronization is turned off. An extra device snapshot with no refresh for 18 months is removed only if another snapshot for that account was refreshed in those 18 months. The only remaining snapshot is kept. They are not kept for 24 months. No new environment variable is required.

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
