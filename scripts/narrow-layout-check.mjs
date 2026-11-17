// Static narrow-width audit: a fixed pixel width wider than a 360px phone
// (the narrowest Android screen BrowserHub supports) is a guaranteed overflow
// and shows up on device as clipped / overlapping chrome.
import fs from 'node:fs';
const files = ['src/App.tsx', ...fs.readdirSync('src/components').map(f => 'src/components/' + f)];
const bad = [];
for (const f of files) {
  const src = fs.readFileSync(f, 'utf8');
  src.split('\n').forEach((line, i) => {
    // fixed widths above 320px inside non-overlay chrome
    const m = /w-\[(\d+)px\]/.exec(line);
    if (m && Number(m[1]) > 320) bad.push(`${f}:${i + 1} w-[${m[1]}px]`);
    const minW = /min-w-\[(\d+)px\]/.exec(line);
    if (minW && Number(minW[1]) > 300) bad.push(`${f}:${i + 1} min-w-[${minW[1]}px]`);
  });
}
if (bad.length) { bad.forEach(b => console.error('  WIDE ' + b)); process.exit(1); }
console.log('  no fixed widths wider than a 360px viewport');
