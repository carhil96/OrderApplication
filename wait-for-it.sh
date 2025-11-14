#!/usr/bin/env bash
# wait-for-it.sh
# Original: https://github.com/vishnubob/wait-for-it
# Script para esperar a que un servicio TCP esté disponible.

set -e

hostport=""
timeout=60
cmd=()

# Parse arguments
while [[ $# -gt 0 ]]
do
    case "$1" in
        --timeout=*)
            timeout="${1#*=}"
            shift
            ;;
        --)
            shift
            cmd=("$@")
            break
            ;;
        *)
            hostport="$1"
            shift
            ;;
    esac
done

if [ -z "$hostport" ]; then
    echo "Usage: $0 host:port [--timeout=SECONDS] -- command args"
    exit 1
fi

host=$(echo $hostport | cut -d: -f1)
port=$(echo $hostport | cut -d: -f2)

echo "Waiting for $host:$port (timeout $timeout seconds)..."

start_ts=$(date +%s)

while :
do
    if nc -z "$host" "$port" >/dev/null 2>&1; then
        echo "$host:$port is available!"
        break
    fi

    now_ts=$(date +%s)
    elapsed=$((now_ts - start_ts))

    if [ "$elapsed" -ge "$timeout" ]; then
        echo "Timeout after $timeout seconds waiting for $host:$port"
        exit 1
    fi

    sleep 1
done

# Execute command
if [ "${#cmd[@]}" -gt 0 ]; then
    exec "${cmd[@]}"
fi
