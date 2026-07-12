#!/usr/bin/env bash

set -Eeuo pipefail

readonly PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly ENV_FILE="$PROJECT_DIR/.env"
readonly PASSWORD_POLICY="length(8) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and notUsername and notEmail"

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

readonly KEYCLOAK_URL="http://localhost:${KEYCLOAK_PORT:-8081}"

admin_token=$(curl -fsS -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=admin-cli' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode "username=$KEYCLOAK_ADMIN_USERNAME" \
    --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" \
    | jq -r '.access_token')

realm=$(curl -fsS "$KEYCLOAK_URL/admin/realms/owlnest" \
    -H "Authorization: Bearer $admin_token")

updated_realm=$(printf '%s' "$realm" | jq --arg passwordPolicy "$PASSWORD_POLICY" '
    .displayName = "OwlNest"
    | .loginTheme = "owlnest"
    | .registrationAllowed = true
    | .registrationEmailAsUsername = true
    | .loginWithEmailAllowed = true
    | .duplicateEmailsAllowed = false
    | .resetPasswordAllowed = true
    | .verifyEmail = false
    | .passwordPolicy = $passwordPolicy
')

curl -fsS -o /dev/null -X PUT "$KEYCLOAK_URL/admin/realms/owlnest" \
    -H "Authorization: Bearer $admin_token" \
    -H 'Content-Type: application/json' \
    --data "$updated_realm"

swagger_client=$(jq -n '
    {
        clientId: "owlnest-swagger",
        name: "OwlNest Swagger UI (Development Only)",
        enabled: true,
        protocol: "openid-connect",
        publicClient: true,
        standardFlowEnabled: true,
        directAccessGrantsEnabled: false,
        serviceAccountsEnabled: false,
        redirectUris: ["http://localhost:8080/swagger-ui/oauth2-redirect.html"],
        webOrigins: ["http://localhost:8080"],
        attributes: {
            "pkce.code.challenge.method": "S256"
        },
        protocolMappers: [
            {
                name: "owlnest-api-audience",
                protocol: "openid-connect",
                protocolMapper: "oidc-audience-mapper",
                consentRequired: false,
                config: {
                    "included.client.audience": "owlnest-api",
                    "id.token.claim": "false",
                    "access.token.claim": "true",
                    "introspection.token.claim": "true"
                }
            }
        ]
    }
')

swagger_client_id=$(curl -fsS "$KEYCLOAK_URL/admin/realms/owlnest/clients?clientId=owlnest-swagger" \
    -H "Authorization: Bearer $admin_token" \
    | jq -r '.[0].id // empty')

if [[ -z "$swagger_client_id" ]]; then
    curl -fsS -o /dev/null -X POST "$KEYCLOAK_URL/admin/realms/owlnest/clients" \
        -H "Authorization: Bearer $admin_token" \
        -H 'Content-Type: application/json' \
        --data "$swagger_client"
else
    curl -fsS -o /dev/null -X PUT "$KEYCLOAK_URL/admin/realms/owlnest/clients/$swagger_client_id" \
        -H "Authorization: Bearer $admin_token" \
        -H 'Content-Type: application/json' \
        --data "$swagger_client"
fi

echo "Synchronized local Keycloak self-registration and Swagger OAuth client settings."
