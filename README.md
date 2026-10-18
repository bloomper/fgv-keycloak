# FGV Keycloak

Keycloak image for FGV, published as `bloomper/fgv-keycloak`. It is the stock Keycloak image plus:

| Part      | Source                 | Location in the image                    |
|-----------|------------------------|------------------------------------------|
| Providers | `providers/`           | `/opt/keycloak/providers/`               |
| Theme     | `theme/`               | `/opt/keycloak/providers/`               |
| Realm     | `realm/fgv.json`       | `/opt/keycloak/data/import/fgv.json`     |

## Providers

| Provider id              | SPI             | Purpose                                                  |
|--------------------------|-----------------|----------------------------------------------------------|
| `legacy-fgv`             | Password hash   | Verifies passwords migrated from the previous register   |
| `fgv-approval`           | Required action | Holds new users until an administrator approves them     |
| `fgv-admin-notification` | Event listener  | Emails administrators and users during approval          |
| `fgv-turnstile`          | Form action     | Requires a Cloudflare Turnstile challenge on registration |

The event listener reads its recipients from
`KC_SPI_EVENTS_LISTENER__FGV_ADMIN_NOTIFICATION__RECIPIENTS`.

### Turnstile

`fgv-turnstile` reads its keys from `KC_SPI_FORM_ACTION__FGV_TURNSTILE__SITE_KEY` and
`KC_SPI_FORM_ACTION__FGV_TURNSTILE__SECRET_KEY`. Without them it logs a warning at start and lets every
registration through. It uses Turnstile's reCAPTCHA compatibility mode, so the theme renders the widget where it
would render reCAPTCHA, and failures show the message `recaptchaFailed`. Cloudflare's test site key
`1x00000000000000000000AA` with the secret key `1x0000000000000000000000000000000AA` always passes, and
`2x0000000000000000000000000000000AA` always fails.

### Approval

A user is approved once an administrator has assigned them any role beyond the realm's default role. Until
then `fgv-approval` stops them from logging in to every client in the realm except the account console, and
shows a page saying the account is awaiting approval.

`fgv-admin-notification` sends the emails around this, through the realm's SMTP settings:

| When                                             | Recipient                    | What happens                               |
|--------------------------------------------------|------------------------------|--------------------------------------------|
| A user registers (and verifies their email)      | The configured `recipients`  | The user joins the group `pending-approval` |
| An administrator assigns a pending user a role   | The user, in their language  | The user leaves the group                  |

Roles assigned in the admin console or through the admin REST API both count. Users who were never in the group,
such as migrated users, get no email. Set `pending-group` on both providers to use another group, e.g.
`KC_SPI_REQUIRED_ACTION__FGV_APPROVAL__PENDING_GROUP`.

Build the jar on its own with `./gradlew build` in `providers/`, which also runs the tests. It needs JDK 21 or
later and the jar ends up in `providers/build/libs/`.

## Theme

`theme/` is a [Keycloakify][1] project that provides the `FGV` login, account and email theme. Run `npm ci` in it,
then `npm run storybook` to preview pages or `npm run build-keycloak-theme` to build the theme jar. Building the
jar needs Maven. [`theme/README.md`](theme/README.md) describes the layout.

The account theme extends `keycloak.v3` instead of `base`, which `postBuild` in `vite.config.ts` sets because
Keycloakify can't. That gives it the authenticator icons Keycloak shows next to each passkey.

The email theme in `theme/src/email/` is a plain Keycloak theme that extends `base`. It replaces only
`html/template.ftl`, the layout around every HTML email, so the texts and translations are Keycloak's own. The
approval emails from `fgv-admin-notification` are plain text and don't use it.

## Realm

`realm/fgv.json` is imported when Keycloak starts with `--import-realm` and the realm `fgv` doesn't exist yet.
The client secrets in it are public, so replace them in any shared environment. [spexregister-deploy][2] does
that with `bin/configure-keycloak.sh`, which also selects the `FGV` theme. The realm contains no keys, so
Keycloak generates new ones for every environment. The realm contains no users apart from the service account
of `fgv-admin`. Spexregister Server creates sample users when it imports sample data.

`realm/fgv.json` is also used by the integration tests in Spexregister Server
(`src/integrationtest/resources/keycloak/fgv.json`), so keep the two copies identical.

The clients' names and addresses are read from the environment during the import:

| Variable               | Default                         | Purpose                                                          |
|------------------------|---------------------------------|------------------------------------------------------------------|
| `SPEXREGISTER_NAME`    | `Spexregister`                  | Name of `spexregister`, shown in the account console and on login |
| `SPEXREGISTER_URL`     | `http://localhost:3000`         | Address of Spexregister Web, without a trailing slash            |
| `SPEXREGISTER_MCP_URL` | `http://localhost:8080/api/mcp` | MCP endpoint, added as audience to tokens for `spexregister-mcp` |

Only `SPEXREGISTER_URL/api/auth/callback/keycloak` is allowed as redirect URI, and the client requires PKCE.

The realm also has:

- brute force protection, and a password policy of 12 to 128 characters that differ from the username and email
  address
- the public client `spexregister-mcp` for MCP clients, with PKCE and redirect URIs on `localhost` and `127.0.0.1`
- a limit of 60 days on offline sessions
- passkeys, which users add under *Signing in* in the account console and then sign in with instead of a
  password. Signing in with a passkey skips OTP
- consent required for `spexregister-mcp`, so users see which MCP client asks for access
- the registration flow `fgv registration`, a copy of the built-in one with `fgv-turnstile` added, and a content
  security policy that lets the Turnstile widget load
- the browser flow `fgv browser`, a copy of the built-in one that skips OTP when the user signed in with a passkey
- login and admin events, kept for 30 days

New users choose their password after verifying their email address, which is how Keycloak 26.8 handles
registration when email verification is on. The password policy applies only when a password is set, so users
migrated with shorter passwords can still sign in.

The realm's mail settings are read from the environment during that import:

| Variable        | Default             | Purpose                                   |
|-----------------|---------------------|-------------------------------------------|
| `SMTP_HOST`     |                     | SMTP server                               |
| `SMTP_PORT`     | `25`                | SMTP port                                 |
| `SMTP_FROM`     | `noreply@localhost` | Sender address                            |
| `SMTP_AUTH`     | `false`             | Log in with `SMTP_USER`/`SMTP_PASSWORD`   |
| `SMTP_USER`     |                     | SMTP user                                 |
| `SMTP_PASSWORD` |                     | SMTP password                             |
| `SMTP_STARTTLS` | `false`             | Upgrade the connection with STARTTLS      |
| `SMTP_SSL`      | `false`             | Connect with implicit TLS                 |

## Building the image

```shell
docker build -t fgv-keycloak .
```

The build needs only Docker. It compiles and tests the providers and builds the theme in throwaway stages. The
build argument `KEYCLOAK_VERSION` (default `26.8`) selects the tag of `quay.io/keycloak/keycloak` to extend. Keep
it on the same minor version as `keycloakVersion` in `providers/build.gradle`.

## Published images

CI publishes `bloomper/fgv-keycloak` for `linux/amd64` and `linux/arm64`:

| Trigger              | Tags                              |
|----------------------|-----------------------------------|
| push to `develop`    | `develop`, `sha-<commit>`         |
| push of tag `vX.Y.Z` | `X.Y.Z`, `latest`, `sha-<commit>` |

To release, merge to `main`, then `git tag vX.Y.Z && git push origin vX.Y.Z`. The tag is the version. The build
fails if `X.Y.Z` is already on Docker Hub.

## License

FGV Keycloak is open source software released under the [Apache 2.0 license][3].

[1]: https://keycloakify.dev

[2]: https://github.com/bloomper/spexregister-deploy

[3]: https://www.apache.org/licenses/LICENSE-2.0.html
