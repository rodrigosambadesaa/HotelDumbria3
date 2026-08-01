import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("el portal identifica la administración y no persiste tokens en el navegador", async () => {
  const page = await readFile(new URL("../app/page.tsx", import.meta.url), "utf8");
  const index = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const server = await readFile(new URL("../server.mjs", import.meta.url), "utf8");
  assert.match(index, /Administración · Hotel Dumbría/);
  assert.match(page, /Panel de administración/);
  assert.match(page, /Gestión de reservas/);
  assert.match(page, /Registro de auditoría/);
  assert.doesNotMatch(page, /localStorage|sessionStorage/);
  assert.match(page, /\/auth\/web\/login/);
  assert.match(page, /\/auth\/web\/refresh/);
  assert.match(page, /\/auth\/web\/logout/);
  assert.doesNotMatch(page, /refreshToken/);
  assert.match(page, /refreshInFlight/);
  assert.match(page, /nextCursor/);
  assert.match(page, /Cargar más/);
  assert.match(server, /ADMIN_API_ORIGIN/);
  assert.doesNotMatch(server, /connect-src[^;]*https:/);
});
