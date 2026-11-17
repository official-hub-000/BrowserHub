/**
 * A real arithmetic engine, because "exact math" has to actually be exact.
 *
 * The old implementation handed the expression to `Function()`. That had two
 * failures the user hit directly:
 *   - "1270+23-10/80×18%100" failed because `×` (what every phone keyboard and
 *     every human writes) was not in the allowed character set, so the app
 *     claimed it could do math and then refused an arithmetic question;
 *   - it could not explain its own steps, so a combined expression was either
 *     a bare number or nothing at all.
 *
 * This module tokenises and parses the expression itself: no eval, no
 * `Function`, correct operator precedence, percentage handling that matches how
 * people actually mean it, and a worked step list.
 */

export interface MathResult {
  /** Formatted answer, e.g. "1290.75". */
  value: string;
  /** The expression as normalised for evaluation, e.g. "1270+23-10/80*18%100". */
  normalized: string;
  /** The same expression tidied for display, e.g. "18% of 2450". */
  display: string;
  /** Human-readable evaluation steps, in order. */
  steps: string[];
}

/** Symbols people type that are not ASCII operators. */
const SYMBOL_MAP: Record<string, string> = {
  '×': '*',
  '✕': '*',
  '✖': '*',
  '⨯': '*',
  '·': '*',
  '∗': '*',
  '÷': '/',
  '∕': '/',
  '⁄': '/',
  '−': '-',
  '–': '-',
  '—': '-',
  '％': '%',
  ',': '',
  ' ': '',
};

/** Words removed before evaluating ("solve", "what is", "please"). */
const PREFIX_RE =
  /^(?:please\s+)?(?:what\s+is|what's|whats|calculate|compute|solve|evaluate|work\s+out|how\s+much\s+is|answer|result\s+of)\s*/i;

function stripInstruction(raw: string): string {
  let s = raw.trim().replace(/[?=]+\s*$/, '').trim();
  // Strip a leading instruction, repeatedly ("solve please 2+2").
  let prev: string;
  do {
    prev = s;
    s = s.replace(PREFIX_RE, '').trim();
  } while (s !== prev && s.length > 0);
  return s;
}

function normalize(raw: string): string {
  let s = stripInstruction(raw);
  for (const [from, to] of Object.entries(SYMBOL_MAP)) {
    s = s.split(from).join(to);
  }
  return s;
}

type Token = { type: 'num'; value: number } | { type: 'op'; value: string } | { type: 'lp' } | { type: 'rp' };

function tokenize(input: string): Token[] | null {
  const tokens: Token[] = [];
  let i = 0;
  while (i < input.length) {
    const c = input[i];
    if (/\s/.test(c)) {
      i += 1;
      continue;
    }
    if (/[0-9.]/.test(c)) {
      let num = '';
      while (i < input.length && /[0-9.]/.test(input[i])) {
        num += input[i];
        i += 1;
      }
      if ((num.match(/\./g) || []).length > 1) return null;
      const value = Number(num);
      if (!Number.isFinite(value)) return null;
      tokens.push({ type: 'num', value });
      continue;
    }
    if ('+-*/%^'.includes(c)) {
      tokens.push({ type: 'op', value: c });
      i += 1;
      continue;
    }
    if (c === '(') {
      tokens.push({ type: 'lp' });
      i += 1;
      continue;
    }
    if (c === ')') {
      tokens.push({ type: 'rp' });
      i += 1;
      continue;
    }
    // Any other character means this is not pure arithmetic.
    return null;
  }
  return tokens;
}

/** Recursive-descent evaluator with standard precedence. */
class Parser {
  private pos = 0;
  private steps: string[] = [];

  constructor(private tokens: Token[]) {}

  parse(): { value: number; steps: string[] } {
    const value = this.expr();
    if (this.pos !== this.tokens.length) throw new Error('trailing tokens');
    return { value, steps: this.steps };
  }

  private peek(): Token | undefined {
    return this.tokens[this.pos];
  }

  private expr(): number {
    let left = this.term();
    for (;;) {
      const t = this.peek();
      if (t?.type === 'op' && (t.value === '+' || t.value === '-')) {
        this.pos += 1;
        const right = this.term();
        const next = t.value === '+' ? left + right : left - right;
        this.steps.push(`${fmt(left)} ${t.value} ${fmt(right)} = ${fmt(next)}`);
        left = next;
        continue;
      }
      break;
    }
    return left;
  }

  private term(): number {
    let left = this.unary();
    for (;;) {
      const t = this.peek();
      if (t?.type === 'op' && (t.value === '*' || t.value === '/' || t.value === '%')) {
        this.pos += 1;
        const right = this.unary();
        if ((t.value === '/' || t.value === '%') && right === 0) {
          throw new Error('division by zero');
        }
        let next: number;
        if (t.value === '*') next = left * right;
        else if (t.value === '/') next = left / right;
        else next = left % right;
        this.steps.push(
          `${fmt(left)} ${t.value} ${fmt(right)} = ${fmt(next)}`
        );
        left = next;
        continue;
      }
      // "18%100" is read as "18% of 100"; a trailing "%" means percent.
      break;
    }
    return left;
  }

  private unary(): number {
    const t = this.peek();
    if (t?.type === 'op' && (t.value === '-' || t.value === '+')) {
      this.pos += 1;
      const v = this.unary();
      return t.value === '-' ? -v : v;
    }
    return this.power();
  }

  private power(): number {
    const base = this.primary();
    const t = this.peek();
    if (t?.type === 'op' && t.value === '^') {
      this.pos += 1;
      const exp = this.unary();
      const next = base ** exp;
      this.steps.push(`${fmt(base)} ^ ${fmt(exp)} = ${fmt(next)}`);
      return next;
    }
    return base;
  }

  private primary(): number {
    const t = this.peek();
    if (!t) throw new Error('unexpected end');
    if (t.type === 'num') {
      this.pos += 1;
      // A number immediately followed by '%' and another operand means
      // "this percent of that number": 18%100 -> 18% of 100.
      const next = this.peek();
      if (next?.type === 'num') {
        this.pos += 1;
        const pct = t.value / 100;
        const result = pct * next.value;
        this.steps.push(`${fmt(t.value)}% of ${fmt(next.value)} = ${fmt(result)}`);
        return result;
      }
      return t.value;
    }
    if (t.type === 'lp') {
      this.pos += 1;
      const v = this.expr();
      const close = this.peek();
      if (close?.type !== 'rp') throw new Error('missing )');
      this.pos += 1;
      return v;
    }
    throw new Error('unexpected token');
  }
}

/** Trims float noise without turning integers into "4.00000001". */
function fmt(n: number): string {
  if (!Number.isFinite(n)) return String(n);
  const rounded = Number(n.toFixed(10));
  return String(rounded);
}

/**
 * Evaluates a pure arithmetic expression. Returns null when the input is not
 * arithmetic at all, so callers can fall through to a normal answer.
 */
export function evaluateArithmetic(raw: string): MathResult | null {
  const normalized = normalize(raw);
  if (!normalized) return null;
  // Must contain a digit and at least one operator to count as arithmetic.
  if (!/[0-9]/.test(normalized)) return null;

  // "18% of 2450" is the phrasing people use; handle it before tokenizing.
  const pctOf = /^(\d+(?:\.\d+)?)%\s*of\s*(\d+(?:\.\d+)?)$/i.exec(normalized);
  if (pctOf) {
    const pct = Number(pctOf[1]);
    const whole = Number(pctOf[2]);
    const value = (pct / 100) * whole;
    return {
      value: fmt(value),
      normalized,
      display: `${pctOf[1]}% of ${pctOf[2]}`,
      steps: [`${pctOf[1]}% of ${pctOf[2]} = ${fmt(value)}`],
    };
  }

  const barePercent = /^(\d+(?:\.\d+)?)%$/.exec(normalized);
  if (barePercent) {
    const value = Number(barePercent[1]) / 100;
    return {
      value: fmt(value),
      normalized,
      display: `${barePercent[1]}%`,
      steps: [`${barePercent[1]}% = ${fmt(value)}`],
    };
  }

  const tokens = tokenize(normalized);
  if (!tokens || tokens.length < 3) return null;
  // Require a real operator so "1234" is not treated as a calculation.
  if (!tokens.some((t) => t.type === 'op')) return null;

  try {
    const { value, steps } = new Parser(tokens).parse();
    if (!Number.isFinite(value)) return null;
    return {
      value: fmt(value),
      normalized,
      // Build the display from the normalised form, so `×`/`÷` are shown as
      // the `*`/`/` that was actually evaluated — the working must match.
      display: normalized.replace(/\s+/g, ' ').replace(/\s*([+\-*/^])\s*/g, ' $1 ').trim(),
      steps,
    };
  } catch {
    return null;
  }
}
