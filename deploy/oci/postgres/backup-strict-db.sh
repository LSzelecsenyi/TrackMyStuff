#!/bin/sh
# Dumps only Strict databases. Does not read or write /opt/puff.
set -eu

backup_dir=/opt/strict/db-backup
mkdir -p "$backup_dir"
stamp=$(date -u +%Y%m%dT%H%M%SZ)

for database in strict strict_dev; do
  docker exec strict-postgres pg_dump -U strict_app --no-owner --no-acl "$database" \
    | gzip -c > "$backup_dir/${database}-${stamp}.sql.gz"
done

find "$backup_dir" -type f -name 'strict*-*.sql.gz' -mtime +7 -delete
