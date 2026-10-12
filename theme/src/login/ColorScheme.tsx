export type ColorSchemePreference = "light" | "dark" | "system";

const COOKIE_NAME = "kc_color_scheme";
const DEFAULT_PREF: ColorSchemePreference = "system";

function parsePref(raw: string | null): ColorSchemePreference | undefined {
    if (!raw) {
        return undefined;
    }

    const v = raw.toLowerCase();

    if (v === "light" || v === "dark" || v === "system") {
        return v;
    }
    return undefined;
}

function readCookie(name: string): string | null {
    const m = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));

    return m ? decodeURIComponent(m[1]) : null;
}

function writeCookie(name: string, value: string): void {
    // 1 year, theme-wide
    document.cookie = `${name}=${encodeURIComponent(value)}; Path=/; Max-Age=31536000; SameSite=Lax`;
}

function getPrefFromUrl(): ColorSchemePreference | undefined {
    const params = new URLSearchParams(window.location.search);

    const theme = parsePref(params.get("theme"));
    if (theme) {
        return theme;
    }

    const dark = params.get("dark");

    if (dark === "true") {
        return "dark";
    }
    if (dark === "false") {
        return "light";
    }

    return undefined;
}

export function getInitialPreference(): ColorSchemePreference {
    const fromUrl = getPrefFromUrl();

    if (fromUrl) {
        return fromUrl;
    }

    const fromCookie = parsePref(readCookie(COOKIE_NAME));

    if (fromCookie) {
        return fromCookie;
    }

    return DEFAULT_PREF;
}

export function persistPreference(pref: ColorSchemePreference): void {
    writeCookie(COOKIE_NAME, pref);
}

export function computeIsDark(pref: ColorSchemePreference): boolean {
    if (pref === "dark") {
        return true;
    }
    if (pref === "light") {
        return false;
    }

    return window.matchMedia?.("(prefers-color-scheme: dark)")?.matches ?? false;
}

export function applyToDom(pref: ColorSchemePreference): void {
    const isDark = computeIsDark(pref);

    document.documentElement.classList.toggle("dark", isDark);

    document.documentElement.style.colorScheme = isDark ? "dark" : "light";

    let meta = document.querySelector('meta[name="color-scheme"]') as HTMLMetaElement | null;

    if (!meta) {
        meta = document.createElement("meta");
        meta.name = "color-scheme";
        document.head.appendChild(meta);
    }
    meta.content = isDark ? "dark light" : "light dark";
}

export function initColorScheme(): () => void {
    const pref = getInitialPreference();

    persistPreference(pref);
    applyToDom(pref);

    if (pref !== "system" || !window.matchMedia) {
        return () => undefined;
    }

    const mql = window.matchMedia("(prefers-color-scheme: dark)");
    const handler = () => applyToDom("system");

    mql.addEventListener("change", handler);

    const legacy = mql as MediaQueryList & {
        addListener?: (listener: (this: MediaQueryList, ev: MediaQueryListEvent) => unknown) => void;
        removeListener?: (listener: (this: MediaQueryList, ev: MediaQueryListEvent) => unknown) => void;
    };

    if (typeof legacy.addListener === "function") {
        legacy.addListener(handler);
    }

    return () => {
        mql.removeEventListener("change", handler);

        if (typeof legacy.removeListener === "function") {
            legacy.removeListener(handler);
        }
    };
}
