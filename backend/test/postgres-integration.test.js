import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import sharp from "sharp";

import { buildApp } from "../src/app.js";
import { hashPassword } from "../src/auth.js";
import { loadConfig } from "../src/config.js";
import { createDatabase } from "../src/db.js";
import { createOutboxProcessor } from "../src/outbox.js";
import { addCalendarMonths, dateInTimeZone } from "../src/reservation-policy.js";

const databaseUrl = process.env.TEST_DATABASE_URL;
const PASSWORD = "Clave-de-prueba-2026!";

function plusDays(isoDate, amount) {
  const [year, month, day] = isoDate.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day + amount)).toISOString().slice(0, 10);
}

function dni(number) {
  const digits = String(number).padStart(8, "0");
  return `${digits}${"TRWAGMYFPDXBNJZSQVHLCKE"[Number(digits) % 23]}`;
}

function multipartImage(image, filename = "avatar.png") {
  const boundary = `----hotel-test-${Date.now()}-${Math.random()}`;
  return {
    payload: Buffer.concat([
      Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="avatar"; filename="${filename}"\r\nContent-Type: image/png\r\n\r\n`),
      image,
      Buffer.from(`\r\n--${boundary}--\r\n`)
    ]),
    headers: { "content-type": `multipart/form-data; boundary=${boundary}` }
  };
}

test("flujos críticos con PostgreSQL real", { skip: !databaseUrl }, async (suite) => {
  const parsedUrl = new URL(databaseUrl);
  if (!parsedUrl.pathname.toLowerCase().includes("test")) {
    throw new Error("TEST_DATABASE_URL debe apuntar a una base de datos desechable cuyo nombre contenga 'test'");
  }
  const avatarDirectory = await fs.mkdtemp(path.join(os.tmpdir(), "hotel-avatar-integration-"));
  const config = loadConfig({
    NODE_ENV: "test",
    DATABASE_URL: databaseUrl,
    JWT_SECRET: "jwt-integration-secret-with-at-least-32-characters",
    AUDIT_HMAC_SECRET: "audit-integration-secret-with-at-least-32-characters",
    PUBLIC_BASE_URL: "https://api.hotel.example",
    ADMIN_WEB_ORIGIN: "https://admin.hotel.example",
    SMTP_HOST: "smtp.example",
    SMTP_PORT: "587",
    MAIL_FROM: "reservas@hotel.example",
    AVATAR_DIRECTORY: avatarDirectory,
    LOGIN_LOCK_MINUTES: "1",
    LOG_LEVEL: process.env.TEST_LOG_LEVEL || "fatal"
  });
  const db = createDatabase(databaseUrl);
  const app = await buildApp({ config, db, mailer: {} });
  let identity = 100;

  async function createUser({ email, role = "customer", verified = true } = {}) {
    identity += 1;
    const passwordHash = await hashPassword(PASSWORD);
    return db.transaction(async (client) => {
      const person = await client.query(
        `INSERT INTO persons(national_id, email, first_name, last_name_1, role)
         VALUES ($1, $2, 'Test', 'User', $3) RETURNING id`,
        [`TEST-${identity}`, email, role]
      );
      const user = await client.query(
        `INSERT INTO users(person_id, password_hash, role, email_verified_at)
         VALUES ($1, $2, $3, CASE WHEN $4 THEN now() ELSE NULL END)
         RETURNING id, person_id`,
        [person.rows[0].id, passwordHash, role, verified]
      );
      return user.rows[0];
    });
  }

  async function mobileLogin(email, password = PASSWORD, remoteAddress = "198.51.100.10") {
    return app.inject({
      method: "POST",
      url: "/api/v1/auth/mobile/login",
      remoteAddress,
      payload: { email, password }
    });
  }

  function bearer(accessToken) {
    return { authorization: `Bearer ${accessToken}` };
  }

  await db.query("TRUNCATE persons, rooms, email_outbox, audit_log RESTART IDENTITY CASCADE");
  await db.query(
    `INSERT INTO rooms(name, description, price_per_night, area_m2, max_guests, active)
     VALUES ('Integration room', '', 80, 20, 2, true)`
  );
  await db.query("INSERT INTO provinces(id, name) VALUES (15, 'A Coruña') ON CONFLICT DO NOTHING");
  await db.query(
    `INSERT INTO towns(id, province_id, name) VALUES ('DUMBRIA', 15, 'Dumbría')
     ON CONFLICT DO NOTHING`
  );
  await db.query(
    `INSERT INTO postal_codes(postal_code, town_id) VALUES ('15151', 'DUMBRIA')
     ON CONFLICT DO NOTHING`
  );

  try {
    await suite.test("consulta postal parametrizada conserva identificadores de texto", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/v1/locations/postal-code?postalCode=15151"
      });
      assert.equal(response.statusCode, 200);
      assert.deepEqual(response.json().data[0], {
        poblacionid: "DUMBRIA",
        poblacion: "Dumbría",
        provinciaid: 15,
        provincia: "A Coruña"
      });
      assert.equal((await app.inject({
        method: "GET",
        url: "/api/v1/locations/postal-code?postalCode=15151%27%20OR%201%3D1--"
      })).statusCode, 400);
    });

    await suite.test("registro neutro y verificación transaccional POST", async () => {
      const email = "registration@example.test";
      const registration = await app.inject({
        method: "POST",
        url: "/api/v1/auth/register",
        remoteAddress: "198.51.100.20",
        payload: {
          nationalId: dni(12_345_670),
          email,
          password: PASSWORD,
          firstName: "Registro",
          lastName1: "Seguro"
        }
      });
      assert.equal(registration.statusCode, 202);
      assert.equal(registration.json().emailConfirmationQueued, true);
      const duplicate = await app.inject({
        method: "POST",
        url: "/api/v1/auth/register",
        remoteAddress: "198.51.100.21",
        payload: {
          nationalId: dni(12_345_670),
          email: "different@example.test",
          password: PASSWORD,
          firstName: "Duplicado",
          lastName1: "Neutral"
        }
      });
      assert.equal(duplicate.statusCode, 202);
      assert.equal(duplicate.json().message, registration.json().message);

      const queued = await db.query(
        "SELECT payload FROM email_outbox WHERE recipient = $1 AND template = 'registration_confirmation'",
        [email]
      );
      const token = new URL(queued.rows[0].payload.verificationUrl).searchParams.get("token");
      const preview = await app.inject({ method: "GET", url: `/api/v1/auth/verify-email?token=${token}` });
      assert.equal(preview.statusCode, 200);
      assert.match(preview.headers["content-security-policy"], /default-src 'none'/);
      assert.match(preview.headers["content-security-policy"], /form-action 'self'/);
      assert.equal((await db.query(
        "SELECT email_verified_at FROM users u JOIN persons p ON p.id=u.person_id WHERE p.email=$1",
        [email]
      )).rows[0].email_verified_at, null);

      const [first, second] = await Promise.all([
        app.inject({ method: "POST", url: "/api/v1/auth/verify-email", payload: { token } }),
        app.inject({ method: "POST", url: "/api/v1/auth/verify-email", payload: { token } })
      ]);
      assert.deepEqual([first.statusCode, second.statusCode].sort(), [200, 400]);
    });

    await suite.test("bloqueo fijo, concurrencia, usuario inexistente y múltiples IP", async () => {
      const email = "lock-policy@example.test";
      await createUser({ email });
      const failures = await Promise.all(Array.from({ length: 5 }, () => mobileLogin(
        email,
        "contraseña-incorrecta",
        "198.51.100.30"
      )));
      assert.equal(failures.every((response) => response.statusCode === 401), true);
      const locked = (await db.query(
        `SELECT failed_login_attempts, locked_until FROM users u
         JOIN persons p ON p.id=u.person_id WHERE p.email=$1`,
        [email]
      )).rows[0];
      assert.equal(locked.failed_login_attempts, 5);
      assert.ok(locked.locked_until);
      await mobileLogin(email, "otra-incorrecta", "198.51.100.30");
      const unchanged = (await db.query(
        `SELECT failed_login_attempts, locked_until FROM users u
         JOIN persons p ON p.id=u.person_id WHERE p.email=$1`,
        [email]
      )).rows[0];
      assert.equal(unchanged.failed_login_attempts, 5);
      assert.equal(new Date(unchanged.locked_until).getTime(), new Date(locked.locked_until).getTime());

      // El bloqueo frena contraseñas incorrectas, pero no permite que un tercero
      // deniegue acceso al propietario que sí presenta la credencial correcta.
      assert.equal((await mobileLogin(email, PASSWORD, "198.51.100.31")).statusCode, 200);

      await db.query(
        `UPDATE users SET failed_login_attempts=5,
           failed_login_window_started_at=now()-interval '2 minutes',
           locked_until=now()-interval '1 second'
         WHERE person_id=(SELECT id FROM persons WHERE email=$1)`,
        [email]
      );
      assert.equal((await mobileLogin(email, PASSWORD, "198.51.100.32")).statusCode, 200);

      const multiEmail = "multiple-ip@example.test";
      await createUser({ email: multiEmail });
      await Promise.all([
        mobileLogin(multiEmail, "incorrecta-a", "198.51.100.40"),
        mobileLogin(multiEmail, "incorrecta-b", "203.0.113.40")
      ]);
      assert.equal((await db.query(
        `SELECT failed_login_attempts FROM users u JOIN persons p ON p.id=u.person_id WHERE p.email=$1`,
        [multiEmail]
      )).rows[0].failed_login_attempts, 2);
      const missing = await mobileLogin("missing@example.test", "incorrecta-a", "198.51.100.41");
      assert.equal(missing.statusCode, 401);
      assert.equal(missing.json().message, failures[0].json().message);
    });

    await suite.test("login no enumera cuentas sin verificar o deshabilitadas", async () => {
      const unverifiedEmail = "unverified@example.test";
      const disabledEmail = "disabled@example.test";
      await createUser({ email: unverifiedEmail, verified: false });
      const disabled = await createUser({ email: disabledEmail });
      await db.query("UPDATE users SET disabled_at=now() WHERE id=$1", [disabled.id]);
      const [unverified, disabledResponse, absent] = await Promise.all([
        mobileLogin(unverifiedEmail, PASSWORD, "198.51.100.42"),
        mobileLogin(disabledEmail, PASSWORD, "198.51.100.43"),
        mobileLogin("absent-login@example.test", PASSWORD, "198.51.100.44")
      ]);
      assert.deepEqual(
        [unverified, disabledResponse, absent].map((response) => response.statusCode),
        [401, 401, 401]
      );
      assert.equal(unverified.json().message, disabledResponse.json().message);
      assert.equal(unverified.json().message, absent.json().message);
    });

    await suite.test("sesiones web/móvil separadas y refresh concurrente sin dos ramas", async () => {
      const email = "sessions@example.test";
      await createUser({ email, role: "admin" });
      const web = await app.inject({
        method: "POST",
        url: "/api/v1/auth/web/login",
        headers: { origin: "https://admin.hotel.example" },
        payload: { email, password: PASSWORD }
      });
      assert.equal(web.statusCode, 200);
      assert.equal(web.json().refreshToken, undefined);
      assert.match(String(web.headers["set-cookie"]), /hd_refresh=.*HttpOnly/i);
      assert.match(String(web.headers["set-cookie"]), /hd_csrf=/i);

      const mobile = await mobileLogin(email, PASSWORD, "198.51.100.50");
      assert.equal(mobile.statusCode, 200);
      const initialToken = mobile.json().refreshToken;
      const [left, right] = await Promise.all([
        app.inject({ method: "POST", url: "/api/v1/auth/mobile/refresh", payload: { refreshToken: initialToken } }),
        app.inject({ method: "POST", url: "/api/v1/auth/mobile/refresh", payload: { refreshToken: initialToken } })
      ]);
      assert.deepEqual([left.statusCode, right.statusCode].sort(), [200, 401]);
      const successful = left.statusCode === 200 ? left : right;
      const reuseFollowUp = await app.inject({
        method: "POST",
        url: "/api/v1/auth/mobile/refresh",
        payload: { refreshToken: successful.json().refreshToken }
      });
      assert.equal(reuseFollowUp.statusCode, 401);
      const family = await db.query(
        `SELECT count(*)::int AS total,
                count(*) FILTER (WHERE revoked_at IS NULL)::int AS active,
                count(*) FILTER (WHERE compromised_at IS NOT NULL)::int AS compromised
         FROM refresh_sessions WHERE family_id=(
           SELECT family_id FROM refresh_sessions WHERE token_hash=encode(digest($1, 'sha256'), 'hex')
         )`,
        [initialToken]
      );
      assert.equal(family.rows[0].total, 2);
      assert.equal(family.rows[0].active, 0);
      assert.equal(family.rows[0].compromised, 2);
    });

    await suite.test("recuperación consume una vez, usa Argon2id y revoca sesiones", async () => {
      const email = "recovery@example.test";
      await createUser({ email });
      const loggedIn = await mobileLogin(email, PASSWORD, "198.51.100.60");
      const forgot = await app.inject({
        method: "POST",
        url: "/api/v1/auth/password/forgot",
        remoteAddress: "198.51.100.61",
        payload: { email }
      });
      const missing = await app.inject({
        method: "POST",
        url: "/api/v1/auth/password/forgot",
        remoteAddress: "198.51.100.62",
        payload: { email: "absent-recovery@example.test" }
      });
      assert.equal(forgot.statusCode, 202);
      assert.equal(missing.json().message, forgot.json().message);
      const queued = await db.query(
        `SELECT payload FROM email_outbox
         WHERE recipient=$1 AND template='password_reset' ORDER BY created_at DESC LIMIT 1`,
        [email]
      );
      const token = new URL(queued.rows[0].payload.resetUrl).searchParams.get("token");
      assert.equal((await app.inject({
        method: "GET",
        url: `/api/v1/auth/password/reset?token=${token}`
      })).statusCode, 200);
      const newPassword = "Nueva-clave-segura-2026!";
      const resetResponses = await Promise.all([
        app.inject({ method: "POST", url: "/api/v1/auth/password/reset", payload: { token, password: newPassword } }),
        app.inject({ method: "POST", url: "/api/v1/auth/password/reset", payload: { token, password: newPassword } })
      ]);
      assert.deepEqual(resetResponses.map((response) => response.statusCode).sort(), [200, 400]);
      assert.equal((await app.inject({
        method: "GET",
        url: "/api/v1/users/me",
        headers: bearer(loggedIn.json().accessToken)
      })).statusCode, 401);
      assert.equal((await mobileLogin(email, PASSWORD, "198.51.100.63")).statusCode, 401);
      assert.equal((await mobileLogin(email, newPassword, "198.51.100.64")).statusCode, 200);
      const stored = (await db.query(
        `SELECT password_hash FROM users u JOIN persons p ON p.id=u.person_id WHERE p.email=$1`,
        [email]
      )).rows[0].password_hash;
      assert.match(stored, /^\$argon2id\$/);

      const expiredEmail = "expired-recovery@example.test";
      await createUser({ email: expiredEmail });
      await app.inject({
        method: "POST",
        url: "/api/v1/auth/password/forgot",
        remoteAddress: "198.51.100.65",
        payload: { email: expiredEmail }
      });
      const expiredQueued = await db.query(
        `SELECT payload FROM email_outbox
         WHERE recipient=$1 AND template='password_reset' ORDER BY created_at DESC LIMIT 1`,
        [expiredEmail]
      );
      const expiredToken = new URL(expiredQueued.rows[0].payload.resetUrl).searchParams.get("token");
      await db.query(
        `UPDATE password_reset_tokens
         SET created_at=now()-interval '2 days', expires_at=now()-interval '1 day'
         WHERE token_hash=encode(digest($1, 'sha256'), 'hex')`,
        [expiredToken]
      );
      assert.equal((await app.inject({
        method: "POST",
        url: "/api/v1/auth/password/reset",
        payload: { token: expiredToken, password: "Otra-clave-segura-2026!" }
      })).statusCode, 400);
    });

    await suite.test("reservas validan fechas, concurrencia, propiedad y máquina de estados", async () => {
      const firstEmail = "reservation-one@example.test";
      const secondEmail = "reservation-two@example.test";
      const adminEmail = "reservation-admin@example.test";
      await createUser({ email: firstEmail });
      await createUser({ email: secondEmail });
      const admin = await createUser({ email: adminEmail, role: "admin" });
      const first = (await mobileLogin(firstEmail)).json();
      const second = (await mobileLogin(secondEmail)).json();
      const adminSession = (await mobileLogin(adminEmail)).json();
      const today = dateInTimeZone(new Date(), config.HOTEL_TIME_ZONE);
      const checkIn = plusDays(today, 10);
      const checkOut = plusDays(today, 13);
      const roomId = (await db.query("SELECT id FROM rooms WHERE active ORDER BY id LIMIT 1")).rows[0].id;

      for (const payload of [
        { roomId, checkIn: plusDays(today, -1), checkOut: today, guests: 1 },
        { roomId, checkIn, checkOut: checkIn, guests: 1 },
        { roomId, checkIn, checkOut: plusDays(checkIn, 31), guests: 1 },
        { roomId, checkIn: plusDays(addCalendarMonths(today, 18), 1), checkOut: plusDays(addCalendarMonths(today, 18), 2), guests: 1 }
      ]) {
        assert.equal((await app.inject({
          method: "POST", url: "/api/v1/reservations", headers: bearer(first.accessToken), payload
        })).statusCode, 400);
      }
      const inactive = await db.query(
        `INSERT INTO rooms(name, description, price_per_night, area_m2, max_guests, active)
         VALUES ('Inactive', '', 10, 10, 1, false) RETURNING id`
      );
      assert.equal((await app.inject({
        method: "POST",
        url: "/api/v1/reservations",
        headers: bearer(first.accessToken),
        payload: { roomId: inactive.rows[0].id, checkIn, checkOut, guests: 1 }
      })).statusCode, 400);
      assert.equal((await app.inject({
        method: "POST",
        url: "/api/v1/reservations",
        headers: bearer(first.accessToken),
        payload: { roomId, checkIn, checkOut, guests: 3 }
      })).statusCode, 400);

      const concurrent = await Promise.all([
        app.inject({
          method: "POST", url: "/api/v1/reservations", headers: bearer(first.accessToken),
          payload: { roomId, checkIn, checkOut, guests: 1 }
        }),
        app.inject({
          method: "POST", url: "/api/v1/reservations", headers: bearer(second.accessToken),
          payload: { roomId, checkIn, checkOut, guests: 1 }
        })
      ]);
      assert.deepEqual(concurrent.map((response) => response.statusCode).sort(), [201, 409]);
      const created = concurrent.find((response) => response.statusCode === 201).json();
      const ownerToken = concurrent[0].statusCode === 201 ? first.accessToken : second.accessToken;
      const otherToken = concurrent[0].statusCode === 201 ? second.accessToken : first.accessToken;
      assert.equal((await app.inject({
        method: "POST", url: `/api/v1/reservations/${created.id}/cancel`, headers: bearer(otherToken)
      })).statusCode, 404);
      assert.equal((await app.inject({
        method: "POST", url: `/api/v1/reservations/${created.id}/cancel`, headers: bearer(ownerToken)
      })).statusCode, 200);
      const idempotent = await app.inject({
        method: "POST", url: `/api/v1/reservations/${created.id}/cancel`, headers: bearer(ownerToken)
      });
      assert.equal(idempotent.statusCode, 200);
      assert.equal(idempotent.json().idempotent, true);

      const stateRoom = await db.query(
        `INSERT INTO rooms(name, description, price_per_night, area_m2, max_guests)
         VALUES ('State room', '', 50, 20, 2) RETURNING id`
      );
      const pending = await app.inject({
        method: "POST", url: "/api/v1/reservations", headers: bearer(first.accessToken),
        payload: { roomId: stateRoom.rows[0].id, checkIn, checkOut, guests: 1 }
      });
      assert.equal(pending.statusCode, 201);
      const reservationId = pending.json().id;
      assert.equal((await app.inject({
        method: "PATCH", url: `/api/v1/admin/reservations/${reservationId}`,
        headers: bearer(adminSession.accessToken), payload: { status: "confirmed" }
      })).statusCode, 200);
      assert.equal((await app.inject({
        method: "PATCH", url: `/api/v1/admin/reservations/${reservationId}`,
        headers: bearer(adminSession.accessToken), payload: { status: "pending" }
      })).statusCode, 400);
      assert.equal((await app.inject({
        method: "PATCH", url: `/api/v1/admin/reservations/${reservationId}`,
        headers: bearer(adminSession.accessToken), payload: { status: "completed" }
      })).statusCode, 400);
      assert.equal((await app.inject({
        method: "PATCH", url: `/api/v1/admin/reservations/${reservationId}`,
        headers: bearer(adminSession.accessToken), payload: { status: "cancelled" }
      })).statusCode, 400);
      assert.equal((await app.inject({
        method: "PATCH", url: `/api/v1/admin/reservations/${reservationId}`,
        headers: bearer(adminSession.accessToken), payload: { status: "cancelled", reason: "Petición documentada" }
      })).statusCode, 200);
      const history = await db.query(
        "SELECT previous_status, new_status, actor_user_id, reason FROM reservation_status_history WHERE reservation_id=$1 ORDER BY id",
        [reservationId]
      );
      assert.equal(history.rowCount, 2);
      assert.equal(history.rows[1].reason, "Petición documentada");
      assert.equal(history.rows[1].actor_user_id, admin.id);

      const bulkRooms = await db.query(
        `INSERT INTO rooms(name, description, price_per_night, area_m2, max_guests)
         VALUES ('Bulk free', '', 50, 20, 2), ('Bulk occupied', '', 50, 20, 2)
         RETURNING id, name`
      );
      const freeRoomId = bulkRooms.rows.find((room) => room.name === "Bulk free").id;
      const occupiedRoomId = bulkRooms.rows.find((room) => room.name === "Bulk occupied").id;
      assert.equal((await app.inject({
        method: "POST", url: "/api/v1/reservations", headers: bearer(second.accessToken),
        payload: { roomId: occupiedRoomId, checkIn, checkOut, guests: 1 }
      })).statusCode, 201);
      const bulk = await app.inject({
        method: "POST", url: "/api/v1/reservations/bulk", headers: bearer(first.accessToken),
        payload: { reservations: [
          { roomId: freeRoomId, checkIn, checkOut, guests: 1 },
          { roomId: occupiedRoomId, checkIn, checkOut, guests: 1 }
        ] }
      });
      assert.equal(bulk.statusCode, 409);
      assert.equal((await db.query(
        "SELECT count(*)::int AS total FROM reservations WHERE room_id=$1",
        [freeRoomId]
      )).rows[0].total, 0);
    });

    await suite.test("avatares concurrentes y anonimización preservan el historial", async () => {
      const email = "privacy@example.test";
      const user = await createUser({ email });
      const session = (await mobileLogin(email, PASSWORD, "198.51.100.80")).json();
      const today = dateInTimeZone(new Date(), config.HOTEL_TIME_ZONE);
      const room = await db.query(
        `INSERT INTO rooms(name, description, price_per_night, area_m2, max_guests)
         VALUES ('Privacy room', '', 60, 20, 2) RETURNING id`
      );
      const reservation = await app.inject({
        method: "POST", url: "/api/v1/reservations", headers: bearer(session.accessToken),
        payload: { roomId: room.rows[0].id, checkIn: plusDays(today, 20), checkOut: plusDays(today, 22), guests: 1 }
      });
      assert.equal(reservation.statusCode, 201);
      const [red, blue] = await Promise.all([
        sharp({ create: { width: 80, height: 60, channels: 3, background: "red" } }).png().toBuffer(),
        sharp({ create: { width: 90, height: 70, channels: 3, background: "blue" } }).png().toBuffer()
      ]);
      const firstUpload = multipartImage(red, "one.png");
      const secondUpload = multipartImage(blue, "two.png");
      const uploads = await Promise.all([
        app.inject({
          method: "POST", url: "/api/v1/users/me/avatar",
          headers: { ...firstUpload.headers, ...bearer(session.accessToken) }, payload: firstUpload.payload
        }),
        app.inject({
          method: "POST", url: "/api/v1/users/me/avatar",
          headers: { ...secondUpload.headers, ...bearer(session.accessToken) }, payload: secondUpload.payload
        })
      ]);
      assert.equal(uploads.every((response) => response.statusCode === 201), true);
      const avatar = await db.query(
        "SELECT storage_key::text, content_type, width, height FROM avatars WHERE person_id=$1",
        [user.person_id]
      );
      assert.equal(avatar.rowCount, 1);
      assert.equal(avatar.rows[0].content_type, "image/webp");
      assert.equal((await fs.readdir(avatarDirectory)).filter((name) => name.endsWith(".webp")).length, 1);
      const served = await app.inject({
        method: "GET", url: "/api/v1/users/me/avatar", headers: bearer(session.accessToken)
      });
      assert.equal(served.statusCode, 200);
      assert.equal(served.headers["content-type"], "image/webp");
      assert.match(served.headers["content-disposition"], /^inline/);

      const deletion = await app.inject({
        method: "DELETE",
        url: "/api/v1/users/me",
        headers: bearer(session.accessToken),
        payload: { password: PASSWORD }
      });
      assert.equal(deletion.statusCode, 200);
      const retained = await db.query(
        `SELECT p.anonymized_at, p.email, u.disabled_at,
                (SELECT count(*)::int FROM reservations WHERE person_id=p.id) AS reservations,
                (SELECT count(*)::int FROM avatars WHERE person_id=p.id) AS avatars,
                (SELECT count(*)::int FROM refresh_sessions WHERE user_id=u.id AND revoked_at IS NULL) AS sessions
         FROM persons p JOIN users u ON u.person_id=p.id WHERE u.id=$1`,
        [user.id]
      );
      assert.ok(retained.rows[0].anonymized_at);
      assert.match(retained.rows[0].email, /^deleted\+/);
      assert.equal(retained.rows[0].reservations, 1);
      assert.equal(retained.rows[0].avatars, 0);
      assert.equal(retained.rows[0].sessions, 0);
      assert.equal((await app.inject({
        method: "GET", url: "/api/v1/users/me", headers: bearer(session.accessToken)
      })).statusCode, 401);
      assert.equal((await fs.readdir(avatarDirectory)).filter((name) => name.endsWith(".webp")).length, 0);

      const racingEmail = "privacy-race@example.test";
      const racing = await createUser({ email: racingEmail });
      const racingSession = (await mobileLogin(racingEmail, PASSWORD, "198.51.100.86")).json();
      const racingUpload = multipartImage(red, "race.png");
      const [uploadDuringDelete, concurrentDelete] = await Promise.all([
        app.inject({
          method: "POST",
          url: "/api/v1/users/me/avatar",
          headers: { ...racingUpload.headers, ...bearer(racingSession.accessToken) },
          payload: racingUpload.payload
        }),
        app.inject({
          method: "DELETE",
          url: "/api/v1/users/me",
          headers: bearer(racingSession.accessToken),
          payload: { password: PASSWORD }
        })
      ]);
      assert.equal(concurrentDelete.statusCode, 200);
      assert.ok([201, 401].includes(uploadDuringDelete.statusCode));
      assert.equal((await db.query(
        "SELECT count(*)::int AS total FROM avatars WHERE person_id=$1",
        [racing.person_id]
      )).rows[0].total, 0);
      assert.equal((await fs.readdir(avatarDirectory)).filter((name) => name.endsWith(".webp")).length, 0);

      const selfAdminEmail = "privacy-admin@example.test";
      const selfAdmin = await createUser({ email: selfAdminEmail, role: "admin" });
      const selfAdminSession = (await mobileLogin(selfAdminEmail)).json();
      assert.equal((await app.inject({
        method: "DELETE",
        url: `/api/v1/admin/users/${selfAdmin.id}`,
        headers: bearer(selfAdminSession.accessToken)
      })).statusCode, 400);
      const victim = await createUser({ email: "privacy-admin-target@example.test" });
      assert.equal((await app.inject({
        method: "DELETE",
        url: `/api/v1/admin/users/${victim.id}`,
        headers: bearer(selfAdminSession.accessToken)
      })).statusCode, 204);
      const adminDeleted = (await db.query(
        `SELECT p.anonymized_at, u.disabled_at FROM persons p
         JOIN users u ON u.person_id=p.id WHERE u.id=$1`,
        [victim.id]
      )).rows[0];
      assert.ok(adminDeleted.anonymized_at);
      assert.ok(adminDeleted.disabled_at);
    });

    await suite.test("el perfil admite género opcional y código postal textual", async () => {
      const email = "profile@example.test";
      await createUser({ email });
      const session = (await mobileLogin(email, PASSWORD, "198.51.100.81")).json();
      const update = await app.inject({
        method: "PUT",
        url: "/api/v1/users/me",
        headers: bearer(session.accessToken),
        payload: {
          firstName: "Nombre",
          lastName1: "Apelido",
          postalCode: "15151",
          streetNumber: "2A"
        }
      });
      assert.equal(update.statusCode, 200);
      const profile = await app.inject({
        method: "GET", url: "/api/v1/users/me", headers: bearer(session.accessToken)
      });
      assert.equal(profile.json().postalCode, "15151");
      assert.equal(profile.json().gender, null);
    });

    await suite.test("cambiar contraseña exige la actual y revoca todas las sesiones", async () => {
      const email = "password-change@example.test";
      await createUser({ email });
      const first = (await mobileLogin(email, PASSWORD, "198.51.100.82")).json();
      const second = (await mobileLogin(email, PASSWORD, "198.51.100.83")).json();
      assert.equal((await app.inject({
        method: "PUT",
        url: "/api/v1/users/me/password",
        headers: bearer(first.accessToken),
        payload: { currentPassword: "incorrecta", newPassword: "Cambio-seguro-2026!" }
      })).statusCode, 401);
      assert.equal((await app.inject({
        method: "PUT",
        url: "/api/v1/users/me/password",
        headers: bearer(first.accessToken),
        payload: { currentPassword: PASSWORD, newPassword: "Cambio-seguro-2026!" }
      })).statusCode, 200);
      assert.equal((await app.inject({
        method: "GET", url: "/api/v1/users/me", headers: bearer(second.accessToken)
      })).statusCode, 401);
      assert.equal((await mobileLogin(email, PASSWORD, "198.51.100.84")).statusCode, 401);
      assert.equal((await mobileLogin(email, "Cambio-seguro-2026!", "198.51.100.85")).statusCode, 200);
    });

    await suite.test("outbox procesa lotes, coordina workers, reintenta y se detiene limpiamente", async () => {
      await db.query("TRUNCATE email_outbox");
      const insertMessages = async (amount) => {
        for (let index = 0; index < amount; index += 1) {
          await db.query(
            `INSERT INTO email_outbox(template, recipient, payload)
             VALUES ('registration_confirmation', $1, $2)`,
            [`mail-${Date.now()}-${index}@example.test`, JSON.stringify({
              name: `Guest ${index}`,
              verificationUrl: `https://api.hotel.example/verify/${index}`
            })]
          );
        }
      };
      const logger = { warn() {}, error() {}, info() {} };
      const deliveries = [];
      const mailer = {
        async sendRegistrationConfirmation(message) { deliveries.push(message); },
        async sendPasswordReset(message) { deliveries.push(message); }
      };
      await insertMessages(3);
      const workerOne = createOutboxProcessor({ db, mailer, config, logger });
      const workerTwo = createOutboxProcessor({ db, mailer, config, logger });
      await Promise.all([workerOne.processBatch(), workerTwo.processBatch()]);
      workerOne.stop();
      workerTwo.stop();
      assert.equal(deliveries.length, 3);
      assert.equal(new Set(deliveries.map((message) => message.messageId)).size, 3);
      const sent = await db.query("SELECT status, payload FROM email_outbox ORDER BY id");
      assert.equal(sent.rows.every((row) => row.status === "sent"), true);
      assert.equal(sent.rows.every((row) => Object.keys(row.payload).length === 0), true);

      await db.query("TRUNCATE email_outbox");
      await insertMessages(1);
      const failingConfig = { ...config, OUTBOX_MAX_ATTEMPTS: 2 };
      const failingMailer = { async sendRegistrationConfirmation() { throw new Error("SMTP secret details"); } };
      const failingWorker = createOutboxProcessor({ db, mailer: failingMailer, config: failingConfig, logger });
      assert.equal(await failingWorker.processOne(), true);
      await db.query("UPDATE email_outbox SET next_attempt_at=now()");
      assert.equal(await failingWorker.processOne(), true);
      failingWorker.stop();
      const dead = (await db.query("SELECT status, attempts, last_error, payload FROM email_outbox")).rows[0];
      assert.equal(dead.status, "dead");
      assert.equal(dead.attempts, 2);
      assert.doesNotMatch(dead.last_error, /SMTP secret details/);
      assert.deepEqual(dead.payload, {});

      await db.query("TRUNCATE email_outbox");
      await insertMessages(2);
      let releaseDelivery;
      let deliveryStarted;
      const started = new Promise((resolve) => { deliveryStarted = resolve; });
      const gate = new Promise((resolve) => { releaseDelivery = resolve; });
      const stoppingMailer = {
        async sendRegistrationConfirmation() {
          deliveryStarted();
          await gate;
        }
      };
      const stoppingWorker = createOutboxProcessor({ db, mailer: stoppingMailer, config, logger });
      const processing = stoppingWorker.processBatch();
      await started;
      const stopped = stoppingWorker.stop();
      releaseDelivery();
      assert.equal(await processing, 1);
      await stopped;
      const afterStop = await db.query(
        "SELECT count(*) FILTER (WHERE status='sent')::int AS sent, count(*) FILTER (WHERE status='pending')::int AS pending FROM email_outbox"
      );
      assert.deepEqual(afterStop.rows[0], { sent: 1, pending: 1 });
    });
  } finally {
    await app.close();
    await db.close();
    await fs.rm(avatarDirectory, { recursive: true, force: true });
  }
});
