import React from 'react';

interface Props {
  children: React.ReactNode;
}

interface State {
  error: Error | null;
  info: string;
}

/**
 * Last line of defence for the on-device WebView.
 *
 * A single thrown error anywhere in the tree used to unmount the whole app and
 * leave a blank screen — which reads to the user as "every button stopped
 * working". This boundary keeps the shell alive, shows what actually failed,
 * and offers a real way out (reload, or clear the saved session that caused it).
 */
export class AppErrorBoundary extends React.Component<Props, State> {
  state: State = { error: null, info: '' };

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    // Keep the first few frames; they are what actually matters on device.
    const frames = (info.componentStack || '')
      .split('\n')
      .map((l) => l.trim())
      .filter(Boolean)
      .slice(0, 4)
      .join(' › ');
    this.setState({ info: frames });
    try {
      const bridge = (window as unknown as { BrowserHubNative?: { log?: (m: string) => void } })
        .BrowserHubNative;
      bridge?.log?.(`[BrowserHub] render error: ${error.message} | ${frames}`);
    } catch {
      // Logging must never make the crash worse.
    }
  }

  private reload = () => {
    window.location.reload();
  };

  private resetSession = () => {
    // The most common cause of a hard render crash is a corrupt saved session.
    try {
      window.localStorage.removeItem('browserhub.prefs.v1');
    } catch {
      // ignore
    }
    window.location.reload();
  };

  render() {
    const { error, info } = this.state;
    if (!error) return this.props.children;

    return (
      <div
        className="h-full w-full flex flex-col items-center justify-center px-5 py-8 text-center"
        style={{ backgroundColor: '#000000' }}
      >
        <div
          className="w-full max-w-sm rounded-2xl border p-5 space-y-4"
          style={{ backgroundColor: '#050506', borderColor: 'rgba(244,63,94,0.35)' }}
        >
          <div className="text-[13px] font-bold text-rose-300">
            BrowserHub hit an error it could not recover from
          </div>
          <div className="text-[11px] leading-relaxed text-slate-400 break-words">
            {error.message || 'Unknown error'}
          </div>
          {info ? (
            <div className="text-[10px] leading-relaxed text-slate-500 break-words">{info}</div>
          ) : null}
          <div className="space-y-2 pt-1">
            <button
              type="button"
              onClick={this.reload}
              className="w-full py-2.5 rounded-xl bg-cyan-500/20 border border-cyan-400 text-white text-[12px] font-bold cursor-pointer"
            >
              Reload BrowserHub
            </button>
            <button
              type="button"
              onClick={this.resetSession}
              className="w-full py-2.5 rounded-xl bg-black border border-slate-700 text-slate-300 text-[12px] font-semibold cursor-pointer"
            >
              Reset saved session and reload
            </button>
          </div>
          <div className="text-[10px] leading-relaxed text-slate-500">
            Resetting clears the saved preferences and the AI answer cache. Your tabs and
            bookmarks are not deleted.
          </div>
        </div>
      </div>
    );
  }
}
