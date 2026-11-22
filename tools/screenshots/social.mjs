// Renders site/social-preview.png (1280x640): the picture GitHub and chat
// apps show when someone shares a link to the game. Run after render.mjs.
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { execSync } from "node:child_process";
import { chromium } from "playwright-core";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const shot = readFileSync(join(ROOT, "site/screenshots/title.png")).toString("base64");
const chrome = process.env.CHROME || execSync("command -v chromium || command -v google-chrome", { encoding: "utf8", shell: "/bin/sh" }).trim();
const html = `<!doctype html><html><head><meta charset="utf-8"><style>
body { margin: 0; width: 1280px; height: 640px; overflow: hidden; font-family: "DejaVu Sans", system-ui, sans-serif;
  background: radial-gradient(700px 400px at 20% 0%, rgba(122,162,247,.22), transparent 70%), #0b0d14; color: #e6e9f5; }
.t { position: absolute; left: 72px; top: 120px; width: 600px; }
.k { font: 600 20px "DejaVu Sans Mono", monospace; letter-spacing: .12em; color: #9ece6a; text-transform: uppercase; }
h1 { font-size: 68px; line-height: 1.04; margin: 18px 0 22px; letter-spacing: -.02em; }
p { font-size: 27px; line-height: 1.4; color: #9aa3c2; margin: 0; }
p b { color: #e6e9f5; }
.pill { margin-top: 34px; display: inline-block; font: 600 20px "DejaVu Sans Mono", monospace; color: #10131c; background: #9ece6a; padding: 10px 18px; border-radius: 10px; }
img { position: absolute; right: -40px; top: 40px; height: 640px; }
</style></head><body>
<img src="data:image/png;base64,${shot}">
<div class="t"><div class="k">macOS &amp; Linux · free</div>
<h1>The Daemon's<br>Apprentice</h1>
<p><b>Learn the terminal by talking to your computer.</b> So you can read what AI agents do on it.</p>
<div class="pill">$ bash daemons-apprentice.sh</div></div>
</body></html>`;
const b = await chromium.launch({ executablePath: chrome });
const p = await b.newPage({ viewport: { width: 1280, height: 640 } });
await p.setContent(html);
await p.screenshot({ path: join(ROOT, "site/social-preview.png") });
await b.close();
console.log("wrote site/social-preview.png");
