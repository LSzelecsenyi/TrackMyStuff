#!/bin/sh
# Runs only when the data volume is empty. An existing volume needs the
# manual CREATE DATABASE in docs/deploy-oci.md.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<'EOSQL'
CREATE DATABASE strict_dev OWNER strict_app;
EOSQL
