import assert from "node:assert/strict";
import test from "node:test";

import {
  hashPassword,
  passwordNeedsRehash,
  verifyPassword
} from "../src/auth.js";
import { loadConfig } from "../src/config.js";

test("las contraseñas se almacenan con Argon2id y parámetros OWASP", async () => {
  const hash = await hashPassword("Una-clave-larga-y-unica-2026!");
  assert.match(hash, /^\$argon2id\$v=19\$/);
  assert.match(hash, /\$m=19456,p=1,t=2\$/);
  assert.equal(await verifyPassword(hash, "Una-clave-larga-y-unica-2026!"), true);
  assert.equal(await verifyPassword(hash, "incorrecta"), false);
  assert.equal(passwordNeedsRehash(hash), false);
});

test("la configuración rechaza secretos JWT débiles", () => {
  assert.throws(() => loadConfig({
    DATABASE_URL: "postgresql://test:test@localhost/test",
    JWT_SECRET: "demasiado-corto",
    PUBLIC_BASE_URL: "https://hotel.example",
    ADMIN_WEB_ORIGIN: "https://admin.hotel.example",
    SMTP_HOST: "smtp.example",
    SMTP_PORT: "587",
    MAIL_FROM: "reservas@hotel.example"
  }), /JWT_SECRET/);
});
