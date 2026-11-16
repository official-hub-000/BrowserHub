import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { createApiRouter } from './server/apiMiddleware';

function browserhubApiPlugin(): Plugin {
  const apiRouter = createApiRouter();
  return {
    name: 'browserhub-hybrid-api',
    configureServer(server) {
      server.middlewares.use('/api', apiRouter);
    },
    configurePreviewServer(server) {
      server.middlewares.use('/api', apiRouter);
    },
  };
}

/**
 * Exact CIE OKLCH -> sRGB converter + @layer unwrapper so 100% of Tailwind v4 colors,
 * backgrounds, borders, flex/grid utilities, and icons render identically on EVERY
 * Android System WebView (Android 8.0 through Android 16+).
 */
function oklchToCssRgb(lRaw: string, cRaw: string, hRaw: string, alphaRaw?: string): string {
  const L = lRaw.endsWith('%') ? parseFloat(lRaw) / 100 : parseFloat(lRaw);
  const C = parseFloat(cRaw);
  const H = (parseFloat(hRaw) * Math.PI) / 180;

  const a = C * Math.cos(H);
  const b = C * Math.sin(H);

  const l_ = L + 0.3963377774 * a + 0.2158037573 * b;
  const m_ = L - 0.1055613458 * a - 0.0638541728 * b;
  const s_ = L - 0.0894841775 * a - 1.291485548 * b;

  const l = l_ * l_ * l_;
  const m = m_ * m_ * m_;
  const s = s_ * s_ * s_;

  const rLin = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
  const gLin = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
  const bLin = -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s;

  const toSrgb = (v: number) => {
    const clamped = Math.max(0, Math.min(1, v));
    const gamma =
      clamped <= 0.0031308
        ? 12.92 * clamped
        : 1.055 * Math.pow(clamped, 1 / 2.4) - 0.055;
    return Math.round(Math.max(0, Math.min(255, gamma * 255)));
  };

  const R = toSrgb(rLin);
  const G = toSrgb(gLin);
  const B = toSrgb(bLin);

  if (alphaRaw !== undefined && alphaRaw.trim() !== '') {
    const aVal = alphaRaw.trim().endsWith('%')
      ? parseFloat(alphaRaw) / 100
      : parseFloat(alphaRaw);
    return `rgba(${R}, ${G}, ${B}, ${Number(aVal.toFixed(3))})`;
  }
  return `rgb(${R}, ${G}, ${B})`;
}

function androidWebViewCssCompatPlugin(): Plugin {
  /**
   * Removes an at-rule block completely (supports nested blocks).
   */
  function removeAtRuleBlock(css: string, atRuleStart: string): string {
    let out = css;
    for (;;) {
      const idx = out.indexOf(atRuleStart);
      if (idx === -1) break;
      let depth = 0;
      let i = idx;
      let end = out.length;
      while (i < out.length) {
        const ch = out[i];
        if (ch === '{') depth++;
        else if (ch === '}') {
          depth--;
          if (depth === 0) {
            end = i + 1;
            break;
          }
        }
        i++;
      }
      out = out.slice(0, idx) + out.slice(end);
    }
    return out;
  }

  function transformCssForAndroidWebView(css: string): string {
    // 1. Remove standalone @layer order statements
    let out = css.replace(/@layer\s+[^{;]+;/g, '');

    // 2. Drop every `@supports (color: color-mix(...))` progressive-enhancement block.
    //    Tailwind v4 always emits a static hex/rgba declaration immediately BEFORE each
    //    of these blocks, so deleting them silently falls back to the universal color.
    out = removeAtRuleBlock(out, '@supports (color:color-mix(in lab,red,red)){');
    out = removeAtRuleBlock(out, '@supports (color: color-mix(in lab, red, red)){');

    // 3. Container-query scaffolding is unsupported on older Android WebViews and is
    //    only used as an opt-in utility here, so drop it entirely.
    out = removeAtRuleBlock(out, '@container{container-type:inline-size}');

    // 4. Drop CSS Color 4 gradient interpolation methods — unsupported before Chrome 111.
    //    `linear-gradient(to right in oklab, …)` degrades to `linear-gradient(to right, …)`.
    out = out.replace(/\s+in\s+(oklab|oklch|lab|lch|srgb|srgb-linear|hsl|hwb)\b/gi, '');

    // 2. Unwrap `@layer name { ... }` blocks iteratively
    let changed = true;
    while (changed) {
      changed = false;
      const match = /@layer\s+[a-zA-Z0-9_.-]*\s*\{/.exec(out);
      if (match) {
        const startIdx = match.index;
        const braceOpenIdx = startIdx + match[0].length - 1;
        let depth = 1;
        let i = braceOpenIdx + 1;
        while (i < out.length && depth > 0) {
          if (out[i] === '{') depth++;
          else if (out[i] === '}') depth--;
          i++;
        }
        if (depth === 0) {
          const inner = out.slice(braceOpenIdx + 1, i - 1);
          out = out.slice(0, startIdx) + inner + out.slice(i);
          changed = true;
        }
      }
    }

    // 3. Convert all oklch(L C H / A) tokens to universal rgb()/rgba()
    out = out.replace(
      /oklch\(\s*([0-9.]+%?)\s+([0-9.]+)\s+([0-9.]+)(?:\s*\/\s*([0-9.]+%?))?\s*\)/gi,
      (_full, l, c, h, alpha) => oklchToCssRgb(l, c, h, alpha)
    );

    return out;
  }

  return {
    name: 'android-webview-css-compat',
    enforce: 'post',
    generateBundle(_options, bundle) {
      for (const file of Object.values(bundle)) {
        if (file.type === 'asset' && file.fileName.endsWith('.css') && typeof file.source === 'string') {
          file.source = transformCssForAndroidWebView(file.source);
        }
      }
    },
  };
}

export default defineConfig({
  base: './',
  plugins: [react(), tailwindcss(), browserhubApiPlugin(), androidWebViewCssCompatPlugin()],
  build: {
    target: 'es2020',
    cssTarget: 'chrome80',
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
  },
  preview: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
  },
});
