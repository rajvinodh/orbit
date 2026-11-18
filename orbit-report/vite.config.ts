import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Vite injects type="module" crossorigin into script/link tags regardless of
// rollup output format. Both attributes break file:// access in Chrome, so strip them.
const stripModuleAttributes = {
  name: "strip-module-attributes",
  transformIndexHtml(html: string) {
    // Replace type="module" with defer — module scripts defer automatically but
    // Chrome blocks them on file://. Plain defer achieves the same: wait until
    // DOM is parsed so ReactDOM.createRoot('#root') finds the element.
    return html.replace(/\s+type="module"/g, " defer").replace(/\s+crossorigin/g, "");
  },
};

export default defineConfig({
  plugins: [react(), stripModuleAttributes],
  base: "./",   // relative paths so index.html works from disk or S3
  build: {
    rollupOptions: {
      output: {
        // IIFE avoids type="module" + crossorigin which Chrome blocks on file://
        format: "iife",
        name: "OrbitApp",
        entryFileNames: "main.js",
        chunkFileNames: "chunk-[hash].js",
        // All CSS → main.css; other assets keep original name
        assetFileNames: (info) => {
          const name = (info.names ?? [])[0] ?? '';
          return name.endsWith('.css') ? 'main.css' : '[name][extname]';
        },
      },
    },
  },
})
