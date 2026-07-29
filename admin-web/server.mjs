import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { extname, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(fileURLToPath(new URL("./dist/", import.meta.url)));
const port = Number(process.env.PORT || 3000);
const contentTypes = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".webp": "image/webp",
  ".ico": "image/x-icon"
};

const server = createServer(async (request, response) => {
  const pathname = new URL(request.url || "/", "http://localhost").pathname;
  let relative = decodeURIComponent(pathname).replace(/^\/+/, "");
  if (!relative || !extname(relative)) relative = "index.html";
  const target = resolve(root, relative);
  if (target !== root && !target.startsWith(`${root}${sep}`)) {
    response.writeHead(400).end("Bad request");
    return;
  }
  try {
    if (!(await stat(target)).isFile()) throw new Error("not a file");
    const body = await readFile(target);
    response.writeHead(200, {
      "content-type": contentTypes[extname(target)] || "application/octet-stream",
      "cache-control": target.endsWith("index.html") ? "no-store" : "public, max-age=31536000, immutable",
      "content-security-policy": "default-src 'self'; connect-src 'self' http://localhost:8080 https:; img-src 'self' data: https:; style-src 'self' 'unsafe-inline'; script-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
      "cross-origin-opener-policy": "same-origin",
      "cross-origin-resource-policy": "same-origin",
      "permissions-policy": "camera=(), microphone=(), geolocation=(), payment=()",
      "referrer-policy": "no-referrer",
      "x-content-type-options": "nosniff",
      "x-frame-options": "DENY"
    });
    response.end(body);
  } catch {
    response.writeHead(404, { "content-type": "text/plain; charset=utf-8" }).end("Not found");
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Hotel Dumbría admin escuchando en ${port}`);
});
