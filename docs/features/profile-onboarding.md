# Profile Onboarding

**Status:** Implemented and verified in the local Docker stack.

## Registration Boundary

OwlNest uses a two-stage registration flow:

1. Flutter opens the Keycloak Authorization Code + PKCE flow in the system browser. Keycloak collects email, password, password confirmation, first name, and last name. Adding `prompt=create` to the authorization request opens registration directly.
2. After Keycloak returns tokens, Flutter completes the OwlNest profile through an authenticated API request. The backend stores product-specific fields in PostgreSQL.

The Keycloak Admin Console and Account Console are not the application's registration entry point. Flutter must start the authorization request for the `owlnest-flutter` client and must never receive or forward the user's password.

## Data Ownership

Keycloak owns credentials, email verification, first name, and last name. OwlNest owns username, display name, bio, birth date, gender, and onboarding state.

Store `birthDate`, not age, because age becomes stale. Birth date and gender are optional in the first version. Gender values are `FEMALE`, `MALE`, `NON_BINARY`, `OTHER`, and `PREFER_NOT_TO_SAY`.

## API Contract

```http
PUT /api/v1/profile/me
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "username": "john.doe",
  "displayName": "John Doe",
  "bio": "Building OwlNest",
  "birthDate": "1995-04-20",
  "gender": "PREFER_NOT_TO_SAY"
}
```

`username` and `displayName` are required. Username is normalized to lowercase, must contain 3–50 letters, digits, dots, or underscores, and remains unique without regard to case. Bio is limited to 500 characters; birth date must be in the past.

Success returns the complete current profile with `onboardingCompleted: true`. Invalid input returns `400`, a username conflict returns `409`, and missing or invalid authentication returns `401`.

## Alternatives

- **All fields in Keycloak — rejected.** It centralizes the form but couples product data to the identity provider and encourages large or sensitive token claims.
- **Credentials entered in Flutter — rejected.** The app would handle passwords directly and make MFA, social login, and provider changes harder.
- **Hybrid browser registration plus in-app onboarding — chosen.** Keycloak remains the credential boundary while OwlNest controls its evolving social profile.
