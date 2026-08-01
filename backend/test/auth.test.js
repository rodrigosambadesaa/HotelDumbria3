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
    AUDIT_HMAC_SECRET: "otro-secreto-de-auditoria-suficientemente-largo",
    PUBLIC_BASE_URL: "https://hotel.example",
    ADMIN_WEB_ORIGIN: "https://admin.hotel.example",
    SMTP_HOST: "smtp.example",
    SMTP_PORT: "587",
    MAIL_FROM: "reservas@hotel.example"
  }), /JWT_SECRET/);
});

test("producción exige HTTPS, cookie Secure y proxies explícitos", () => {
  const base = {
    NODE_ENV: "production",
    DATABASE_URL: "postgresql://test:test@localhost/test",
    JWT_SECRET: "jwt-secreto-de-pruebas-con-mas-de-32-caracteres",
    AUDIT_HMAC_SECRET: "audit-secreto-de-pruebas-con-mas-de-32-caracteres",
    PUBLIC_BASE_URL: "https://api.hotel.example",
    ADMIN_WEB_ORIGIN: "https://admin.hotel.example",
    SMTP_HOST: "smtp.example",
    SMTP_PORT: "587",
    MAIL_FROM: "reservas@hotel.example",
    COOKIE_SECURE: "true"
  };
  assert.throws(() => loadConfig(base), /TRUSTED_PROXIES/);
  const config = loadConfig({ ...base, TRUSTED_PROXIES: "10.10.0.0/16" });
  assert.deepEqual(config.TRUSTED_PROXIES, ["10.10.0.0/16"]);
  assert.deepEqual(config.ADMIN_WEB_ORIGINS, ["https://admin.hotel.example"]);
  assert.throws(
    () => loadConfig({ ...base, TRUSTED_PROXIES: "proxy.example,10.0.0.0/99" }),
    /TRUSTED_PROXIES/
  );
});
