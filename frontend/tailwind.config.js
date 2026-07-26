/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        poker: {
          dark: '#0f172a',
          card: '#1e293b',
          accent: '#10b981',
          gold: '#f59e0b',
          table: '#065f46',
        },
      },
    },
  },
  plugins: [],
};
