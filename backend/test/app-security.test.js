import assert from "node:assert/strict";
import test from "node:test";

import { buildApp } from "../src/app.js";
import { loadConfig } from "../src/config.js";

function testConfig(overrides = {}) {
  return loadConfig({
    NODE_ENV: "test",
    DATABASE_URL: "postgresql://test:test@localhost/test",
    JWT_SECRET: "jwt-secreto-de-pruebas-con-mas-de-32-caracteres",
    AUDIT_HMAC_SECRET: "audit-secreto-de-pruebas-con-mas-de-32-caracteres",
    PUBLIC_BASE_URL: "https://api.hotel.example",
    ADMIN_WEB_ORIGIN: "https://admin.hotel.example",
    SMTP_HOST: "smtp.example",
    SMTP_PORT: "587",
    MAIL_FROM: "reservas@hotel.example",
    LOG_LEVEL: "fatal",
    ...overrides
  });
}

async function withApp(operation, overrides = {}) {
  const db = {
    async query(text) {
      if (text === "SELECT 1") return { rowCount: 1, rows: [{ '?column?': 1 }] };
      throw new Error(`Consulta inesperada en test: ${text}`);
    },
    async transaction(callback) {
      return callback(this);
    }
  };
  const app = await buildApp({ config: testConfig(overrides), db, mailer: {} });
  try {
    await operation(app);
  } finally {
    await app.close();
  }
}

test("X-Request-ID solo conserva UUID válidos y la respuesta 404 es uniforme", async () => {
  await withApp(async (app) => {
    const invalid = await app.inject({ method: "GET", url: "/missing", headers: { "x-request-id": "log-forging" } });
    assert.equal(invalid.statusCode, 404);
    assert.notEqual(invalid.json().requestId, "log-forging");
    assert.match(invalid.json().requestId, /^[0-9a-f-]{36}$/);

    const requestId = "7c3e9bd0-bf3d-4cb7-94a8-3ebd1f26d173";
    const valid = await app.inject({ method: "GET", url: "/missing", headers: { "x-request-id": requestId } });
    assert.equal(valid.json().requestId, requestId);
  });
});

test("la configuración de logging redacta tokens de URL, cuerpo y cabeceras", async () => {
  const source = await import("node:fs/promises").then(({ readFile }) =>
    readFile(new URL("../src/app.js", import.meta.url), "utf8")
  );
  assert.match(source, /\(\[\?&\]token=\)/);
  assert.match(source, /req\.body\.currentPassword/);
  assert.match(source, /x-csrf-token/);
});

test("un cliente directo no puede falsificar su IP mediante X-Forwarded-For", async () => {
  await withApp(async (app) => {
    let last;
    for (let index = 0; index < 121; index += 1) {
      last = await app.inject({
        method: "GET",
        url: "/health",
        remoteAddress: "198.51.100.90",
        headers: { "x-forwarded-for": `203.0.113.${index % 250}` }
      });
    }
    assert.equal(last.statusCode, 429);
  });
});

test("el refresh web exige cookie, coincidencia CSRF y origen permitido", async () => {
  await withApp(async (app) => {
    const noCookie = await app.inject({
      method: "POST",
      url: "/api/v1/auth/web/refresh",
      headers: { origin: "https://admin.hotel.example" },
      payload: {}
    });
    assert.equal(noCookie.statusCode, 401);

    const mismatch = await app.inject({
      method: "POST",
      url: "/api/v1/auth/web/refresh",
      headers: {
        origin: "https://admin.hotel.example",
        cookie: "hd_refresh=refresh; hd_csrf=expected",
        "x-csrf-token": "different"
      },
      payload: {}
    });
    assert.equal(mismatch.statusCode, 403);
    assert.equal(mismatch.json().error, "CSRF_REJECTED");

    const invalidOrigin = await app.inject({
      method: "POST",
      url: "/api/v1/auth/web/refresh",
      headers: {
        origin: "https://attacker.example",
        cookie: "hd_refresh=refresh; hd_csrf=matching",
        "x-csrf-token": "matching"
      },
      payload: {}
    });
    assert.equal(invalidOrigin.statusCode, 403);
    assert.equal(invalidOrigin.json().error, "ORIGIN_REJECTED");
  });
});
