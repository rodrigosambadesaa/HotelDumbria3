import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("el portal identifica la administración y no persiste tokens en el navegador", async () => {
  const page = await readFile(new URL("../app/page.tsx", import.meta.url), "utf8");
  const index = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.match(index, /Administración · Hotel Dumbría/);
  assert.match(page, /Panel de administración/);
  assert.match(page, /Gestión de reservas/);
  assert.match(page, /Registro de auditoría/);
  assert.doesNotMatch(page, /localStorage|sessionStorage/);
});
