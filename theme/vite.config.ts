import {defineConfig} from "vite";
import react from "@vitejs/plugin-react";
import {keycloakify} from "keycloakify/vite-plugin";
import tailwindcss from "@tailwindcss/vite";
import {readFileSync, writeFileSync} from "node:fs";

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [
        react(),
        keycloakify({
            accountThemeImplementation: "Single-Page",
            themeName: "FGV",
            postBuild: async () => {
                const path = "theme/FGV/account/theme.properties";
                writeFileSync(path, readFileSync(path, "utf8").replace(/^parent=base$/m, "parent=keycloak.v3"));
            }
        }),
        tailwindcss(),
    ]
});
