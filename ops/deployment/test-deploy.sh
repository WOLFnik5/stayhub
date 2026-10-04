#!/usr/bin/env bash
# Exercise deployment control flow without a registry or real Docker daemon.
set -Eeuo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
workspace=$(mktemp -d)
trap 'rm -rf -- "$workspace"' EXIT
mkdir "$workspace/bin" "$workspace/state"
touch "$workspace/state/.env"
export TEST_LOG="$workspace/docker.log"
export PATH="$workspace/bin:$PATH"
export OLD_IMAGE="ghcr.io/test/stayhub@sha256:$(printf 'b%.0s' {1..64})"
new_image="ghcr.io/test/stayhub@sha256:$(printf 'a%.0s' {1..64})"
cat > "$workspace/bin/docker" <<'STUB'
#!/usr/bin/env bash
set -eu
printf '%s %s\n' "${STAYHUB_IMAGE:-}" "$*" >> "$TEST_LOG"
if [[ "$1" == inspect ]]; then echo "$OLD_IMAGE"; exit 0; fi
if [[ "$*" == *'ps -q booking-app'* ]]; then
    if [[ "$SCENARIO" != first ]]; then echo existing-container; fi
fi
if [[ "$*" == *'pull booking-app'* && "$SCENARIO" == pull ]]; then exit 1; fi
if [[ "$*" == *'up -d'* && "$SCENARIO" != success && "${STAYHUB_IMAGE:-}" != "$OLD_IMAGE" ]]; then exit 1; fi
STUB
chmod +x "$workspace/bin/docker"

for scenario in success unhealthy first pull; do
    export SCENARIO="$scenario"
    : > "$TEST_LOG"
    rm -f -- "$workspace/state/current-image"
    if bash "$root/ops/deployment/deploy.sh" "$new_image" "$workspace/state" > "$workspace/output" 2>&1; then
        [[ "$scenario" == success ]]
        [[ $(cat "$workspace/state/current-image") == "$new_image" ]]
    else
        [[ "$scenario" != success ]]
        [[ ! -f "$workspace/state/current-image" ]]
    fi
    case "$scenario" in
        unhealthy) grep -q "$OLD_IMAGE.*up -d.*--no-deps" "$TEST_LOG" ;;
        first) grep -q 'stop booking-app' "$TEST_LOG" ;;
        pull) ! grep -q 'up -d' "$TEST_LOG" ;;
    esac
    echo "PASS: $scenario"
done
: > "$TEST_LOG"
if bash "$root/ops/deployment/deploy.sh" 'ghcr.io/test/stayhub:latest' "$workspace/state" > /dev/null 2>&1; then exit 1; fi
[[ ! -s "$TEST_LOG" ]]
echo 'PASS: mutable tag rejected before Docker'
exec 8>"$workspace/state/deploy.lock"
flock -n 8
if bash "$root/ops/deployment/deploy.sh" "$new_image" "$workspace/state" > /dev/null 2>&1; then exit 1; fi
[[ ! -s "$TEST_LOG" ]]
echo 'PASS: concurrent deployment rejected'
