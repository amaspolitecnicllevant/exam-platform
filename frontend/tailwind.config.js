/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50:  'hsl(var(--brand-h) var(--brand-s) 97%)',
          100: 'hsl(var(--brand-h) var(--brand-s) 93%)',
          200: 'hsl(var(--brand-h) var(--brand-s) 85%)',
          300: 'hsl(var(--brand-h) var(--brand-s) 73%)',
          400: 'hsl(var(--brand-h) var(--brand-s) 57%)',
          500: 'hsl(var(--brand-h) var(--brand-s) 44%)',
          600: 'hsl(var(--brand-h) var(--brand-s) 33%)',
          700: 'hsl(var(--brand-h) var(--brand-s) 20%)',
        }
      }
    }
  },
  plugins: []
}
