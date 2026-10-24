#!/usr/bin/env bash
#
# Local development database for the POS backend.
#
# Runs a PostgreSQL server with its data directory inside this project
# (.dev/postgres), so no system service and no Docker are required.
#
#   ./scripts/dev-db.sh start    # initialise (first run) and start
#   ./scripts/dev-db.sh stop     # shut the server down
#   ./scripts/dev-db.sh status   # show whether it is running
#   ./scripts/dev-db.sh reset    # stop and delete all local data
#
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_DIR="$BASE_DIR/.dev/postgres"
DATA_DIR="$DEV_DIR/data"
RUN_DIR="$DEV_DIR/run"
PID_FILE="$DATA_DIR/postmaster.pid"
LOG_FILE="$RUN_DIR/postgres.log"

DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-posdb}"
DB_USER="${DB_USER:-mapalo}"
DB_PASSWORD="${DB_PASSWORD:-mapalo}"
TEST_DB_NAME="${TEST_DB_NAME:-posdb_test}"

require_postgres() {
    for bin in initdb pg_ctl psql; do
        if ! command -v "$bin" >/dev/null 2>&1; then
            echo "$bin not found. Install PostgreSQL first (e.g. 'pacman -S postgresql')." >&2
            exit 1
        fi
    done
}

is_running() {
    pg_ctl -D "$DATA_DIR" status >/dev/null 2>&1
}

# Superuser connection over the local socket (trust auth, loopback-only instance).
admin_psql() {
    psql -v ON_ERROR_STOP=1 -q -h "$RUN_DIR" -p "$DB_PORT" -U postgres -d postgres "$@"
}

initialize() {
    if [[ -f "$DATA_DIR/PG_VERSION" ]]; then
        return 0
    fi

    echo "Initialising database in $DATA_DIR ..."
    mkdir -p "$DATA_DIR" "$RUN_DIR"
    # 'trust' auth: the server only listens on 127.0.0.1 and a private socket,
    # so this is a loopback-only development instance.
    initdb -D "$DATA_DIR" -U postgres --auth=trust --encoding=UTF8 --locale=C.UTF-8 >/dev/null
}

ensure_database() {
    local name="$1"
    if [[ "$(admin_psql -tAc "SELECT 1 FROM pg_database WHERE datname = '$name'")" != "1" ]]; then
        admin_psql -c "CREATE DATABASE \"$name\" OWNER \"$DB_USER\""
    fi
}

bootstrap() {
    # Idempotent: create the application role and databases on every start.
    if [[ "$(admin_psql -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$DB_USER'")" != "1" ]]; then
        admin_psql -c "CREATE ROLE \"$DB_USER\" LOGIN PASSWORD '$DB_PASSWORD'"
    fi
    ensure_database "$DB_NAME"
    ensure_database "$TEST_DB_NAME"
}

start() {
    require_postgres
    mkdir -p "$RUN_DIR"

    if is_running; then
        echo "PostgreSQL is already running (port $DB_PORT)."
        # Idempotent: make sure the application and test databases exist.
        bootstrap
        return 0
    fi

    initialize

    echo "Starting PostgreSQL on 127.0.0.1:$DB_PORT ..."
    pg_ctl -D "$DATA_DIR" -l "$LOG_FILE" -w \
        -o "-p $DB_PORT -c listen_addresses=127.0.0.1 -c unix_socket_directories=$RUN_DIR" \
        start >/dev/null || {
        echo "PostgreSQL did not become ready. Last lines of $LOG_FILE:" >&2
        tail -n 30 "$LOG_FILE" >&2 || true
        exit 1
    }

    bootstrap

    echo "PostgreSQL ready: jdbc:postgresql://localhost:$DB_PORT/$DB_NAME (user: $DB_USER)"
    echo "Log: $LOG_FILE"
}

stop() {
    if [[ ! -d "$DATA_DIR" ]] || ! is_running; then
        echo "PostgreSQL is not running."
        return 0
    fi

    echo "Stopping PostgreSQL ..."
    pg_ctl -D "$DATA_DIR" -m fast -w stop >/dev/null
    echo "PostgreSQL stopped."
}

status() {
    if [[ -d "$DATA_DIR" ]] && is_running; then
        echo "PostgreSQL is running (port $DB_PORT, socket dir $RUN_DIR)."
    else
        echo "PostgreSQL is not running."
        return 1
    fi
}

reset() {
    stop
    echo "Deleting $DEV_DIR ..."
    rm -rf "$DEV_DIR"
    echo "Local database removed."
}

case "${1:-start}" in
    start) start ;;
    stop) stop ;;
    status) status ;;
    reset) reset ;;
    *)
        echo "Usage: $0 {start|stop|status|reset}" >&2
        exit 2
        ;;
esac
