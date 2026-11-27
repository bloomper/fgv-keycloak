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

Registration, the emails around approval and the Cloudflare Turnstile challenge are not Keycloak's: they belong to
Medlemskap (`fgv-membership-*`), which creates users through the admin REST API. The login page's *Register* link
points to `MEMBERSHIP_URL/register` and Keycloak's own registration is switched off.

### Approval

A user is approved once an administrator has assigned them any role beyond the realm's default role, such as
`USER` on `fgv-spexregister` or on `fgv-revival`. Approval has nothing to do with FGV membership: members and
non-members alike get an account and are approved by being given access to an application. Until then
`fgv-approval` stops them from logging in to every client in the realm except the account console, and shows a
page saying the account is awaiting approval.

Medlemskap does the rest of approval: it puts new users in the group `pending-approval` (which stays in the realm),
takes them out when it gives them access, and sends the emails to administrators and users. Roles assigned in
Keycloak's admin console still approve a user, but send no email and leave the user in the group.

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
approval emails now come from Medlemskap.

## Realm

`realm/fgv.json` is imported when Keycloak starts with `--import-realm` and the realm `fgv` doesn't exist yet.
The client secrets in it are public, so replace them in any shared environment. [fgv-infra-deploy][2], which
runs this image, does that with `bin/configure-keycloak.sh`, which also selects the `FGV` theme. The realm contains no keys, so
Keycloak generates new ones for every environment. The realm contains no users apart from the service accounts
of `fgv-admin` and the applications' `*-service` clients. Spexregister Server and Nyuppsättning Server create sample users when they
import sample data.

`realm/fgv.json` is also used by the integration tests in Spexregister Server
(`src/integrationtest/resources/keycloak/fgv.json`), and in Nyuppsättning Server (refreshed with `./gradlew syncRealm`),
Spexipedia Server, Spexify Server, Spexflix Server, Spexgram Server and Spexarkiv Server
(`src/integrationtest/resources/realm/fgv.json`), so keep the copies identical.

The clients' names and addresses are read from the environment during the import:

| Variable               | Default                         | Purpose                                                          |
|------------------------|---------------------------------|------------------------------------------------------------------|
| `SPEXREGISTER_NAME`    | `Spexregister`                  | Name of `fgv-spexregister`, shown in the account console and on login |
| `SPEXREGISTER_URL`     | `http://localhost:3000`         | Address of Spexregister Web, without a trailing slash            |
| `SPEXREGISTER_MCP_URL` | `http://localhost:8080/api/mcp` | MCP endpoint, added as audience to tokens for `fgv-spexregister-mcp` |
| `MEMBERSHIP_NAME`      | `Medlemskap`                    | Name of `fgv-membership`, shown on login and in the approval email |
| `MEMBERSHIP_URL`       | `http://localhost:3007`         | Address of Medlemskap Web, without a trailing slash; also where the login page's *Register* link goes (set it in Keycloak's environment as well, as the theme reads it) |
| `REVIVAL_NAME`     | `Nyuppsättning`                 | Name of `fgv-revival`, shown on login and in the approval email  |
| `REVIVAL_URL`      | `http://localhost:3001`         | Address of Nyuppsättning Web, without a trailing slash           |
| `REVIVAL_MCP_URL`  | `http://localhost:8081/api/mcp` | MCP endpoint, added as audience to tokens for `fgv-revival-mcp`  |
| `SPEXIFY_NAME`         | `Spexify`                       | Name of `fgv-spexify`, shown on login and in the approval email  |
| `SPEXIFY_URL`          | `http://localhost:3002`         | Address of Spexify Web, without a trailing slash                 |
| `SPEXIFY_MCP_URL`      | `http://localhost:8082/api/mcp` | MCP endpoint, added as audience to tokens for `fgv-spexify-mcp`  |
| `SPEXFLIX_NAME`        | `Spexflix`                      | Name of `fgv-spexflix`, shown on login and in the approval email |
| `SPEXFLIX_URL`         | `http://localhost:3003`         | Address of Spexflix Web, without a trailing slash                |
| `SPEXFLIX_MCP_URL`     | `http://localhost:8083/api/mcp` | MCP endpoint, added as audience to tokens for `fgv-spexflix-mcp` |
| `SPEXGRAM_NAME`        | `Spexgram`                      | Name of `fgv-spexgram`, shown on login and in the approval email |
| `SPEXGRAM_URL`         | `http://localhost:3004`         | Address of Spexgram Web, without a trailing slash                |
| `SPEXGRAM_MCP_URL`     | `http://localhost:8085/api/mcp` | MCP endpoint, added as audience to tokens for `fgv-spexgram-mcp` |
| `ARCHIVE_NAME`         | `Spexarkiv`                   | Name of `fgv-archive`, shown on login and in the approval email  |
| `ARCHIVE_URL`          | `http://localhost:3005`         | Address of Spexarkiv Web, without a trailing slash             |
| `ARCHIVE_MCP_URL`      | `http://localhost:8087/api/mcp` | MCP endpoint, added as audience to tokens for `fgv-archive-mcp`  |
| `SPEXIPEDIA_NAME`      | `Spexipedia`                    | Name of `fgv-spexipedia`, shown on login and in the approval email |
| `SPEXIPEDIA_URL`       | `http://localhost:3006`         | Address of Spexipedia Web, without a trailing slash              |
| `SPEXIPEDIA_MCP_URL`   | `http://localhost:8088/api/mcp` | MCP endpoint, added as audience to tokens for `fgv-spexipedia-mcp` |

Only `SPEXREGISTER_URL/api/auth/callback/keycloak` is allowed as redirect URI, and the client requires PKCE. The
same holds for `fgv-revival` and `REVIVAL_URL`, `fgv-spexify` and `SPEXIFY_URL`, and `fgv-spexflix` and
`SPEXFLIX_URL`, `fgv-spexgram` and `SPEXGRAM_URL`, `fgv-archive` and `ARCHIVE_URL`, and `fgv-spexipedia` and
`SPEXIPEDIA_URL`.

Nyuppsättning has three clients:

| Client                | Type                                   | Roles and mappings                                         |
|-----------------------|----------------------------------------|------------------------------------------------------------|
| `fgv-revival`         | Confidential, for Nyuppsättning Web    | Client roles `USER` (access), `SUPER_ADMIN`, `OBSERVER`; scope mappings to the other web clients' `USER` |
| `fgv-revival-mcp`     | Public, for MCP clients, like `fgv-spexregister-mcp` | Scope mapping to all three `fgv-revival` roles    |
| `fgv-revival-service` | Confidential, service account only     | Service account roles and scope mappings `fgv-spexregister: REVIVAL`, `fgv-spexipedia: REVIVAL` |
| `fgv-revival-migration` | Confidential, service account only   | Service account roles and scope mappings `fgv-revival: USER, SUPER_ADMIN`, and a `sub` mapper; used by the throw-away `fgv-revival-migration`, which builds the guide's first draft. Disable it when the guide is in |

`REVIVAL` on `fgv-spexregister` is the narrow role Nyuppsättning's server uses to call Spexregister. It reads spex, tasks,
task categories and spexare, and writes what the sync of a settled revival records, but deletes nothing. Spexregister
Server's GraphQL API guide lists exactly what it may do. It is not an `authority` in Spexregister, so the service account is never imported as a
Spexregister user.

Spexify (music) and Spexflix (video) have three clients each:

| Client                 | Type                                | Roles and mappings                                                        |
|------------------------|-------------------------------------|---------------------------------------------------------------------------|
| `fgv-spexify`          | Confidential, for Spexify Web       | Client roles `USER` (access), `EDITOR`, `ADMIN`, and for other services' accounts `SPEXFLIX`, `SPEXIPEDIA`, `NOTIFY` and `IMPORT`; scope mappings to the other web clients' `USER` |
| `fgv-spexify-service`  | Confidential, service account only  | Service account roles and scope mappings `realm-management: view-users, view-clients`, `fgv-spexflix: SPEXIFY`, `fgv-spexipedia: SYNC`, `fgv-archive: SPEXIFY` |
| `fgv-spexify-mcp`     | Public, for MCP clients, like `fgv-revival-mcp` | Scope mapping to `fgv-spexify: USER, EDITOR, ADMIN`; read-only catalog tools |
| `fgv-spexflix`         | Confidential, for Spexflix Web      | Client roles `USER` (access), `EDITOR`, `ADMIN`, and for other services' accounts `SPEXIFY`, `SPEXIPEDIA` and `NOTIFY`; scope mappings to the other web clients' `USER` |
| `fgv-spexflix-service` | Confidential, service account only  | Service account roles and scope mappings `fgv-spexify: SPEXFLIX`, `fgv-spexipedia: SYNC` |
| `fgv-spexflix-mcp`    | Public, for MCP clients, like `fgv-revival-mcp` | Scope mapping to `fgv-spexflix: USER, EDITOR, ADMIN`; read-only video and playlist tools |

`EDITOR` and `ADMIN` are given in addition to `USER`. The two web clients have a `sub` mapper, since the realm has no `basic` scope and the servers identify users by their Keycloak id. Each web client's tokens carry the other web clients' `USER`
roles (`fgv-spexregister`, `fgv-revival`, `fgv-spexify`, `fgv-spexflix`, `fgv-spexgram` and `fgv-archive` are all mapped to
each other), so that a page and the application switcher link to another application only for users who can open it. Spexify's service account reads
users and clients, because Subsonic apps log in with app passwords, not Keycloak, and Spexify checks that their owners
still have `USER`.

Medlemskap (memberships and fees) has two clients:

| Client                   | Type                               | Roles and mappings                                                        |
|--------------------------|------------------------------------|---------------------------------------------------------------------------|
| `fgv-membership`         | Confidential, for Medlemskap Web   | Client roles `USER` (access; part of `default-roles-fgv`, like Spexipedia's), `BOARD`, `TREASURER`, `ADMIN`, and `SYNC` (Spexregister's service account) and `REVIVAL` (Nyuppsättning's); a `sub` mapper; scope mappings to the other web clients' `USER`, and theirs to Medlemskap's |
| `fgv-membership-service` | Confidential, service account only | Service account roles `fgv-spexregister: MEMBERSHIP` and `realm-management: manage-users, view-users, query-users, query-groups, view-clients, view-realm` |
| `fgv-external-payment-service` | Confidential, service account only | Service account role `fgv-membership: PAYMENT`, which lets an external application (a shop, say) tell Medlemskap (`POST /api/external/payments`) that someone paid |

`BOARD`, `TREASURER` and `ADMIN` are given in addition to `USER`, and each also gives the roles below it; Medlemskap's
user administration does that, since the roles are not composite.

Spexgram (photos and short videos) and Spexarkiv (manuscripts and sheet music) have three clients each:

| Client                 | Type                                | Roles and mappings                                                        |
|------------------------|-------------------------------------|---------------------------------------------------------------------------|
| `fgv-spexgram`         | Confidential, for Spexgram Web      | Client roles `USER` (access), `EDITOR`, `ADMIN`, and `SPEXIPEDIA` and `NOTIFY` for Spexipedia's service account; scope mappings to the other web clients' `USER` |
| `fgv-spexgram-service` | Confidential, service account only  | Service account roles and scope mappings `realm-management: view-users, view-clients`, `fgv-spexipedia: SYNC` |
| `fgv-spexgram-mcp`    | Public, for MCP clients, like `fgv-revival-mcp` | No scope mapping: its tools show only public albums, whatever the user's roles |
| `fgv-archive`          | Confidential, for Spexarkiv Web   | Client roles `USER` (access), `EDITOR`, `ADMIN`, and for other services' accounts `LINKS`, `NOTIFY` and `IMPORT` (Spexipedia) and `SPEXIFY` (Spexify); scope mappings to the other web clients' `USER` |
| `fgv-archive-service`  | Confidential, service account only  | Service account roles and scope mappings `realm-management: view-users, view-clients`, `fgv-spexipedia: SYNC` |
| `fgv-archive-mcp`     | Public, for MCP clients, like `fgv-revival-mcp` | Scope mapping to `fgv-archive: USER, EDITOR, ADMIN`, and a `sub` mapper; read-only search and material tools |

Spexipedia (the encyclopedia, and the source of base data: spex, revivals, categories, tasks, characters and numbers)
has three clients, and Spexregister gets a service account of its own for its copy of base data:

| Client                   | Type                                | Roles and mappings                                                      |
|--------------------------|-------------------------------------|-------------------------------------------------------------------------|
| `fgv-spexipedia`         | Confidential, for Spexipedia Web    | Client roles `USER` (access; a composite of `default-roles-fgv`, so every approved user has it), `AUTHOR`, `EDITOR`, `ADMIN`, and for other services' accounts `SYNC` (the change feed of base data), `REVIVAL` (creating revivals) and `IMPORT` (the import API); scope mappings to the other web clients' `USER` |
| `fgv-spexipedia-service` | Confidential, service account only  | Service account roles and scope mappings `fgv-spexregister: SPEXIPEDIA, NOTIFY`, `fgv-spexgram: SPEXIPEDIA, NOTIFY, IMPORT`, `fgv-spexify: SPEXIPEDIA, NOTIFY, IMPORT`, `fgv-spexflix: SPEXIPEDIA, NOTIFY, IMPORT`, `fgv-archive: LINKS, NOTIFY, IMPORT` |
| `fgv-spexipedia-mcp`     | Public, for MCP clients, like `fgv-revival-mcp` | Scope mapping to `fgv-spexipedia: USER, AUTHOR, EDITOR, ADMIN`; the base data tools that were Spexregister's |
| `fgv-spexregister-service`   | Confidential, service account only  | Service account role and scope mapping `fgv-spexipedia: SYNC`           |
| `fgv-content-migration`  | Confidential, service account only  | Service account roles and scope mappings `IMPORT` on `fgv-spexipedia`, `fgv-archive`, `fgv-spexify`, `fgv-spexflix` and `fgv-spexgram`, and `fgv-spexipedia: SYNC`; used by the throw-away `fgv-content-migration` |
| `fgv-spexregister-migration` | Confidential, service account only | Service account roles and scope mappings `IMPORT` on `fgv-spexregister` and `fgv-membership`, and a `sub` mapper; used by the throw-away `spexregister-migration`, which builds Spexregister's first database and Medlemskap's first FGV memberships |

`fgv-archive`, `fgv-spexify`, `fgv-spexflix` and `fgv-spexgram` also carry `IMPORT`, like `fgv-spexipedia`: the import
API that the migration and Spexipedia's spex packages use (Spexipedia's spec, `docs/content-import.md`).

`fgv-spexregister` also carries `fgv-spexipedia: EDITOR`, so that its spex pages link editors to Spexipedia.

Who calls whom, and with which role. Each role permits what one caller needs and nothing more, so that leaked service
credentials do little damage:

| Caller                   | Called         | Role                     | For                                                               |
|--------------------------|----------------|--------------------------|-------------------------------------------------------------------|
| `fgv-spexipedia-service` | Spexregister   | `fgv-spexregister: SPEXIPEDIA` | Base data for the switch; names and nicknames of published spexare on spex pages; picking credits |
| `fgv-spexipedia-service` | Spexgram       | `fgv-spexgram: SPEXIPEDIA` | Signed addresses of chosen photos, for logged-in users          |
| `fgv-spexipedia-service` | Spexify        | `fgv-spexify: SPEXIPEDIA`  | Published tracks and albums linked to numbers, recordings and spex |
| `fgv-spexipedia-service` | Spexflix       | `fgv-spexflix: SPEXIPEDIA` | Published videos of a spex                                     |
| `fgv-spexipedia-service` | Spexarkivet    | `fgv-archive: LINKS`     | Numbers with published arrangements, and manuscripts of a spex    |
| `fgv-spexipedia-service` | Every consumer | `NOTIFY` on its client   | `POST /api/sync/notify` after a change to base data               |
| `fgv-spexipedia-service` | Spexarkivet, Spexify, Spexflix, Spexgram | `IMPORT` on their clients | Applying spex packages                         |
| `fgv-content-migration`  | Spexipedia, Spexarkivet, Spexify, Spexflix, Spexgram | `IMPORT` on their clients, `fgv-spexipedia: SYNC` | The migration of FGV's file archive and the MediaWiki |
| `fgv-revival-migration`  | Nyuppsättning  | `fgv-revival: USER, SUPER_ADMIN` | The migration of the guide (Word) into Nyuppsättning's first draft; Nyuppsättning must list it in `API_SECURITY_AUTHORIZED_PARTIES` for the run |
| `fgv-spexregister-migration` | Spexregister, Medlemskap | `IMPORT` on `fgv-spexregister` and `fgv-membership` | The migration of Spexregister 1.x through their import APIs (`spexregister-spec` `docs/migration.md`); both must list it in `API_SECURITY_AUTHORIZED_PARTIES` for the run. Its Keycloak accounts are made with `fgv-admin`. Disable it when the migration is done |
| `fgv-spexregister-service`, `fgv-spexify-service`, `fgv-spexflix-service`, `fgv-spexgram-service`, `fgv-archive-service` | Spexipedia | `fgv-spexipedia: SYNC` | The change feed of base data (`baseDataChanges`) |
| `fgv-revival-service`    | Spexipedia     | `fgv-spexipedia: REVIVAL` | Creating revivals                                                 |
| `fgv-spexify-service`    | Spexarkivet    | `fgv-archive: SPEXIFY`   | *Sheet music in Spexarkivet* on tracks                            |
| `fgv-spexify-service`, `fgv-spexflix-service` | each other | `SPEXFLIX`, `SPEXIFY` | Chapter links between videos and tracks              |

Reading base data needs no role: Spexipedia's base data is public.

Their service accounts read users and clients, to find the email addresses of editors for notifications. Spexify and Spexflix also carry the
`USER` roles of Spexgram and Spexarkiv. Spexgram shows public albums without login; that needs nothing in Keycloak.

The realm also has:

- brute force protection, and a password policy of 12 to 128 characters that differ from the username and email
  address
- the public clients `fgv-spexregister-mcp`, `fgv-revival-mcp`, `fgv-spexipedia-mcp`, `fgv-spexify-mcp`,
  `fgv-spexflix-mcp`, `fgv-spexgram-mcp` and `fgv-archive-mcp` for MCP clients, with PKCE and redirect URIs on
  `localhost` and `127.0.0.1`
- a limit of 60 days on offline sessions
- passkeys, which users add under *Signing in* in the account console and then sign in with instead of a
  password. Signing in with a passkey skips OTP
- consent required for the MCP clients, so users see which MCP client asks for access
- registration switched off: users register in Medlemskap, which creates them in Keycloak with the service account
  `fgv-membership-service` (the roles `manage-users`, `view-users`, `query-users`, `query-groups`, `view-clients` and
  `view-realm` on `realm-management`, nothing more)
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

[2]: https://github.com/bloomper/fgv-infra-deploy

[3]: https://www.apache.org/licenses/LICENSE-2.0.html

