import {defineConfig} from "vite";
import react from "@vitejs/plugin-react";
import {keycloakify} from "keycloakify/vite-plugin";
import tailwindcss from "@tailwindcss/vite";

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [
        react(),
        keycloakify({
            accountThemeImplementation: "Single-Page",
            themeName: "FGV"
        }),
        tailwindcss(),
    ]
});
