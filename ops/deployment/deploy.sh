#!/usr/bin/env bash
set -Eeuo pipefail

image=${1:?Expected immutable GHCR image reference}
directory=${2:?Expected absolute deployment directory}
[[ "$image" =~ ^ghcr\.io/[a-z0-9._/-]+@sha256:[a-f0-9]{64}$ ]] || { echo 'Invalid image digest' >&2; exit 2; }
[[ "$directory" == /* && "$directory" != / ]] || { echo 'Deployment directory must be absolute' >&2; exit 2; }
[[ -d "$directory" && -f "$directory/.env" ]] || { echo 'Provision deployment directory and .env first' >&2; exit 2; }
command -v flock >/dev/null
exec 9>"$directory/deploy.lock"
flock -n 9 || { echo 'Another deployment is running' >&2; exit 3; }

export STAYHUB_ENV_FILE="$directory/.env"
export STAYHUB_IMAGE="$image"
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
compose=(docker compose --project-name stayhub-staging --env-file "$STAYHUB_ENV_FILE"
    -f "$root/docker-compose.yml" -f "$root/docker-compose.deploy.yml")
"${compose[@]}" config -q
previous=''
container=$("${compose[@]}" ps -q booking-app)
if [[ -n "$container" ]]; then
    previous=$(docker inspect --format '{{.Config.Image}}' "$container")
fi

# Pull before changing any running service. Server Docker credentials stay on host.
"${compose[@]}" pull booking-app booking-migrate
"${compose[@]}" up -d --no-build --wait --wait-timeout 240 postgres kafka
if ! "${compose[@]}" run --rm --no-deps booking-migrate; then
    echo 'Migration failed; application image was not replaced' >&2
    exit 1
fi
if "${compose[@]}" up -d --no-build --no-deps --wait --wait-timeout 240 booking-app; then
    printf '%s\n' "$image" > "$directory/current-image.tmp"
    mv -- "$directory/current-image.tmp" "$directory/current-image"
    echo "Deployment healthy: $image"
else
    echo 'Deployment failed health checks' >&2
    if [[ -n "$previous" ]]; then
        export STAYHUB_IMAGE="$previous"
        if "${compose[@]}" up -d --no-build --no-deps --wait --wait-timeout 240 booking-app; then
            echo "Previous image restored: $previous" >&2
        else
            echo 'Rollback failed; manual intervention required' >&2
        fi
    else
        "${compose[@]}" stop booking-app
        echo 'First deployment failed; application stopped' >&2
    fi
    exit 1
fi
