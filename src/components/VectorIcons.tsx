import React from 'react';
import type { SearchEngineId } from '../types/browser';

interface EngineIconProps {
  id: SearchEngineId | string;
  size?: number;
  className?: string;
}

/**
 * Vector brand marks for every search engine.
 *
 * These are drawn as inline SVG rather than emoji so they render identically on
 * every Android WebView (emoji glyph coverage and colour fonts are inconsistent
 * across OEM ROMs) and can inherit sizing/alignment from the surrounding UI.
 */
export const SearchEngineIcon: React.FC<EngineIconProps> = ({ id, size = 16, className = '' }) => {
  const common = {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    className: `bh-engine-icon ${className}`.trim(),
    'aria-hidden': true as const,
    focusable: 'false' as const,
    style: { flexShrink: 0, display: 'inline-block', verticalAlign: 'middle' },
  };

  switch (id) {
    case 'google':
      return (
        <svg {...common}>
          <path
            fill="#4285F4"
            d="M23.5 12.27c0-.85-.08-1.67-.22-2.45H12v4.64h6.45a5.52 5.52 0 0 1-2.39 3.62v3h3.86c2.26-2.08 3.58-5.15 3.58-8.81z"
          />
          <path
            fill="#34A853"
            d="M12 24c3.24 0 5.96-1.08 7.94-2.92l-3.86-3c-1.07.72-2.44 1.15-4.08 1.15-3.13 0-5.78-2.11-6.73-4.96H1.28v3.09A12 12 0 0 0 12 24z"
          />
          <path
            fill="#FBBC05"
            d="M5.27 14.27a7.2 7.2 0 0 1 0-4.54V6.64H1.28a12 12 0 0 0 0 10.72l3.99-3.09z"
          />
          <path
            fill="#EA4335"
            d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0A12 12 0 0 0 1.28 6.64l3.99 3.09C6.22 6.88 8.87 4.75 12 4.75z"
          />
        </svg>
      );

    case 'duckduckgo':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#DE5833" />
          <path
            fill="#fff"
            d="M12.9 5.1c-2.5.1-4.4 2.2-4.4 4.7 0 .5.1 1 .2 1.4l-.9 2.3 1 .3.6 1.7 1.6.5.1 2.9 3.1.1.2-3.4 1.2-1.1.7-2.4c.4-.7.6-1.5.6-2.3 0-2.6-2.1-4.8-4.9-4.7z"
          />
          <circle cx="10.9" cy="9.6" r="1.15" fill="#DE5833" />
          <path
            fill="#DE5833"
            d="M9.9 12.4c.5 1 1.7 1.4 2.7 1.1l.5 1.5-1.7.4-1-1.4z"
          />
        </svg>
      );

    case 'brave':
      return (
        <svg {...common}>
          <path fill="#FB542B" d="M12 1.7 3 5.4l1.3 8.3L12 22.3l7.7-8.6 1.3-8.3z" />
          <path
            fill="#fff"
            d="M12 5.1 7.4 7.2l1 4.1 1.5 4 2.1 2.6 2.1-2.6 1.5-4 1-4.1z"
            opacity=".92"
          />
        </svg>
      );

    case 'bing':
      return (
        <svg {...common}>
          <path fill="#008373" d="M4.4 2v16.1l4.3 2.4 7.2-4.2-3.2-1.9-1.6.9V6.6z" />
          <path fill="#1FB3A3" d="M8.8 6.6v10.6l4.4 2.5 6-3.6-4.5-2.6-3.4 1.7V9.2z" />
        </svg>
      );

    case 'startpage':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#5A5FCF" />
          <path
            fill="#fff"
            d="M15.9 8.2h-5.3a1.5 1.5 0 0 0 0 3h2.9a4.2 4.2 0 1 1-4.2 4.2h3.2a1.5 1.5 0 0 0 0-3H9.1A4.2 4.2 0 1 1 13.3 17h2.6a7.1 7.1 0 1 0 0-8.8z"
          />
        </svg>
      );

    case 'wikipedia':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#1a1a1f" />
          <path
            fill="#e2e8f0"
            d="M4.4 7.3h2.5l2.2 7 2.1-7h1.7l2.1 7 2.2-7h2.4l-3.5 9.4h-2L12.2 10l-1.9 6.7h-2z"
          />
        </svg>
      );

    case 'github-live':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#000000" />
          <path
            fill="#e6edf3"
            d="M12 3.8a8.3 8.3 0 0 0-2.6 16.2c.4.1.6-.2.6-.4v-1.4c-2.3.5-2.8-1.1-2.8-1.1-.4-1-.9-1.2-.9-1.2-.8-.5 0-.5 0-.5.8.1 1.2.8 1.2.8.8 1.3 2 .9 2.5.7.1-.6.3-1 .6-1.2-1.9-.2-3.8-.9-3.8-4.1 0-.9.3-1.6.8-2.2-.1-.2-.4-1 .1-2.1 0 0 .7-.2 2.3.8a8 8 0 0 1 4.2 0c1.6-1 2.3-.8 2.3-.8.5 1.1.2 1.9.1 2.1.5.6.8 1.3.8 2.2 0 3.2-1.9 3.9-3.8 4.1.4.3.6.8.6 1.6v2.3c0 .2.2.5.6.4A8.3 8.3 0 0 0 12 3.8z"
          />
        </svg>
      );

    default:
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#334155" />
          <circle cx="10.5" cy="10.5" r="5.2" fill="none" stroke="#e2e8f0" strokeWidth="1.8" />
          <path d="m14.4 14.4 4 4" stroke="#e2e8f0" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      );
  }
};

/** Country / route badge drawn as vector so no emoji font is required. */
export const FlagIcon: React.FC<{ code: string; size?: number; className?: string }> = ({
  code,
  size = 18,
  className = '',
}) => {
  const flags: Record<string, { field: string; mark: string; ring?: string }> = {
    '🇨🇭': { field: '#d52b1e', mark: '✚' },
    '🇸🇬': { field: '#ed2939', mark: '☾' },
    '🇧🇩': { field: '#006a4e', mark: '●' },
    '🇮🇸': { field: '#02529c', mark: '✚' },
    '🇯🇵': { field: '#f7f7f7', mark: '●' },
    '🇩🇪': { field: '#1a1a1a', mark: '▬' },
    '🇺🇸': { field: '#3c3b6e', mark: '✦' },
    tor: { field: '#0f766e', mark: '◎' },
    '🧅': { field: '#4c1d95', mark: '◎' },
  };
  const conf = flags[code] || { field: '#334155', mark: '•' };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      className={`bh-flag-icon ${className}`.trim()}
      aria-hidden
      focusable="false"
      style={{ flexShrink: 0, display: 'inline-block', verticalAlign: 'middle' }}
    >
      <circle cx="12" cy="12" r="11" fill={conf.field} stroke="rgba(255,255,255,0.35)" strokeWidth="1.2" />
      <text
        x="12"
        y="16.4"
        textAnchor="middle"
        fontSize="11"
        fill={conf.field === '#f7f7f7' ? '#b91c1c' : '#ffffff'}
        fontFamily="system-ui, sans-serif"
      >
        {conf.mark}
      </text>
    </svg>
  );
};

/** Language badge: two-letter code in a coloured disc (no emoji font needed). */
export const LangBadge: React.FC<{ code: string; size?: number }> = ({ code, size = 24 }) => {
  const palette: Record<string, string> = {
    bn: '#0f766e', hi: '#b45309', es: '#b91c1c', fr: '#1d4ed8', de: '#374151',
    ja: '#be123c', zh: '#dc2626', ar: '#065f46', ru: '#1e40af', pt: '#15803d',
    ko: '#7c3aed', tr: '#c2410c', id: '#b91c1c', it: '#047857',
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      className="bh-lang-badge"
      aria-hidden
      focusable="false"
      style={{ flexShrink: 0, display: 'inline-block', verticalAlign: 'middle' }}
    >
      <circle cx="12" cy="12" r="11" fill={palette[code] || '#334155'} />
      <text
        x="12"
        y="16"
        textAnchor="middle"
        fontSize="10"
        fontWeight="700"
        fill="#ffffff"
        fontFamily="system-ui, sans-serif"
      >
        {code.toUpperCase()}
      </text>
    </svg>
  );
};

/** Speed-dial / extension marks that previously used emoji. */
export const SiteIcon: React.FC<EngineIconProps> = ({ id, size = 18, className = '' }) => {
  const common = {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    className: `bh-site-icon ${className}`.trim(),
    'aria-hidden': true as const,
    focusable: 'false' as const,
    style: { flexShrink: 0, display: 'inline-block', verticalAlign: 'middle' },
  };

  switch (id) {
    case 'google':
      return <SearchEngineIcon id="google" size={size} className={className} />;
    case 'duckduckgo':
      return <SearchEngineIcon id="duckduckgo" size={size} className={className} />;
    case 'youtube':
      return (
        <svg {...common}>
          <rect x="1.8" y="5" width="20.4" height="14" rx="4" fill="#FF0000" />
          <path fill="#fff" d="M10 8.8v6.4l5.4-3.2z" />
        </svg>
      );
    case 'wikipedia':
      return <SearchEngineIcon id="wikipedia" size={size} className={className} />;
    case 'github':
      return <SearchEngineIcon id="github-live" size={size} className={className} />;
    case 'reddit':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#FF4500" />
          <circle cx="8.8" cy="13.2" r="1.5" fill="#fff" />
          <circle cx="15.2" cy="13.2" r="1.5" fill="#fff" />
          <path
            d="M8.4 16.1a5.2 5.2 0 0 0 7.2 0"
            stroke="#fff"
            strokeWidth="1.5"
            strokeLinecap="round"
            fill="none"
          />
          <circle cx="17.2" cy="8.2" r="1.5" fill="#fff" />
          <path d="M11.6 7.3 12.6 4l3.3.8" stroke="#fff" strokeWidth="1.3" fill="none" />
        </svg>
      );
    case 'hackernews':
      return (
        <svg {...common}>
          <rect x="1.8" y="1.8" width="20.4" height="20.4" rx="3" fill="#FF6600" />
          <path
            d="M8.2 6.6h1.9l1.9 3.9 1.9-3.9h1.9l-2.9 6v3.8h-1.8v-3.8z"
            fill="#fff"
          />
        </svg>
      );
    case 'bbc':
      return (
        <svg {...common}>
          <rect x="1.8" y="1.8" width="8.6" height="8.6" fill="#BB1919" />
          <rect x="13.6" y="1.8" width="8.6" height="8.6" fill="#1a1a1f" />
          <rect x="1.8" y="13.6" width="8.6" height="8.6" fill="#1a1a1f" />
          <rect x="13.6" y="13.6" width="8.6" height="8.6" fill="#BB1919" />
        </svg>
      );
    case 'shield':
      return (
        <svg {...common}>
          <path d="M12 2.2 4 5.4v6.2c0 4.6 3.2 8.6 8 10.2 4.8-1.6 8-5.6 8-10.2V5.4z" fill="#10b981" />
          <path d="m8.6 12.1 2.3 2.3 4.5-4.6" stroke="#04231a" strokeWidth="1.9" fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case 'dark-mode':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#312e81" />
          <path d="M15.6 6.2a6.8 6.8 0 1 0 4.6 9.1 6.9 6.9 0 0 1-4.6-9.1z" fill="#c7d2fe" />
        </svg>
      );
    case 'cookie':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#b45309" />
          <circle cx="9" cy="9.4" r="1.4" fill="#fde68a" />
          <circle cx="15" cy="10.6" r="1.2" fill="#fde68a" />
          <circle cx="11.4" cy="15.2" r="1.5" fill="#fde68a" />
          <circle cx="16.2" cy="15" r="1" fill="#fde68a" />
        </svg>
      );
    case 'skip':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#0f766e" />
          <path d="M8 6.5v11l6-5.5z" fill="#fff" />
          <path d="M15.4 6.5h1.8v11h-1.8z" fill="#fff" />
        </svg>
      );
    case 'thumbs-down':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#334155" />
          <path
            d="M14.6 5.2h-5l-.8 5.3h2.1l-1 4.9 5.2-5.3h-2l1.3-4.9z"
            fill="#cbd5e1"
          />
        </svg>
      );
    case 'pip':
      return (
        <svg {...common}>
          <rect x="1.8" y="4.6" width="20.4" height="14.8" rx="2.6" fill="#0ea5e9" />
          <rect x="12.4" y="11.4" width="8" height="6.2" rx="1.2" fill="#f8fafc" />
        </svg>
      );
    case 'extension':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#7c3aed" />
          <path
            d="M9.2 6.6a1.7 1.7 0 1 1 3.4 0h2.2v2.2a1.7 1.7 0 1 1 2.4 2.4v4.4a1.2 1.2 0 0 1-1.2 1.2h-2.2a1.7 1.7 0 1 1-3.4 0H8.2a1.2 1.2 0 0 1-1.2-1.2v-2.2a1.7 1.7 0 1 1 0-3.4V7.8a1.2 1.2 0 0 1 1.2-1.2z"
            fill="#ede9fe"
          />
        </svg>
      );
    case 'css':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#0891b2" />
          <path d="M7 8h10l-1 10H8z" fill="#cffafe" />
          <path d="M9 10.5h6l-.3 2.2H10l.2 1.7h4.2l-.3 2.2H9.6" stroke="#0e7490" strokeWidth="1.1" fill="none" />
        </svg>
      );
    case 'userscript':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#ca8a04" />
          <path d="M13.4 5.4 7.8 13h3.4l-.8 5.6L16.2 11h-3.4z" fill="#fef9c3" />
        </svg>
      );
    case 'clear-urls':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#0f766e" />
          <path d="M7.4 9.2h9.2M7.4 12.4h6.2M7.4 15.6h8.4" stroke="#ccfbf1" strokeWidth="1.5" strokeLinecap="round" />
          <path d="m15.4 13.6 3.2 3.2m0-3.2-3.2 3.2" stroke="#fda4af" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      );
    case 'vault':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#1d4ed8" />
          <rect x="6.4" y="10.2" width="11.2" height="8.2" rx="2" fill="#dbeafe" />
          <path d="M9 10.2V8.6a3 3 0 0 1 6 0v1.6" stroke="#dbeafe" strokeWidth="1.7" fill="none" />
          <circle cx="12" cy="14" r="1.5" fill="#1d4ed8" />
        </svg>
      );
    case 'keyboard':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#475569" />
          <rect x="4.8" y="8" width="14.4" height="8.4" rx="1.8" fill="#e2e8f0" />
          <path d="M7 10.6h1.6M10.2 10.6h1.6M13.4 10.6h1.6M16.6 10.6H17M8.6 13.4h6.8" stroke="#334155" strokeWidth="1.2" strokeLinecap="round" />
        </svg>
      );
    case 'archive':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#0369a1" />
          <path d="M7 6.6h10v3.2H7z" fill="#e0f2fe" />
          <path d="M7.8 9.8h8.4v7.6H7.8z" fill="#bae6fd" />
          <path d="M10.6 12h2.8v1.4h-2.8z" fill="#0369a1" />
        </svg>
      );
    case 'target':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#be123c" />
          <circle cx="12" cy="12" r="6.8" fill="none" stroke="#ffe4e6" strokeWidth="1.6" />
          <circle cx="12" cy="12" r="3.4" fill="none" stroke="#ffe4e6" strokeWidth="1.6" />
          <circle cx="12" cy="12" r="1.3" fill="#ffe4e6" />
        </svg>
      );
    default:
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#334155" />
          <circle cx="12" cy="12" r="4.4" fill="none" stroke="#cbd5e1" strokeWidth="1.6" />
        </svg>
      );
  }
};
