#!/usr/bin/env bash

set -Eeuo pipefail

readonly PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ENV_FILE="$PROJECT_DIR/.env"
readonly MANIFEST_FILE="$PROJECT_DIR/scripts/seed-data/community-demo-v1.json"
readonly STATE_DIR="$PROJECT_DIR/scripts/.local"
readonly INVENTORY_FILE="$STATE_DIR/community-demo-v1.inventory.json"
readonly MANIFEST_VERSION="community-demo-v1"

KEYCLOAK_USERS_CREATED=0
KEYCLOAK_USERS_REUSED=0
KEYCLOAK_CREDENTIALS_UPDATED=0
PROFILES_UPDATED=0
PROFILES_SKIPPED=0
POSTS_CREATED=0
POSTS_REUSED=0
LIKES_CREATED=0
LIKES_REUSED=0
COMMENTS_CREATED=0
COMMENTS_REUSED=0
CONFLICTS=0
FAILED=0
SUMMARY_PRINTED=false
LAST_STATUS=""
LAST_RESPONSE=""

print_summary() {
    if [[ "$SUMMARY_PRINTED" == "true" ]]; then
        return
    fi
    SUMMARY_PRINTED=true

    echo
    echo "Community demo seed summary ($MANIFEST_VERSION)"
    echo "  Keycloak users: created=$KEYCLOAK_USERS_CREATED reused=$KEYCLOAK_USERS_REUSED credentials_updated=$KEYCLOAK_CREDENTIALS_UPDATED"
    echo "  Profiles: updated=$PROFILES_UPDATED skipped=$PROFILES_SKIPPED"
    echo "  Posts: created=$POSTS_CREATED reused=$POSTS_REUSED expected=36"
    echo "  Likes: created=$LIKES_CREATED reused=$LIKES_REUSED expected=24"
    echo "  Comments: created=$COMMENTS_CREATED reused=$COMMENTS_REUSED expected=18"
    echo "  Conflicts=$CONFLICTS failed=$FAILED"
}

cleanup() {
    if [[ -n "${TEMP_DIR:-}" && -d "$TEMP_DIR" ]]; then
        rm -rf -- "$TEMP_DIR"
    fi
}

on_unexpected_error() {
    local exit_code=$?
    local line_number=$1
    trap - ERR
    set +e
    FAILED=$((FAILED + 1))
    echo "Unexpected seed failure at line $line_number." >&2
    print_summary >&2
    exit "$exit_code"
}

die() {
    FAILED=$((FAILED + 1))
    echo "$1" >&2
    print_summary >&2
    exit 1
}

die_conflict() {
    CONFLICTS=$((CONFLICTS + 1))
    die "$1"
}

trap cleanup EXIT
trap 'on_unexpected_error "$LINENO"' ERR

if [[ "${OWLNEST_LOCAL_SEED:-}" != "true" ]]; then
    die "Refusing to seed without the explicit OWLNEST_LOCAL_SEED=true opt-in."
fi

for required_command in curl docker jq sed awk mktemp chmod mv; do
    if ! command -v "$required_command" >/dev/null 2>&1; then
        die "$required_command is required for the local community seed."
    fi
done

if [[ ! -f "$ENV_FILE" ]]; then
    die "Missing .env. Copy .env.example to .env and provide local-only credentials."
fi

if [[ ! -f "$MANIFEST_FILE" ]]; then
    die "Missing seed manifest: $MANIFEST_FILE"
fi

chmod 600 "$ENV_FILE"

# shellcheck disable=SC1090
source "$ENV_FILE"

: "${POSTGRES_DB:?Set POSTGRES_DB in .env}"
: "${POSTGRES_USER:?Set POSTGRES_USER in .env}"
: "${KEYCLOAK_ADMIN_USERNAME:?Set KEYCLOAK_ADMIN_USERNAME in .env}"
: "${KEYCLOAK_ADMIN_PASSWORD:?Set KEYCLOAK_ADMIN_PASSWORD in .env}"
: "${KEYCLOAK_SEED_USER_PASSWORD:?Set KEYCLOAK_SEED_USER_PASSWORD in .env}"

if [[ "$KEYCLOAK_SEED_USER_PASSWORD" == replace-with-* ]]; then
    die "KEYCLOAK_SEED_USER_PASSWORD must not use the .env.example placeholder."
fi

if [[ ${#KEYCLOAK_SEED_USER_PASSWORD} -lt 12 \
    || ! "$KEYCLOAK_SEED_USER_PASSWORD" =~ [A-Z] \
    || ! "$KEYCLOAK_SEED_USER_PASSWORD" =~ [a-z] \
    || ! "$KEYCLOAK_SEED_USER_PASSWORD" =~ [0-9] \
    || ! "$KEYCLOAK_SEED_USER_PASSWORD" =~ [@%+=:,._-] \
    || "$KEYCLOAK_SEED_USER_PASSWORD" =~ [^A-Za-z0-9@%+=:,._-] ]]; then
    die "KEYCLOAK_SEED_USER_PASSWORD must be at least 12 characters and contain upper, lower, digit, and a safe special character."
fi

if [[ ! "${SERVER_PORT:-8080}" =~ ^[0-9]{1,5}$ || ! "${KEYCLOAK_PORT:-8081}" =~ ^[0-9]{1,5}$ ]]; then
    die "SERVER_PORT and KEYCLOAK_PORT must be numeric local TCP ports."
fi

readonly BACKEND_URL="http://localhost:${SERVER_PORT:-8080}"
readonly KEYCLOAK_URL="http://localhost:${KEYCLOAK_PORT:-8081}"
readonly REALM="owlnest"
readonly CLIENT_ID="owlnest-postman"
readonly -a COMPOSE=(
    docker compose
    --project-directory "$PROJECT_DIR"
    --file "$PROJECT_DIR/compose.yaml"
    --env-file "$ENV_FILE"
)

umask 077
mkdir -p "$STATE_DIR"
chmod 700 "$STATE_DIR"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/owlnest-community-seed.XXXXXX")"
readonly TEMP_DIR

jq -e --arg version "$MANIFEST_VERSION" '
    .version == $version
    and (.users | length) == 6
    and (.posts | length) == 36
    and (.likes | length) == 24
    and (.comments | length) == 18
    and ([.users[].key] | length == (unique | length))
    and ([.users[].email] | length == (unique | length))
    and ([.posts[].key] | length == (unique | length))
    and ([.comments[].key] | length == (unique | length))
    and (all(.users[]; (.key | test("^[a-z0-9-]+$")) and (.email | test("^[a-z0-9.]+@owlnest[.]com$"))))
    and ([.posts[].author] | group_by(.) | all(length == 6))
    and ([.likes[].actor] | group_by(.) | all(length == 4))
    and ([.comments[].actor] | group_by(.) | all(length == 3))
    and (all(.posts[]; (.labels | index("Community Post")) != null and (.media? == null)))
    and (([.users[].key] - ([.posts[].author] | unique)) | length == 0)
    and (([.users[].key] - ([.likes[].actor] | unique)) | length == 0)
    and (([.users[].key] - ([.comments[].actor] | unique)) | length == 0)
    and (([.likes[].post, .comments[].post] - [.posts[].key]) | length == 0)
    and (all(.likes[]; .actor != (.post | split("-")[0])))
    and (all(.comments[]; .actor != (.post | split("-")[0])))
    and ([.comments[].post] | unique | length) >= 12
' "$MANIFEST_FILE" >/dev/null || die "Seed manifest structure or exact cardinalities are invalid."

if [[ -f "$INVENTORY_FILE" ]]; then
    jq -e --arg version "$MANIFEST_VERSION" '.manifestVersion == $version' "$INVENTORY_FILE" >/dev/null \
        || die_conflict "Existing seed inventory has an unsupported manifest version."
else
    jq -n --arg version "$MANIFEST_VERSION" '
        {manifestVersion: $version, users: {}, posts: {}, comments: {}}
    ' > "$INVENTORY_FILE"
    chmod 600 "$INVENTORY_FILE"
fi

http_request() {
    local method=$1
    local url=$2
    local token_file=${3:-}
    local data_file=${4:-}
    local content_type=${5:-application/json}
    local request_id
    local config_file
    local token

    request_id="$(date +%s)-$RANDOM-$RANDOM"
    config_file="$TEMP_DIR/curl-$request_id.conf"
    LAST_RESPONSE="$TEMP_DIR/response-$request_id.json"

    {
        echo 'silent'
        echo 'show-error'
        echo 'no-location'
        echo 'noproxy = "*"'
        echo 'proto = "-all,http"'
        echo 'connect-timeout = 5'
        echo 'max-time = 30'
        echo "request = \"$method\""
        echo "url = \"$url\""
        echo 'header = "Accept: application/json, application/problem+json"'
        if [[ -n "$content_type" ]]; then
            echo "header = \"Content-Type: $content_type\""
        fi
        if [[ -n "$token_file" ]]; then
            token="$(<"$token_file")"
            echo "header = \"Authorization: Bearer $token\""
        fi
        if [[ -n "$data_file" ]]; then
            echo "data-binary = \"@$data_file\""
        fi
        echo "output = \"$LAST_RESPONSE\""
    } > "$config_file"
    chmod 600 "$config_file"

    if ! LAST_STATUS="$(curl --config "$config_file" --write-out '%{http_code}')"; then
        die "HTTP request failed before receiving a response: $method $url"
    fi
}

require_status() {
    local expected=$1
    local context=$2
    if [[ " $expected " != *" $LAST_STATUS "* ]]; then
        echo "Unexpected HTTP $LAST_STATUS while $context." >&2
        if jq -e . "$LAST_RESPONSE" >/dev/null 2>&1; then
            jq '{type, title, status, detail, code}' "$LAST_RESPONSE" >&2
        fi
        die "The local seed stopped without guessing or applying destructive repair."
    fi
}

urlencode() {
    jq -jRs @uri
}

write_form() {
    local output_file=$1
    shift
    : > "$output_file"
    while [[ $# -gt 0 ]]; do
        local key=$1
        local value=$2
        shift 2
        if [[ -s "$output_file" ]]; then
            printf '&' >> "$output_file"
        fi
        printf '%s=' "$key" >> "$output_file"
        printf '%s' "$value" | urlencode >> "$output_file"
    done
    chmod 600 "$output_file"
}

inventory_set() {
    local section=$1
    local key=$2
    local value=$3
    local next_inventory

    next_inventory="$(mktemp "$STATE_DIR/.inventory-next.XXXXXX")"
    jq --arg section "$section" --arg key "$key" --arg value "$value" \
        '.[$section][$key] = $value' "$INVENTORY_FILE" > "$next_inventory"
    chmod 600 "$next_inventory"
    mv "$next_inventory" "$INVENTORY_FILE"
}

database_query() {
    local query=$1
    shift
    "${COMPOSE[@]}" exec -T postgres psql \
        -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atq "$@" <<< "$query" \
        | sed '/^$/d'
}

line_count() {
    printf '%s\n' "$1" | awk 'NF { count++ } END { print count + 0 }'
}

readonly POST_RECONCILE_SQL="
SELECT p.id
FROM post p
JOIN identity_account account ON account.id = p.author_id
WHERE account.email = :'email'
  AND p.post_type = 'COMMUNITY'
  AND ((:'title' = '' AND p.title IS NULL) OR p.title = :'title')
  AND p.description = :'description'
  AND p.deleted_at IS NULL
ORDER BY p.id;
"

readonly COMMENT_RECONCILE_SQL="
SELECT comment.id
FROM post_comment comment
JOIN identity_account account ON account.id = comment.author_id
WHERE account.email = :'email'
  AND comment.post_id = :'post_id'::uuid
  AND comment.text_content = :'text'
ORDER BY comment.id;
"

readonly COUNTER_VERIFY_SQL="
SELECT COUNT(*)
FROM post p
WHERE p.id = :'post_id'::uuid
  AND p.comment_count = (
      SELECT COUNT(*) FROM post_comment comment WHERE comment.post_id = p.id
  );
"

if ! docker info >/dev/null 2>&1; then
    die "Docker is not running. Start the local OwlNest stack first."
fi

http_request GET "$KEYCLOAK_URL/realms/$REALM/.well-known/openid-configuration" "" "" ""
require_status "200" "checking the local Keycloak issuer"
jq -e --arg issuer "$KEYCLOAK_URL/realms/$REALM" '.issuer == $issuer' "$LAST_RESPONSE" >/dev/null \
    || die "Keycloak issuer does not match the fixed local OwlNest realm."

http_request GET "$BACKEND_URL/actuator/health" "" "" ""
require_status "200" "checking the local backend"
jq -e '.status == "UP"' "$LAST_RESPONSE" >/dev/null \
    || die "The local backend is not UP."

admin_form="$TEMP_DIR/admin-token.form"
write_form "$admin_form" \
    client_id admin-cli \
    grant_type password \
    username "$KEYCLOAK_ADMIN_USERNAME" \
    password "$KEYCLOAK_ADMIN_PASSWORD"
http_request POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" "" "$admin_form" "application/x-www-form-urlencoded"
require_status "200" "obtaining a local Keycloak admin token"
ADMIN_TOKEN_FILE="$TEMP_DIR/admin.token"
jq -er '.access_token | strings | select(length > 0)' "$LAST_RESPONSE" > "$ADMIN_TOKEN_FILE"
chmod 600 "$ADMIN_TOKEN_FILE"
readonly ADMIN_TOKEN_FILE

login_user() {
    local email=$1
    local token_file=$2
    local form_file="$TEMP_DIR/login-${email%@*}.form"

    write_form "$form_file" \
        client_id "$CLIENT_ID" \
        grant_type password \
        username "$email" \
        password "$KEYCLOAK_SEED_USER_PASSWORD"
    http_request POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" "" "$form_file" "application/x-www-form-urlencoded"
    if [[ "$LAST_STATUS" == "400" ]] \
        && jq -e '.error == "invalid_grant"' "$LAST_RESPONSE" >/dev/null 2>&1; then
        return 10
    fi
    require_status "200" "signing in marked seed user $email"
    jq -er '.access_token | strings | select(length > 0)' "$LAST_RESPONSE" > "$token_file"
    chmod 600 "$token_file"
}

reset_user_password() {
    local keycloak_user_id=$1
    local password_payload="$TEMP_DIR/reset-password.json"

    printf '%s' "$KEYCLOAK_SEED_USER_PASSWORD" \
        | jq -Rs '{type: "password", value: ., temporary: false}' > "$password_payload"
    chmod 600 "$password_payload"
    http_request PUT "$KEYCLOAK_URL/admin/realms/$REALM/users/$keycloak_user_id/reset-password" \
        "$ADMIN_TOKEN_FILE" "$password_payload"
    require_status "204" "synchronizing a marked seed user's local password"
    KEYCLOAK_CREDENTIALS_UPDATED=$((KEYCLOAK_CREDENTIALS_UPDATED + 1))
}

echo "Synchronizing six marked local Keycloak identities and OwlNest profiles..."
while IFS= read -r user; do
    key="$(jq -r '.key' <<< "$user")"
    email="$(jq -r '.email' <<< "$user")"
    username="$(jq -r '.username' <<< "$user")"
    display_name="$(jq -r '.displayName' <<< "$user")"
    first_name="$(jq -r '.firstName' <<< "$user")"
    last_name="$(jq -r '.lastName' <<< "$user")"
    bio="$(jq -r '.bio' <<< "$user")"
    encoded_email="$(printf '%s' "$email" | urlencode)"

    http_request GET "$KEYCLOAK_URL/admin/realms/$REALM/users?email=$encoded_email&exact=true" "$ADMIN_TOKEN_FILE" ""
    require_status "200" "looking up marked seed user $email"
    user_count="$(jq 'length' "$LAST_RESPONSE")"
    if [[ "$user_count" == "0" ]]; then
        user_payload="$TEMP_DIR/create-user-$key.json"
        jq --arg marker "$MANIFEST_VERSION" '
            {
                username: .email,
                email: .email,
                emailVerified: true,
                enabled: true,
                firstName: .firstName,
                lastName: .lastName,
                attributes: {owlnestSeed: [$marker]}
            }
        ' <<< "$user" > "$user_payload"
        http_request POST "$KEYCLOAK_URL/admin/realms/$REALM/users" "$ADMIN_TOKEN_FILE" "$user_payload"
        require_status "201" "creating marked seed user $email"
        KEYCLOAK_USERS_CREATED=$((KEYCLOAK_USERS_CREATED + 1))

        http_request GET "$KEYCLOAK_URL/admin/realms/$REALM/users?email=$encoded_email&exact=true" "$ADMIN_TOKEN_FILE" ""
        require_status "200" "resolving newly created seed user $email"
        user_count="$(jq 'length' "$LAST_RESPONSE")"
    else
        KEYCLOAK_USERS_REUSED=$((KEYCLOAK_USERS_REUSED + 1))
    fi

    if [[ "$user_count" != "1" ]]; then
        die_conflict "Expected one Keycloak user for $email, found $user_count."
    fi
    if ! jq -e --arg marker "$MANIFEST_VERSION" '.[0].attributes.owlnestSeed // [] | index($marker) != null' \
        "$LAST_RESPONSE" >/dev/null; then
        die_conflict "Refusing to adopt unmarked existing Keycloak user $email."
    fi
    keycloak_user_id="$(jq -er '.[0].id' "$LAST_RESPONSE")"
    token_file="$TEMP_DIR/token-$key"

    if login_user "$email" "$token_file"; then
        :
    else
        login_status=$?
        if [[ "$login_status" != "10" ]]; then
            die "Unexpected local login failure for marked seed user $email."
        fi
        reset_user_password "$keycloak_user_id"
        if ! login_user "$email" "$token_file"; then
            die "Unable to sign in marked seed user $email after a local password reset."
        fi
    fi

    http_request GET "$BACKEND_URL/api/v1/profile/me" "$token_file" ""
    require_status "200" "provisioning the OwlNest profile for $email"
    account_id="$(jq -er '.accountId' "$LAST_RESPONSE")"
    if jq -e \
        --arg username "$username" \
        --arg displayName "$display_name" \
        --arg bio "$bio" '
            .username == $username
            and .displayName == $displayName
            and .bio == $bio
            and .birthDate == null
            and .gender == null
            and .onboardingCompleted == true
        ' "$LAST_RESPONSE" >/dev/null; then
        PROFILES_SKIPPED=$((PROFILES_SKIPPED + 1))
    else
        profile_payload="$TEMP_DIR/profile-$key.json"
        jq -n \
            --arg username "$username" \
            --arg displayName "$display_name" \
            --arg bio "$bio" '
                {
                    username: $username,
                    displayName: $displayName,
                    bio: $bio,
                    birthDate: null,
                    gender: null
                }
            ' > "$profile_payload"
        http_request PUT "$BACKEND_URL/api/v1/profile/me" "$token_file" "$profile_payload"
        require_status "200" "completing the OwlNest profile for $email"
        account_id="$(jq -er '.accountId' "$LAST_RESPONSE")"
        PROFILES_UPDATED=$((PROFILES_UPDATED + 1))
    fi
    inventory_set users "$key" "$account_id"
done < <(jq -c '.users[]' "$MANIFEST_FILE")

echo "Reconciling 36 community posts through authenticated API writes..."
while IFS= read -r post; do
    key="$(jq -r '.key' <<< "$post")"
    author="$(jq -r '.author' <<< "$post")"
    title="$(jq -r '.title // ""' <<< "$post")"
    description="$(jq -r '.description' <<< "$post")"
    email="$(jq -r --arg author "$author" '.users[] | select(.key == $author) | .email' "$MANIFEST_FILE")"
    matches="$(database_query "$POST_RECONCILE_SQL" \
        -v "email=$email" -v "title=$title" -v "description=$description")"
    match_count="$(line_count "$matches")"

    if [[ "$match_count" == "0" ]]; then
        post_payload="$TEMP_DIR/post-$key.json"
        jq '{title, description, postType: "COMMUNITY", labels, media: []}' <<< "$post" > "$post_payload"
        http_request POST "$BACKEND_URL/api/v1/posts" "$TEMP_DIR/token-$author" "$post_payload"
        require_status "201" "creating community post $key"
        post_id="$(jq -er '.id' "$LAST_RESPONSE")"
        POSTS_CREATED=$((POSTS_CREATED + 1))
    elif [[ "$match_count" == "1" ]]; then
        post_id="$(printf '%s\n' "$matches" | sed -n '1p')"
        POSTS_REUSED=$((POSTS_REUSED + 1))
    else
        die_conflict "Post reconciliation for $key found $match_count matches; refusing to create another."
    fi
    inventory_set posts "$key" "$post_id"
done < <(jq -c '.posts[]' "$MANIFEST_FILE")

echo "Reconciling 24 cross-user likes through desired-state API writes..."
while IFS= read -r like; do
    actor="$(jq -r '.actor' <<< "$like")"
    post_key="$(jq -r '.post' <<< "$like")"
    post_id="$(jq -er --arg key "$post_key" '.posts[$key]' "$INVENTORY_FILE")"

    http_request GET "$BACKEND_URL/api/v1/posts/$post_id" "$TEMP_DIR/token-$actor" ""
    require_status "200" "checking like state for $actor on $post_key"
    if jq -e '.viewerState.liked == true' "$LAST_RESPONSE" >/dev/null; then
        LIKES_REUSED=$((LIKES_REUSED + 1))
    else
        http_request PUT "$BACKEND_URL/api/v1/posts/$post_id/likes" "$TEMP_DIR/token-$actor" ""
        require_status "204" "liking $post_key as $actor"
        LIKES_CREATED=$((LIKES_CREATED + 1))
    fi
done < <(jq -c '.likes[]' "$MANIFEST_FILE")

echo "Reconciling 18 cross-user comments through authenticated API writes..."
while IFS= read -r comment; do
    key="$(jq -r '.key' <<< "$comment")"
    actor="$(jq -r '.actor' <<< "$comment")"
    post_key="$(jq -r '.post' <<< "$comment")"
    text="$(jq -r '.text' <<< "$comment")"
    post_id="$(jq -er --arg key "$post_key" '.posts[$key]' "$INVENTORY_FILE")"
    email="$(jq -r --arg actor "$actor" '.users[] | select(.key == $actor) | .email' "$MANIFEST_FILE")"
    matches="$(database_query "$COMMENT_RECONCILE_SQL" \
        -v "email=$email" -v "post_id=$post_id" -v "text=$text")"
    match_count="$(line_count "$matches")"

    if [[ "$match_count" == "0" ]]; then
        comment_payload="$TEMP_DIR/comment-$key.json"
        jq '{text}' <<< "$comment" > "$comment_payload"
        http_request POST "$BACKEND_URL/api/v1/posts/$post_id/comments" "$TEMP_DIR/token-$actor" "$comment_payload"
        require_status "201" "commenting on $post_key as $actor"
        comment_id="$(jq -er '.id' "$LAST_RESPONSE")"
        COMMENTS_CREATED=$((COMMENTS_CREATED + 1))
    elif [[ "$match_count" == "1" ]]; then
        comment_id="$(printf '%s\n' "$matches" | sed -n '1p')"
        COMMENTS_REUSED=$((COMMENTS_REUSED + 1))
    else
        die_conflict "Comment reconciliation for $key found $match_count matches; refusing to create another."
    fi
    inventory_set comments "$key" "$comment_id"
done < <(jq -c '.comments[]' "$MANIFEST_FILE")

echo "Verifying stored comment counters for every managed post..."
while IFS= read -r post_key; do
    post_id="$(jq -er --arg key "$post_key" '.posts[$key]' "$INVENTORY_FILE")"
    counter_valid="$(database_query "$COUNTER_VERIFY_SQL" -v "post_id=$post_id")"
    if [[ "$counter_valid" != "1" ]]; then
        die_conflict "Stored comment counter is inconsistent for managed post $post_key."
    fi
done < <(jq -r '.posts[].key' "$MANIFEST_FILE")

if [[ $((POSTS_CREATED + POSTS_REUSED)) -ne 36 \
    || $((LIKES_CREATED + LIKES_REUSED)) -ne 24 \
    || $((COMMENTS_CREATED + COMMENTS_REUSED)) -ne 18 ]]; then
    die_conflict "Final managed-resource counts do not match the versioned manifest."
fi

print_summary
echo "The local community demo dataset is ready. Re-running the same manifest is safe and non-destructive."
