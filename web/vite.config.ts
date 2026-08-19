import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["assets/icon-192.png", "assets/logo_damsan_green.png"],
      manifest: false,
      workbox: {
        navigateFallback: "/index.html",
        globPatterns: ["**/*.{js,css,html,png,svg,woff2}"],
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/.*(?:googleapis|gstatic)\.com\//,
            handler: "NetworkFirst",
            options: { cacheName: "firebase-runtime", networkTimeoutSeconds: 5 }
          }
        ]
      }
    })
  ]
});
