/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        pass:   "#22c55e",
        fail:   "#ef4444",
        ignore: "#f59e0b",
      },
    },
  },
  plugins: [],
}
