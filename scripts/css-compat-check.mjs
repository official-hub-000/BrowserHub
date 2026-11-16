import fs from 'node:fs';
import path from 'node:path';

const dir = 'dist/assets';
const cssFile = fs.readdirSync(dir).find((f) => f.endsWith('.css'));
const css = fs.readFileSync(path.join(dir, cssFile), 'utf8');
const forbidden = ['@layer', 'oklch', 'oklab', 'color-mix', 'in oklab', 'in lab', '@container', ':has('];
let fail = false;
for (const token of forbidden) {
  const count = css.split(token).length - 1;
  console.log((count === 0 ? 'OK   ' : 'FAIL ') + token.padEnd(14) + count);
  if (count > 0) fail = true;
}
const props = (css.match(/@property/g) || []).length;
console.log('info  @property     ' + props + ' (safe: unknown at-rules are ignored)');
process.exit(fail ? 1 : 0);
