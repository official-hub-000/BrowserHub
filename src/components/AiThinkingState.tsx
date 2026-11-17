import React from 'react';
import { Sparkles, WifiOff } from 'lucide-react';

/**
 * Honest progress feedback for the AI drawer: tells the user which engine is being
 * tried and reassures them that a local answer follows if the live model is slow.
 */
export const AiThinkingState: React.FC<{ slow: boolean }> = ({ slow }) => (
  <div
    className="rounded-2xl p-3.5 border border-slate-800 space-y-1.5"
    style={{ backgroundColor: '#0b0b0e' }}
  >
    <div className="flex items-center gap-2 text-xs font-semibold text-cyan-300">
      <Sparkles size={14} className="animate-spin shrink-0" />
      <span>{slow ? 'Still waiting for the live model…' : 'Thinking…'}</span>
    </div>
    <p className="text-[11px] text-slate-400 flex items-start gap-1.5 leading-relaxed">
      <WifiOff size={11} className="mt-0.5 shrink-0 text-slate-500" />
      <span>
        {slow
          ? 'If the network is slow I will answer from the on-device engine instead — no key needed.'
          : 'Trying the zero-key live model first, then the on-device engine.'}
      </span>
    </p>
  </div>
);
