/**
 * This file has been claimed for ownership from @keycloakify/keycloak-account-ui version 260700.0.3.
 * To relinquish ownership and restore this file to its original content, run the following command:
 *
 * $ npx keycloakify own --path "account/colorScheme.ts" --revert
 */

/* IMPORTANT NOTE:
 * If you modify how the light/dark mode is implemented
 * you must also update public/admin/early-color-scheme.js
 * This scrip is ran before this to avoid white flashes
 */

import {getKcContext} from "./KcContext";

const DARK_THEME_CLASS = "pf-v5-theme-dark";

function setIsDarkModeEnabled(isDarkModeEnabled: boolean) {
    {
        const elementId = "root-color-scheme-style";

        // Remove the style tag that might have been added by early-color-scheme.js
        document.getElementById(elementId)?.remove();

        const element = document.createElement("style");

        element.id = elementId;

        element.innerHTML = `:root { color-scheme: ${isDarkModeEnabled ? "dark" : "light"}; }`;

        document.head.appendChild(element);
    }

    // Remove the background color that might have been set by early-color-scheme.js
    // The stylesheet should have been loaded by now.
    document.documentElement.style.removeProperty("background-color");

    {
        const {classList} = document.documentElement;

        if (isDarkModeEnabled) {
            classList.add(DARK_THEME_CLASS);
        } else {
            classList.remove(DARK_THEME_CLASS);
        }
    }
}

function getCookie(name: string): string | undefined {
    const cookies = typeof document !== "undefined" ? document.cookie : "";

    if (!cookies) {
        return undefined;
    }

    for (const part of cookies.split(";")) {
        const [rawKey, ...rest] = part.trim().split("=");
        if (rawKey === name) {
            return decodeURIComponent(rest.join("="));
        }
    }
    return undefined;
}

type ColorSchemeCookie = "light" | "dark" | "system";

function readKcColorSchemeCookie(): ColorSchemeCookie | undefined {
    const v = getCookie("kc_color_scheme")?.toLowerCase();

    if (v === "light" || v === "dark" || v === "system") {
        return v;
    }

    return undefined;
}

export function startColorSchemeManagement() {
    const {kcContext} = getKcContext();

    // The "Dark Mode" realm configuration has been set to false
    // (Admin Console -> Realm Setting -> Themes -> Dark Mode)
    // This means that the admin don't want the UI to be render in dark mode
    // even when it's the user preference.
    if (kcContext.darkMode === false) {
        setIsDarkModeEnabled(false);
        return;
    }

    const cookieScheme = readKcColorSchemeCookie();

    // If Keycloak explicitly stores a user choice in the cookie, honor it.
    if (cookieScheme === "dark") {
        setIsDarkModeEnabled(true);
        return;
    }

    if (cookieScheme === "light") {
        setIsDarkModeEnabled(false);
        return;
    }

    const mediaQuery_isDarkThePreferredColorScheme = window.matchMedia(
        "(prefers-color-scheme: dark)"
    );

    setIsDarkModeEnabled(mediaQuery_isDarkThePreferredColorScheme.matches);

    mediaQuery_isDarkThePreferredColorScheme.addEventListener("change", event =>
        setIsDarkModeEnabled(event.matches)
    );
}
