import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import Fastify from "fastify";
import cookie from "@fastify/cookie";
import cors from "@fastify/cors";
import helmet from "@fastify/helmet";
import multipart from "@fastify/multipart";
import rateLimit from "@fastify/rate-limit";
import { fileTypeFromBuffer } from "file-type";
import { z } from "zod";
import {
  cookieOptions,
  hashPassword,
  hashToken,
  passwordNeedsRehash,
  randomToken,
  signAccessToken,
  tokenExpiry,
  verifyAccessToken,
  verifyPassword
} from "./auth.js";

const email = z.string().trim().toLowerCase().email().max(254);
const password = z.string().min(12).max(128);
const dateString = z.iso.date();

const registrationSchema = z.object({
  nationalId: z.string().trim().min(5).max(16).regex(/^[A-Za-z0-9-]+$/),
  email,
  password,
  firstName: z.string().trim().min(1).max(80),
  lastName1: z.string().trim().min(1).max(80),
  lastName2: z.string().trim().max(80).optional().nullable(),
  gender: z.string().trim().max(32).optional().nullable(),
  street: z.string().trim().max(120).optional().nullable(),
  streetNumber: z.string().trim().max(16).optional().nullable(),
  postalCode: z.string().regex(/^[0-9]{5}$/).optional().nullable()
});

const roomSchema = z.object({
  name: z.string().trim().min(2).max(100),
  description: z.string().trim().max(2000).default(""),
  pricePerNight: z.coerce.number().min(0).max(100_000),
  areaM2: z.coerce.number().int().min(1).max(10_000),
  maxGuests: z.coerce.number().int().min(1).max(20),
  imageUrl: z.string().url().max(2000).optional().nullable(),
  active: z.boolean().default(true)
});

export async function buildApp({ config, db, mailer }) {
  const app = Fastify({
    logger: {
      level: config.LOG_LEVEL,
      redact: {
        paths: [
          "req.headers.authorization",
          "req.headers.cookie",
          "req.body.password",
          "req.body.refreshToken",
          "res.headers.set-cookie"
        ],
        censor: "[REDACTED]"
      }
    },
    bodyLimit: 1_048_576,
    trustProxy: true,
    requestIdHeader: "x-request-id",
    genReqId: () => crypto.randomUUID()
  });

  await app.register(cookie);
  await app.register(cors, {
    origin(origin, callback) {
      if (!origin || origin === config.ADMIN_WEB_ORIGIN) return callback(null, true);
      callback(new Error("Origen no permitido"), false);
    },
    credentials: true,
    allowedHeaders: ["authorization", "content-type", "x-csrf-token", "x-request-id"],
    methods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]
  });
  await app.register(helmet, {
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'none'"],
        frameAncestors: ["'none'"],
        baseUri: ["'none'"],
        formAction: ["'none'"]
      }
    },
    hsts: config.NODE_ENV === "production"
      ? { maxAge: 31_536_000, includeSubDomains: true, preload: true }
      : false
  });
  await app.register(rateLimit, {
    max: 120,
    timeWindow: "1 minute",
    allowList: ["127.0.0.1"],
    keyGenerator: (request) => request.ip
  });
  await app.register(multipart, {
    limits: { files: 1, fileSize: 5_242_880, fields: 2, parts: 3 }
  });

  app.decorateRequest("auth", null);

  app.setErrorHandler((error, request, reply) => {
    request.log.error({ err: error, requestId: request.id }, "Error procesando la solicitud");
    if (error instanceof z.ZodError) {
      return reply.code(400).send({
        error: "VALIDATION_ERROR",
        message: "Los datos enviados no son válidos",
        fields: error.issues.map((issue) => ({ field: issue.path.join("."), message: issue.message }))
      });
    }
    if (error.statusCode && error.statusCode >= 400 && error.statusCode < 500) {
      return reply.code(error.statusCode).send({
        error: error.code || "REQUEST_ERROR",
        message: error.statusCode === 429 ? "Demasiadas solicitudes; inténtalo más tarde" : error.message
      });
    }
    return reply.code(500).send({
      error: "INTERNAL_ERROR",
      message: "No se pudo completar la operación",
      requestId: request.id
    });
  });

  app.addHook("onSend", async (_request, reply) => {
    reply.header("cache-control", "no-store");
    reply.header("x-content-type-options", "nosniff");
  });

  async function authenticate(request, reply) {
    const header = request.headers.authorization;
    if (!header?.startsWith("Bearer ")) {
      return reply.code(401).send({ error: "UNAUTHORIZED", message: "Autenticación requerida" });
    }
    try {
      const payload = await verifyAccessToken(header.slice(7), config);
      const result = await db.query(
        `SELECT u.id, u.person_id, u.role, u.disabled_at, p.email
         FROM users u JOIN persons p ON p.id = u.person_id WHERE u.id = $1`,
        [payload.sub]
      );
      if (!result.rowCount || result.rows[0].disabled_at) throw new Error("disabled");
      request.auth = result.rows[0];
    } catch {
      return reply.code(401).send({ error: "INVALID_TOKEN", message: "La sesión no es válida" });
    }
  }

  function requireRole(...roles) {
    return async (request, reply) => {
      await authenticate(request, reply);
      if (reply.sent) return;
      if (!roles.includes(request.auth.role)) {
        return reply.code(403).send({ error: "FORBIDDEN", message: "No tienes permisos para esta operación" });
      }
    };
  }

  function requireCookieCsrf(request, reply) {
    const refreshFromCookie = request.cookies.hd_refresh;
    if (!refreshFromCookie) return;
    const header = request.headers["x-csrf-token"];
    if (!header || header !== request.cookies.hd_csrf) {
      return reply.code(403).send({ error: "CSRF_REJECTED", message: "Solicitud no autorizada" });
    }
    if (request.headers.origin && request.headers.origin !== config.ADMIN_WEB_ORIGIN) {
      return reply.code(403).send({ error: "ORIGIN_REJECTED", message: "Origen no permitido" });
    }
  }

  async function audit(request, action, targetType, targetId, metadata = {}) {
    const hmac = (value) =>
      value ? crypto.createHmac("sha256", config.JWT_SECRET).update(value).digest("hex") : null;
    await db.query(
      `INSERT INTO audit_log
       (actor_user_id, action, target_type, target_id, ip_hash, user_agent_hash, metadata)
       VALUES ($1, $2, $3, $4, $5, $6, $7)`,
      [
        request.auth?.id ?? null,
        action,
        targetType,
        targetId ? String(targetId) : null,
        hmac(request.ip),
        hmac(request.headers["user-agent"]),
        JSON.stringify(metadata)
      ]
    );
  }

  async function issueSession(reply, user, request) {
    const refreshToken = randomToken(48);
    const csrfToken = randomToken(24);
    await db.query(
      `INSERT INTO refresh_sessions (user_id, token_hash, user_agent_hash, expires_at)
       VALUES ($1, $2, $3, $4)`,
      [
        user.id,
        hashToken(refreshToken),
        hashToken(request.headers["user-agent"] || "unknown"),
        tokenExpiry(config.REFRESH_TOKEN_DAYS)
      ]
    );
    const accessToken = await signAccessToken(user, config);
    reply.setCookie("hd_refresh", refreshToken, cookieOptions(config));
    reply.setCookie("hd_csrf", csrfToken, {
      httpOnly: false,
      secure: config.COOKIE_SECURE,
      sameSite: "strict",
      path: "/api/v1/auth",
      maxAge: config.REFRESH_TOKEN_DAYS * 86_400
    });
    return {
      accessToken,
      refreshToken,
      csrfToken,
      expiresIn: config.ACCESS_TOKEN_MINUTES * 60,
      user: { id: user.id, email: user.email, role: user.role }
    };
  }

  app.get("/health", async () => {
    await db.query("SELECT 1");
    return { status: "ok" };
  });

  app.post("/api/v1/auth/register", {
    config: { rateLimit: { max: 5, timeWindow: "1 hour" } }
  }, async (request, reply) => {
    const input = registrationSchema.parse(request.body);
    const passwordHash = await hashPassword(input.password);
    const verificationToken = randomToken(32);
    const user = await db.transaction(async (client) => {
      const person = await client.query(
        `INSERT INTO persons
         (national_id, email, first_name, last_name_1, last_name_2, gender, street, street_number, postal_code)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
         RETURNING id, email, first_name`,
        [
          input.nationalId.toUpperCase(), input.email, input.firstName, input.lastName1,
          input.lastName2 || null, input.gender || null, input.street || null,
          input.streetNumber || null, input.postalCode || null
        ]
      );
      const created = await client.query(
        `INSERT INTO users (person_id, password_hash)
         VALUES ($1, $2) RETURNING id, role`,
        [person.rows[0].id, passwordHash]
      );
      await client.query(
        `INSERT INTO email_verification_tokens (user_id, token_hash, expires_at)
         VALUES ($1, $2, now() + interval '24 hours')`,
        [created.rows[0].id, hashToken(verificationToken)]
      );
      const verificationUrl =
        `${config.PUBLIC_BASE_URL}/api/v1/auth/verify-email?token=${encodeURIComponent(verificationToken)}`;
      await client.query(
        `INSERT INTO email_outbox(template, recipient, payload)
         VALUES ('registration_confirmation', $1, $2)`,
        [person.rows[0].email, JSON.stringify({
          name: person.rows[0].first_name,
          verificationUrl
        })]
      );
      return { id: created.rows[0].id, personId: person.rows[0].id };
    }).catch((error) => {
      if (error.code === "23505") {
        const conflict = new Error("Ya existe una cuenta con ese email o documento");
        conflict.statusCode = 409;
        conflict.code = "ACCOUNT_EXISTS";
        throw conflict;
      }
      throw error;
    });
    await audit(request, "user.registered", "user", user.id);
    return reply.code(201).send({
      message: "Cuenta creada. Revisa tu correo para confirmarla.",
      emailConfirmationQueued: true
    });
  });

  app.get("/api/v1/auth/verify-email", async (request, reply) => {
    const query = z.object({ token: z.string().min(32).max(200) }).parse(request.query);
    const verified = await db.query(
      `UPDATE users SET email_verified_at = now()
       WHERE id = (
         SELECT user_id FROM email_verification_tokens
         WHERE token_hash = $1 AND consumed_at IS NULL AND expires_at > now()
       )
       RETURNING id`,
      [hashToken(query.token)]
    );
    if (!verified.rowCount) {
      return reply.code(400).type("text/html").send(
        "<!doctype html><meta charset=utf-8><title>Enlace no válido</title><p>El enlace no es válido o ha caducado.</p>"
      );
    }
    await db.query(
      "UPDATE email_verification_tokens SET consumed_at = now() WHERE token_hash = $1",
      [hashToken(query.token)]
    );
    return reply.type("text/html").send(
      "<!doctype html><meta charset=utf-8><title>Cuenta confirmada</title><p>Tu cuenta de Hotel Dumbría ya está confirmada.</p>"
    );
  });

  app.post("/api/v1/auth/login", {
    config: { rateLimit: { max: 8, timeWindow: "15 minutes" } }
  }, async (request, reply) => {
    const input = z.object({ email, password: z.string().min(1).max(128) }).parse(request.body);
    const result = await db.query(
      `SELECT u.id, u.person_id, u.password_hash, u.role, u.email_verified_at,
              u.disabled_at, u.locked_until, p.email
       FROM users u JOIN persons p ON p.id = u.person_id
       WHERE p.email = $1`,
      [input.email]
    );
    const user = result.rows[0];
    const dummyHash = "$argon2id$v=19$m=19456,t=2,p=1$MDAwMDAwMDAwMDAwMDAwMA$Mmmq8fP/S+XvUZm72Q8+OiYF5Y31e4l7MWHsiVLHwi8";
    const passwordOk = await verifyPassword(user?.password_hash || dummyHash, input.password).catch(() => false);
    if (!user || !passwordOk || user.disabled_at || (user.locked_until && user.locked_until > new Date())) {
      if (user) {
        await db.query(
          `UPDATE users SET
             failed_login_attempts = failed_login_attempts + 1,
             locked_until = CASE WHEN failed_login_attempts + 1 >= 5
               THEN now() + interval '15 minutes' ELSE locked_until END
           WHERE id = $1`,
          [user.id]
        );
      }
      await audit(request, "auth.login_failed", "user", user?.id, { reason: "invalid_credentials" });
      return reply.code(401).send({ error: "INVALID_CREDENTIALS", message: "Email o contraseña incorrectos" });
    }
    if (!user.email_verified_at) {
      return reply.code(403).send({ error: "EMAIL_NOT_VERIFIED", message: "Confirma primero tu dirección de correo" });
    }
    if (passwordNeedsRehash(user.password_hash)) {
      await db.query("UPDATE users SET password_hash = $2 WHERE id = $1", [
        user.id,
        await hashPassword(input.password)
      ]);
    }
    await db.query(
      "UPDATE users SET failed_login_attempts = 0, locked_until = NULL, last_login_at = now() WHERE id = $1",
      [user.id]
    );
    const session = await issueSession(reply, user, request);
    request.auth = user;
    await audit(request, "auth.login_succeeded", "user", user.id);
    return session;
  });

  app.post("/api/v1/auth/refresh", async (request, reply) => {
    requireCookieCsrf(request, reply);
    if (reply.sent) return;
    const body = z.object({ refreshToken: z.string().optional() }).parse(request.body || {});
    const oldToken = request.cookies.hd_refresh || body.refreshToken;
    if (!oldToken) {
      return reply.code(401).send({ error: "NO_REFRESH_TOKEN", message: "La sesión ha caducado" });
    }
    const result = await db.query(
      `SELECT s.id AS session_id, s.user_id AS id, u.person_id, u.role, p.email
       FROM refresh_sessions s
       JOIN users u ON u.id = s.user_id
       JOIN persons p ON p.id = u.person_id
       WHERE s.token_hash = $1 AND s.revoked_at IS NULL AND s.expires_at > now()
         AND u.disabled_at IS NULL`,
      [hashToken(oldToken)]
    );
    if (!result.rowCount) {
      reply.clearCookie("hd_refresh", cookieOptions(config));
      return reply.code(401).send({ error: "INVALID_REFRESH_TOKEN", message: "La sesión ha caducado" });
    }
    const newSession = await issueSession(reply, result.rows[0], request);
    const newHash = hashToken(newSession.refreshToken);
    const replacement = await db.query(
      "SELECT id FROM refresh_sessions WHERE token_hash = $1",
      [newHash]
    );
    await db.query(
      "UPDATE refresh_sessions SET revoked_at = now(), replaced_by = $2 WHERE id = $1",
      [result.rows[0].session_id, replacement.rows[0].id]
    );
    return newSession;
  });

  app.post("/api/v1/auth/logout", async (request, reply) => {
    requireCookieCsrf(request, reply);
    if (reply.sent) return;
    const body = z.object({ refreshToken: z.string().optional() }).parse(request.body || {});
    const token = request.cookies.hd_refresh || body.refreshToken;
    if (token) {
      await db.query("UPDATE refresh_sessions SET revoked_at = now() WHERE token_hash = $1", [hashToken(token)]);
    }
    reply.clearCookie("hd_refresh", cookieOptions(config));
    reply.clearCookie("hd_csrf", { path: "/api/v1/auth" });
    return reply.code(204).send();
  });

  app.get("/api/v1/users/me", { preHandler: authenticate }, async (request) => {
    const result = await db.query(
      `SELECT p.id, p.national_id AS "nationalId", p.email,
              p.first_name AS "firstName", p.last_name_1 AS "lastName1",
              p.last_name_2 AS "lastName2", p.gender, p.street,
              p.street_number AS "streetNumber", p.postal_code AS "postalCode",
              u.role, u.email_verified_at AS "emailVerifiedAt",
              a.id IS NOT NULL AS "hasAvatar"
       FROM persons p JOIN users u ON u.person_id = p.id
       LEFT JOIN avatars a ON a.person_id = p.id
       WHERE u.id = $1`,
      [request.auth.id]
    );
    return result.rows[0];
  });

  app.put("/api/v1/users/me", { preHandler: authenticate }, async (request) => {
    const input = registrationSchema.omit({ password: true, nationalId: true, email: true }).parse(request.body);
    const result = await db.query(
      `UPDATE persons SET first_name=$2, last_name_1=$3, last_name_2=$4,
         gender=$5, street=$6, street_number=$7, postal_code=$8
       WHERE id=$1 RETURNING id`,
      [
        request.auth.person_id, input.firstName, input.lastName1, input.lastName2 || null,
        input.gender || null, input.street || null, input.streetNumber || null,
        input.postalCode || null
      ]
    );
    await audit(request, "profile.updated", "person", result.rows[0].id);
    return { message: "Perfil actualizado" };
  });

  app.delete("/api/v1/users/me", { preHandler: authenticate }, async (request, reply) => {
    const input = z.object({ password: z.string().min(1).max(128) }).parse(request.body);
    const current = await db.query("SELECT password_hash FROM users WHERE id = $1", [request.auth.id]);
    if (!await verifyPassword(current.rows[0].password_hash, input.password)) {
      return reply.code(401).send({ error: "INVALID_CREDENTIALS", message: "La contraseña no es correcta" });
    }
    await db.transaction(async (client) => {
      await client.query(
        `INSERT INTO audit_log(actor_user_id, action, target_type, target_id)
         VALUES ($1, 'user.deleted_self', 'person', $2)`,
        [request.auth.id, request.auth.person_id]
      );
      const deleted = await client.query("DELETE FROM persons WHERE id = $1 RETURNING id", [request.auth.person_id]);
      if (!deleted.rowCount) throw new Error("No se encontró la persona");
    });
    reply.clearCookie("hd_refresh", cookieOptions(config));
    return reply.send({ message: "Cuenta eliminada" });
  });

  app.post("/api/v1/users/me/avatar", { preHandler: authenticate }, async (request, reply) => {
    const upload = await request.file();
    if (!upload) {
      return reply.code(400).send({ error: "FILE_REQUIRED", message: "Debes seleccionar una imagen" });
    }
    const buffer = await upload.toBuffer();
    const type = await fileTypeFromBuffer(buffer);
    if (!type || !["image/jpeg", "image/png", "image/webp"].includes(type.mime)) {
      return reply.code(415).send({ error: "INVALID_IMAGE", message: "La imagen debe ser JPEG, PNG o WebP" });
    }
    const storageKey = crypto.randomUUID();
    const target = path.join(config.AVATAR_DIRECTORY, `${storageKey}.${type.ext}`);
    await fs.mkdir(config.AVATAR_DIRECTORY, { recursive: true, mode: 0o700 });
    await fs.writeFile(target, buffer, { mode: 0o600, flag: "wx" });
    let previous;
    try {
      previous = await db.transaction(async (client) => {
        const old = await client.query("SELECT storage_key, content_type FROM avatars WHERE person_id = $1", [request.auth.person_id]);
        await client.query(
          `INSERT INTO avatars(person_id, storage_key, content_type, byte_size, sha256, original_name)
           VALUES ($1, $2, $3, $4, $5, $6)
           ON CONFLICT(person_id) DO UPDATE SET storage_key=EXCLUDED.storage_key,
             content_type=EXCLUDED.content_type, byte_size=EXCLUDED.byte_size,
             sha256=EXCLUDED.sha256, original_name=EXCLUDED.original_name, created_at=now()`,
          [
            request.auth.person_id, storageKey, type.mime, buffer.length,
            crypto.createHash("sha256").update(buffer).digest("hex"),
            path.basename(upload.filename || "avatar").slice(0, 255)
          ]
        );
        return old.rows[0];
      });
    } catch (error) {
      await fs.unlink(target).catch(() => {});
      throw error;
    }
    if (previous) {
      const oldExtension = previous.content_type === "image/png" ? "png"
        : previous.content_type === "image/webp" ? "webp" : "jpg";
      await fs.unlink(path.join(config.AVATAR_DIRECTORY, `${previous.storage_key}.${oldExtension}`)).catch(() => {});
    }
    await audit(request, "avatar.updated", "person", request.auth.person_id, { contentType: type.mime });
    return reply.code(201).send({ message: "Imagen de perfil actualizada" });
  });

  app.get("/api/v1/users/me/avatar", { preHandler: authenticate }, async (request, reply) => {
    const result = await db.query(
      "SELECT storage_key, content_type FROM avatars WHERE person_id = $1",
      [request.auth.person_id]
    );
    if (!result.rowCount) return reply.code(404).send({ error: "NOT_FOUND", message: "No hay imagen de perfil" });
    const extension = result.rows[0].content_type === "image/png" ? "png"
      : result.rows[0].content_type === "image/webp" ? "webp" : "jpg";
    const data = await fs.readFile(path.join(config.AVATAR_DIRECTORY, `${result.rows[0].storage_key}.${extension}`));
    return reply.type(result.rows[0].content_type).send(data);
  });

  app.get("/api/v1/rooms/availability", async (request) => {
    const query = z.object({
      checkIn: dateString,
      checkOut: dateString,
      guests: z.coerce.number().int().min(1).max(20).default(1)
    }).parse(request.query);
    if (query.checkOut <= query.checkIn) throw new z.ZodError([]);
    const result = await db.query(
      `SELECT r.id, r.name, r.description, r.price_per_night AS "pricePerNight",
              r.area_m2 AS "areaM2", r.max_guests AS "maxGuests", r.image_url AS "imageUrl"
       FROM rooms r
       WHERE r.active AND r.max_guests >= $3
         AND NOT EXISTS (
           SELECT 1 FROM reservations b WHERE b.room_id = r.id
             AND b.status IN ('pending','confirmed')
             AND daterange(b.check_in, b.check_out, '[)') && daterange($1::date, $2::date, '[)')
         )
       ORDER BY r.price_per_night, r.id`,
      [query.checkIn, query.checkOut, query.guests]
    );
    return { data: result.rows };
  });

  app.post("/api/v1/reservations", { preHandler: authenticate }, async (request, reply) => {
    const input = z.object({
      roomId: z.coerce.number().int().positive(),
      checkIn: dateString,
      checkOut: dateString,
      guests: z.coerce.number().int().min(1).max(20),
      notes: z.string().trim().max(500).optional().nullable()
    }).parse(request.body);
    if (input.checkOut <= input.checkIn) {
      return reply.code(400).send({ error: "INVALID_DATES", message: "La fecha de salida debe ser posterior" });
    }
    const reservation = await db.transaction(async (client) => {
      const room = await client.query(
        "SELECT id, price_per_night, max_guests FROM rooms WHERE id = $1 AND active FOR UPDATE",
        [input.roomId]
      );
      if (!room.rowCount || input.guests > room.rows[0].max_guests) {
        const error = new Error("La habitación no existe o no admite ese número de huéspedes");
        error.statusCode = 400;
        error.code = "ROOM_UNAVAILABLE";
        throw error;
      }
      const inserted = await client.query(
        `INSERT INTO reservations(room_id, person_id, check_in, check_out, guests, total_price, notes)
         VALUES ($1, $2, $3, $4, $5,
           ($4::date - $3::date) * $6::numeric, $7)
         RETURNING id, status, total_price AS "totalPrice"`,
        [
          input.roomId, request.auth.person_id, input.checkIn, input.checkOut,
          input.guests, room.rows[0].price_per_night, input.notes || null
        ]
      );
      return inserted.rows[0];
    }).catch((error) => {
      if (error.code === "23P01") {
        const conflict = new Error("La habitación acaba de ser reservada para esas fechas");
        conflict.statusCode = 409;
        conflict.code = "ROOM_ALREADY_BOOKED";
        throw conflict;
      }
      throw error;
    });
    await audit(request, "reservation.created", "reservation", reservation.id);
    return reply.code(201).send(reservation);
  });

  app.get("/api/v1/reservations/me", { preHandler: authenticate }, async (request) => {
    const result = await db.query(
      `SELECT b.id, b.check_in AS "checkIn", b.check_out AS "checkOut",
              b.guests, b.status, b.total_price AS "totalPrice",
              r.id AS "roomId", r.name AS "roomName"
       FROM reservations b JOIN rooms r ON r.id = b.room_id
       WHERE b.person_id = $1 ORDER BY b.check_in DESC`,
      [request.auth.person_id]
    );
    return { data: result.rows };
  });

  app.get("/api/v1/admin/summary", { preHandler: requireRole("admin", "staff") }, async () => {
    const result = await db.query(
      `SELECT
        (SELECT count(*)::int FROM reservations WHERE status IN ('pending','confirmed')) AS "activeReservations",
        (SELECT count(*)::int FROM reservations WHERE check_in = current_date AND status='confirmed') AS "arrivalsToday",
        (SELECT count(*)::int FROM rooms WHERE active) AS "activeRooms",
        (SELECT count(*)::int FROM users WHERE disabled_at IS NULL) AS "activeUsers"`
    );
    return result.rows[0];
  });

  app.get("/api/v1/admin/reservations", { preHandler: requireRole("admin", "staff") }, async (request) => {
    const query = z.object({
      status: z.enum(["pending", "confirmed", "cancelled", "completed"]).optional(),
      limit: z.coerce.number().int().min(1).max(200).default(100)
    }).parse(request.query);
    const result = await db.query(
      `SELECT b.id, b.check_in AS "checkIn", b.check_out AS "checkOut",
              b.guests, b.status, b.total_price AS "totalPrice",
              r.id AS "roomId", r.name AS "roomName",
              p.first_name || ' ' || p.last_name_1 AS "guestName", p.email
       FROM reservations b
       JOIN rooms r ON r.id = b.room_id JOIN persons p ON p.id = b.person_id
       WHERE ($1::reservation_status IS NULL OR b.status = $1)
       ORDER BY b.check_in DESC LIMIT $2`,
      [query.status || null, query.limit]
    );
    return { data: result.rows };
  });

  app.patch("/api/v1/admin/reservations/:id", { preHandler: requireRole("admin", "staff") }, async (request, reply) => {
    const params = z.object({ id: z.uuid() }).parse(request.params);
    const input = z.object({ status: z.enum(["pending", "confirmed", "cancelled", "completed"]) }).parse(request.body);
    const result = await db.query(
      "UPDATE reservations SET status = $2 WHERE id = $1 RETURNING id, status",
      [params.id, input.status]
    );
    if (!result.rowCount) return reply.code(404).send({ error: "NOT_FOUND", message: "Reserva no encontrada" });
    await audit(request, "reservation.status_changed", "reservation", params.id, { status: input.status });
    return result.rows[0];
  });

  app.get("/api/v1/admin/rooms", { preHandler: requireRole("admin", "staff") }, async () => {
    const result = await db.query(
      `SELECT id, name, description, price_per_night AS "pricePerNight",
              area_m2 AS "areaM2", max_guests AS "maxGuests",
              image_url AS "imageUrl", active FROM rooms ORDER BY id`
    );
    return { data: result.rows };
  });

  app.post("/api/v1/admin/rooms", { preHandler: requireRole("admin") }, async (request, reply) => {
    const input = roomSchema.parse(request.body);
    const result = await db.query(
      `INSERT INTO rooms(name, description, price_per_night, area_m2, max_guests, image_url, active)
       VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id`,
      [input.name, input.description, input.pricePerNight, input.areaM2, input.maxGuests, input.imageUrl || null, input.active]
    );
    await audit(request, "room.created", "room", result.rows[0].id);
    return reply.code(201).send(result.rows[0]);
  });

  app.put("/api/v1/admin/rooms/:id", { preHandler: requireRole("admin") }, async (request, reply) => {
    const params = z.object({ id: z.coerce.number().int().positive() }).parse(request.params);
    const input = roomSchema.parse(request.body);
    const result = await db.query(
      `UPDATE rooms SET name=$2, description=$3, price_per_night=$4,
         area_m2=$5, max_guests=$6, image_url=$7, active=$8
       WHERE id=$1 RETURNING id`,
      [params.id, input.name, input.description, input.pricePerNight, input.areaM2, input.maxGuests, input.imageUrl || null, input.active]
    );
    if (!result.rowCount) return reply.code(404).send({ error: "NOT_FOUND", message: "Habitación no encontrada" });
    await audit(request, "room.updated", "room", params.id);
    return result.rows[0];
  });

  app.get("/api/v1/admin/users", { preHandler: requireRole("admin") }, async () => {
    const result = await db.query(
      `SELECT u.id, p.id AS "personId", p.email,
              p.first_name || ' ' || p.last_name_1 AS name,
              u.role, u.email_verified_at AS "emailVerifiedAt",
              u.disabled_at AS "disabledAt", u.created_at AS "createdAt"
       FROM users u JOIN persons p ON p.id = u.person_id
       ORDER BY u.created_at DESC LIMIT 200`
    );
    return { data: result.rows };
  });

  app.delete("/api/v1/admin/users/:id", { preHandler: requireRole("admin") }, async (request, reply) => {
    const params = z.object({ id: z.uuid() }).parse(request.params);
    if (params.id === request.auth.id) {
      return reply.code(400).send({ error: "SELF_DELETE_FORBIDDEN", message: "No puedes eliminar tu propia cuenta desde el panel" });
    }
    const result = await db.transaction(async (client) => {
      const user = await client.query("SELECT person_id FROM users WHERE id = $1 FOR UPDATE", [params.id]);
      if (!user.rowCount) return null;
      await client.query(
        `INSERT INTO audit_log(actor_user_id, action, target_type, target_id)
         VALUES ($1, 'admin.user_deleted', 'person', $2)`,
        [request.auth.id, user.rows[0].person_id]
      );
      await client.query("DELETE FROM persons WHERE id = $1", [user.rows[0].person_id]);
      return user.rows[0];
    });
    if (!result) return reply.code(404).send({ error: "NOT_FOUND", message: "Usuario no encontrado" });
    return reply.code(204).send();
  });

  app.get("/api/v1/admin/audit", { preHandler: requireRole("admin") }, async () => {
    const result = await db.query(
      `SELECT id, action, target_type AS "targetType", target_id AS "targetId",
              metadata, created_at AS "createdAt"
       FROM audit_log ORDER BY created_at DESC LIMIT 200`
    );
    return { data: result.rows };
  });

  return app;
}
