#!/usr/bin/env bash

set -Eeuo pipefail

readonly PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ENV_FILE="$PROJECT_DIR/.env"
readonly OUTPUT_FILE="$PROJECT_DIR/API/OwlNest.local.postman_environment.json"

if ! command -v jq >/dev/null 2>&1; then
    echo "jq is required to generate the Postman environment." >&2
    exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
    echo "Missing .env. Copy .env.example to .env and provide local credentials." >&2
    exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${KEYCLOAK_ADMIN_USERNAME:?Set KEYCLOAK_ADMIN_USERNAME in .env}"
: "${KEYCLOAK_ADMIN_PASSWORD:?Set KEYCLOAK_ADMIN_PASSWORD in .env}"
: "${KEYCLOAK_TEST_USER_EMAIL:?Set KEYCLOAK_TEST_USER_EMAIL in .env}"
: "${KEYCLOAK_TEST_USER_PASSWORD:?Set KEYCLOAK_TEST_USER_PASSWORD in .env}"
: "${KEYCLOAK_TEST_USER_FIRST_NAME:?Set KEYCLOAK_TEST_USER_FIRST_NAME in .env}"
: "${KEYCLOAK_TEST_USER_LAST_NAME:?Set KEYCLOAK_TEST_USER_LAST_NAME in .env}"

jq -n \
    --arg baseUrl "http://localhost:${SERVER_PORT:-8080}" \
    --arg keycloakUrl "http://localhost:${KEYCLOAK_PORT:-8081}" \
    --arg adminUsername "$KEYCLOAK_ADMIN_USERNAME" \
    --arg adminPassword "$KEYCLOAK_ADMIN_PASSWORD" \
    --arg email "$KEYCLOAK_TEST_USER_EMAIL" \
    --arg password "$KEYCLOAK_TEST_USER_PASSWORD" \
    --arg firstName "$KEYCLOAK_TEST_USER_FIRST_NAME" \
    --arg lastName "$KEYCLOAK_TEST_USER_LAST_NAME" \
    '{
        "id": "8d05de47-b450-4a2e-bc27-32367e6f862d",
        "name": "OwlNest Local",
        "values": [
            {"key": "baseUrl", "value": $baseUrl, "type": "default", "enabled": true},
            {"key": "keycloakUrl", "value": $keycloakUrl, "type": "default", "enabled": true},
            {"key": "realm", "value": "owlnest", "type": "default", "enabled": true},
            {"key": "clientId", "value": "owlnest-postman", "type": "default", "enabled": true},
            {"key": "adminUsername", "value": $adminUsername, "type": "default", "enabled": true},
            {"key": "adminPassword", "value": $adminPassword, "type": "secret", "enabled": true},
            {"key": "email", "value": $email, "type": "default", "enabled": true},
            {"key": "password", "value": $password, "type": "secret", "enabled": true},
            {"key": "firstName", "value": $firstName, "type": "default", "enabled": true},
            {"key": "lastName", "value": $lastName, "type": "default", "enabled": true}
        ],
        "_postman_variable_scope": "environment",
        "_postman_exported_using": "OwlNest local environment generator"
    }' > "$OUTPUT_FILE"

chmod 600 "$OUTPUT_FILE"
echo "Generated ignored Postman environment: $OUTPUT_FILE"
