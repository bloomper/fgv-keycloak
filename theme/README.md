# FGV theme

[Keycloakify][1] 11 project that builds the `FGV` login, account and email theme. The Dockerfile in the repository
root builds it into the image; see the [main README](../README.md) for how it fits together.

| Directory      | Contents                                                                         |
|----------------|----------------------------------------------------------------------------------|
| `src/login/`   | Login theme. `KcPage.tsx` picks the page, `Template.tsx` is the layout around it |
| `src/account/` | Single-page account theme, built on `@keycloakify/keycloak-account-ui`           |
| `src/email/`   | Plain Keycloak email theme that only replaces `html/template.ftl`                |
| `src/shared/`  | Components of `@keycloakify/keycloak-ui-shared` that this theme changes          |

Files from Keycloakify's packages that the theme changes are taken over with `npx keycloakify own` and committed.
`npm ci` copies in the rest (`keycloakify sync-extensions`).

```shell
npm ci
npm run storybook             # preview the login and registration pages on http://localhost:6006
npm run build-keycloak-theme  # build the theme jars into dist_keycloak/, needs Maven
```

The image uses `dist_keycloak/keycloak-theme-for-kc-all-other-versions.jar`.

[1]: https://docs.keycloakify.dev
