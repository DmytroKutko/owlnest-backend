#!/usr/bin/env bash

set -Eeuo pipefail

readonly PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly -a COMPOSE=(docker compose --env-file .env --profile full-stack)

cd "$PROJECT_DIR"

for required_command in docker curl jq; do
    if ! command -v "$required_command" >/dev/null 2>&1; then
        echo "$required_command is not installed or is not available in PATH." >&2
        exit 1
    fi
done

if ! docker info >/dev/null 2>&1; then
    echo "Docker is not running. Start Docker Desktop and run this script again." >&2
    exit 1
fi

if [[ ! -f .env ]]; then
    echo "Missing .env. Copy .env.example to .env and provide local credentials." >&2
    exit 1
fi

echo "Validating Docker Compose configuration..."
"${COMPOSE[@]}" config --quiet

echo "Stopping the current OwlNest stack..."
"${COMPOSE[@]}" down --remove-orphans

echo "Rebuilding the backend image from the current sources..."
"${COMPOSE[@]}" build

echo "Starting PostgreSQL, Keycloak, and OwlNest Backend..."
"${COMPOSE[@]}" up -d --wait --wait-timeout 180

"$PROJECT_DIR/docker/keycloak/configure-local-realm.sh"

echo
"${COMPOSE[@]}" ps
echo
echo "OwlNest stack is ready:"
echo "  Backend:  http://$("${COMPOSE[@]}" port backend 8080)"
echo "  Keycloak: http://$("${COMPOSE[@]}" port keycloak 8080)"

"$PROJECT_DIR/API/generate-postman-environment.sh"
