/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}"
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Plus Jakarta Sans"', '-apple-system', 'BlinkMacSystemFont', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
      colors: {
        brand: {
          dark: '#0b0f19',
          sidebar: '#0f172a',
          panel: '#1e293b',
          card: 'rgba(30, 41, 59, 0.7)',
        }
      },
      backgroundImage: {
        'accent-gradient': 'linear-gradient(135deg, #6366f1 0%, #06b6d4 100%)',
        'accent-gradient-hover': 'linear-gradient(135deg, #4f46e5 0%, #0891b2 100%)',
        'user-bubble': 'linear-gradient(135deg, #1e1b4b 0%, #312e81 100%)',
      }
    },
  },
  plugins: [],
}
