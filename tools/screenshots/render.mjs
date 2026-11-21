// Renders the README screenshots from real playthroughs (R23).
//
// The pictures are not mock-ups: tests/autoplay.sh plays the real game with
// TQ_KEEP_RAW=1, which keeps every byte the game wrote (colours included).
// This script cuts one screen out of such a recording, replays it in
// xterm.js (the terminal of VS Code) in a headless Chromium, and saves it
// as a PNG with a window frame.
//
//   cd tools/screenshots && npm install
//   TQ_KEEP_RAW=1 bash ../../tests/autoplay.sh full
//   node render.mjs                  # every shot in shots.json -> site/screenshots/
//   node render.mjs title            # only the shot named "title"
//
// Needs a Chromium or Chrome (CHROME=/path/to/chrome, default: found on PATH).

import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { execSync } from "node:child_process";
import { chromium } from "playwright-core";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, "../..");
const OUT = join(ROOT, "site", "screenshots");
const shots = JSON.parse(readFileSync(join(HERE, "shots.json"), "utf8"));
const only = process.argv.slice(2);

function findChrome() {
  if (process.env.CHROME) return process.env.CHROME;
  for (const c of ["chromium", "chromium-browser", "google-chrome", "google-chrome-stable"]) {
    try { return execSync(`command -v ${c}`, { encoding: "utf8" }).trim(); } catch { /* next */ }
  }
  const mac = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
  if (existsSync(mac)) return mac;
  throw new Error("no Chromium or Chrome found; set CHROME=/path/to/it");
}

// The screens of a recording: the game prints <<TQ-clear>> instead of
// clearing the screen in test mode, and <<TQ:id>> before every prompt.
function screens(file) {
  const raw = readFileSync(file, "utf8");
  return raw
    .split(/(?:\r?\n)?<<TQ-clear>>\r?\n/)
    .map((s) => s.replace(/(?:\r?\n)?<<TQ:[A-Za-z0-9_:]+>>\r?\n/g, "\r\n"));
}

const ANSI = /\x1b\[[0-9;?]*[A-Za-z]|\x1b[()][A-Z0-9]|\x1b[=>]/g;
// Plain text of a screen plus, for every plain character, its index in the
// raw screen, so a cut found in the text can be made in the raw bytes.
function plain(s) {
  let text = "", map = [], i = 0;
  for (const m of s.matchAll(ANSI)) {
    for (; i < m.index; i++) { text += s[i]; map.push(i); }
    i = m.index + m[0].length;
  }
  for (; i < s.length; i++) { text += s[i]; map.push(i); }
  map.push(s.length);
  return { text, map };
}

function pick(shot) {
  const file = join(ROOT, "tests", "out", shot.file);
  if (!existsSync(file)) throw new Error(`${shot.name}: ${file} missing (run TQ_KEEP_RAW=1 bash tests/autoplay.sh full)`);
  const all = screens(file);
  const hits = all.filter((s) => plain(s).text.includes(shot.screen));
  const scr = hits[shot.nth || 0];
  if (!scr) throw new Error(`${shot.name}: no screen contains ${JSON.stringify(shot.screen)}`);
  let out = scr;
  if (shot.until) {
    const { text, map } = plain(scr);
    let at = text.indexOf(shot.until);
    if (at < 0) throw new Error(`${shot.name}: ${JSON.stringify(shot.until)} not on the screen`);
    let end = text.indexOf("\n", at + shot.until.length);
    if (end < 0) end = text.length;
    if (shot.cutAtStart) end = at + shot.until.length;
    out = scr.slice(0, map[end]);
  }
  // Drop trailing empty lines, then show the cursor after the last text.
  return out.replace(/(\r?\n[ \t]*)+$/, "");
}

function page(shot, data) {
  const css = readFileSync(join(HERE, "node_modules/@xterm/xterm/css/xterm.css"), "utf8");
  const js = readFileSync(join(HERE, "node_modules/@xterm/xterm/lib/xterm.js"), "utf8");
  return `<!doctype html><html><head><meta charset="utf-8"><style>${css}
  html, body { margin: 0; background: transparent; }
  #frame { display: inline-block; padding: 28px; }
  .win { background: #1a1b26; border-radius: 12px; overflow: hidden;
         box-shadow: 0 18px 40px rgba(0,0,0,.35), 0 0 0 1px rgba(255,255,255,.08); }
  .bar { height: 34px; background: #24283b; display: flex; align-items: center;
         padding: 0 14px; gap: 8px; font: 13px -apple-system, "DejaVu Sans", sans-serif; color: #9aa5ce; }
  .dot { width: 12px; height: 12px; border-radius: 50%; }
  .bar .t { flex: 1; text-align: center; margin-right: 52px; }
  #term { padding: 14px 16px 16px; }
  .xterm .xterm-viewport { overflow: hidden !important; background: #1a1b26 !important; }
  </style></head><body><div id="frame"><div class="win">
  <div class="bar"><span class="dot" style="background:#ff5f57"></span><span class="dot" style="background:#febc2e"></span><span class="dot" style="background:#28c840"></span><span class="t">${shot.title || "Terminal"}</span></div>
  <div id="term"></div></div></div>
  <script>${js}</script>
  <script>
  const term = new Terminal({ cols: 80, rows: ${shot.rows || 30}, fontFamily: "DejaVu Sans Mono, Menlo, monospace",
    fontSize: 15, lineHeight: 1.12, cursorBlink: false, allowProposedApi: true,
    theme: { background: "#1a1b26", foreground: "#c0caf5", cursor: "#c0caf5",
      black: "#15161e", red: "#f7768e", green: "#9ece6a", yellow: "#e0af68", blue: "#7aa2f7",
      magenta: "#bb9af7", cyan: "#7dcfff", white: "#a9b1d6", brightBlack: "#565f89",
      brightRed: "#ff899d", brightGreen: "#9fe044", brightYellow: "#faba4a", brightBlue: "#8db0ff",
      brightMagenta: "#c7a9ff", brightCyan: "#a4daff", brightWhite: "#e6e9f5" } });
  term.open(document.getElementById("term"));
  term.write(${JSON.stringify(data)}, () => { window.done = true; });
  </script></body></html>`;
}

const browser = await chromium.launch({ executablePath: findChrome() });
const ctx = await browser.newContext({ deviceScaleFactor: 2, viewport: { width: 1000, height: 1000 } });
mkdirSync(OUT, { recursive: true });
for (const shot of shots) {
  if (only.length && !only.includes(shot.name)) continue;
  const data = pick(shot);
  const p = await ctx.newPage();
  await p.setContent(page(shot, data));
  await p.waitForFunction(() => window.done === true);
  await p.waitForTimeout(150);
  const el = await p.$("#frame");
  const file = join(OUT, `${shot.name}.png`);
  await el.screenshot({ path: file, omitBackground: true });
  await p.close();
  console.log(`wrote site/screenshots/${shot.name}.png`);
}
await browser.close();
