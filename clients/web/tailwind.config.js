/** @type {import('tailwindcss').Config} */
// docs/design/colors.md, typography.md 토큰을 그대로 매핑.
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#EFF6FF', 100: '#DBEAFE', 200: '#BFDBFE',
          300: '#93C5FD', 400: '#60A5FA', 500: '#3B82F6',
          600: '#2563EB', 700: '#1D4ED8', 800: '#1E40AF', 900: '#1E3A8A',
        },
        accent: {
          300: '#FCD34D', 400: '#FB923C',
          500: '#F97316', 600: '#EA580C', 700: '#C2410C',
        },
        neutral: {
          50: '#FAFAFA', 100: '#F5F5F5', 200: '#E5E5E5',
          300: '#D4D4D4', 400: '#A3A3A3', 500: '#737373',
          600: '#525252', 700: '#404040', 800: '#262626', 900: '#171717',
        },
        success: { 100: '#DCFCE7', 500: '#22C55E' },
        warning: { 100: '#FEF3C7', 500: '#F59E0B' },
        error:   { 100: '#FEE2E2', 500: '#EF4444' },
      },
      fontFamily: {
        sans: ['Pretendard Variable', 'Pretendard', '-apple-system', 'BlinkMacSystemFont', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'monospace'],
      },
    },
  },
  plugins: [],
};
