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
  verifyAccessToken,
  verifyPassword
} from "./auth.js";
import { loginPolicyFromConfig, nextFailedLoginState } from "./auth-policy.js";
import {
  createRefreshSession,
  revokeAllUserSessions,
  revokeRefreshToken,
  rotateRefreshSession
} from "./sessions.js";
import { isValidSpanishDocument, normalizeSpanishDocument } from "./identity.js";
import {
  assertReservationTransition,
  canCustomerCancel,
  validateReservationDates
} from "./reservation-policy.js";
import { decodeCursor, pageResult } from "./pagination.js";
import {
  avatarPath,
  reconstructAvatar,
  removeAvatarFile,
  storeAvatarFile
} from "./avatar-service.js";
import { anonymizeUserAccount } from "./user-anonymization.js";

const email = z.string().trim().toLowerCase().email().max(254);
const password = z.string().min(12).max(128);
const dateString = z.iso.date();

const registrationSchema = z.object({
  nationalId: z.string().trim().min(5).max(16)
    .transform(normalizeSpanishDocument)
    .refine(isValidSpanishDocument, "El DNI o NIE no es válido"),
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
const reservationInputSchema = z.object({
  roomId: z.coerce.number().int().positive(),
  checkIn: dateString,
  checkOut: dateString,
  guests: z.coerce.number().int().min(1).max(20),
  notes: z.string().trim().max(500).optional().nullable()
});

export async function buildApp({ config, db, mailer }) {
  const app = Fastify({
    logger: {
      level: config.LOG_LEVEL,
      serializers: {
        req(rawRequest) {
          return {
            method: rawRequest.method,
            url: rawRequest.url?.replace(/([?&]token=)[^&]*/gi, "$1[REDACTED]"),
            host: rawRequest.headers.host,
            remoteAddress: rawRequest.socket.remoteAddress,
            remotePort: rawRequest.socket.remotePort
          };
        }
      },
      redact: {
        paths: [
          "req.headers.authorization",
          "req.headers.cookie",
          "req.headers['x-csrf-token']",
          "req.body.password",
          "req.body.currentPassword",
          "req.body.newPassword",
          "req.body.refreshToken",
          "req.body.token",
          "req.query.token",
          "res.headers.set-cookie"
        ],
        censor: "[REDACTED]"
      }
    },
    bodyLimit: 1_048_576,
    trustProxy: config.TRUSTED_PROXIES.length ? config.TRUSTED_PROXIES : false,
    genReqId: (rawRequest) => {
      const candidate = rawRequest.headers["x-request-id"];
      return typeof candidate === "string" && candidate.length <= 36 && z.uuid().safeParse(candidate).success
        ? candidate
        : crypto.randomUUID();
    }
  });

  await app.register(cookie);
  await app.register(cors, {
    origin(origin, callback) {
      if (!origin || config.ADMIN_WEB_ORIGINS.includes(origin)) return callback(null, true);
      const error = new Error("Origen no permitido");
      error.statusCode = 403;
      error.code = "ORIGIN_REJECTED";
      callback(error, false);
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
        formAction: ["'self'"]
      }
    },
    hsts: config.NODE_ENV === "production"
      ? { maxAge: 31_536_000, includeSubDomains: true, preload: true }
      : false
  });
  await app.register(rateLimit, {
    max: 120,
    timeWindow: "1 minute",
    keyGenerator: (request) => request.ip
  });
  await app.register(multipart, {
    limits: { files: 1, fileSize: 5_242_880, fields: 2, parts: 3 }
  });
  app.addContentTypeParser(
    "application/x-www-form-urlencoded",
    { parseAs: "string" },
    (_request, body, done) => {
      try {
        done(null, Object.fromEntries(new URLSearchParams(body)));
      } catch (error) {
        done(error);
      }
    }
  );

  app.decorateRequest("auth", null);

  app.setErrorHandler((error, request, reply) => {
    if (error instanceof z.ZodError) {
      request.log.info({ requestId: request.id, validationIssues: error.issues.length }, "Solicitud no válida");
      return reply.code(400).send({
        error: "VALIDATION_ERROR",
        message: "Los datos enviados no son válidos",
        fields: error.issues.map((issue) => ({ field: issue.path.join("."), message: issue.message }))
      });
    }
    if (error.statusCode && error.statusCode >= 400 && error.statusCode < 500) {
      const level = error.statusCode === 429 || error.statusCode >= 401 ? "warn" : "info";
      request.log[level]({ requestId: request.id, code: error.code, statusCode: error.statusCode }, "Solicitud rechazada");
      const publicMessages = {
        ACCOUNT_EXISTS: "Si los datos permiten crear una cuenta, recibirás instrucciones por correo",
        ROOM_UNAVAILABLE: "La habitación no está disponible",
        ROOM_ALREADY_BOOKED: "La habitación ya no está disponible para esas fechas",
        FST_ERR_CTP_INVALID_MEDIA_TYPE: "El tipo de contenido no está permitido",
        INVALID_DATES: "Las fechas de la reserva no son válidas",
        CHECK_IN_IN_PAST: "No se puede reservar en el pasado",
        STAY_TOO_LONG: "La estancia supera la duración máxima",
        BOOKING_TOO_FAR: "La fecha supera el horizonte de reservas",
        INVALID_STATUS_TRANSITION: "La transición de estado no está permitida",
        RESERVATION_NOT_FINISHED: "La reserva todavía no puede completarse",
        CANCELLATION_NOT_ALLOWED: "La reserva ya no puede cancelarse",
        INVALID_IMAGE: "La imagen no se pudo procesar",
        IMAGE_DIMENSIONS_EXCEEDED: "La imagen supera las dimensiones permitidas"
      };
      return reply.code(error.statusCode).send({
        error: error.code || "REQUEST_ERROR",
        message: error.statusCode === 429
          ? "Demasiadas solicitudes; inténtalo más tarde"
          : publicMessages[error.code] || "No se pudo completar la solicitud",
        requestId: request.id
      });
    }
    request.log.error({ err: error, requestId: request.id }, "Error interno procesando la solicitud");
    return reply.code(500).send({
      error: "INTERNAL_ERROR",
      message: "No se pudo completar la operación",
      requestId: request.id
    });
  });

  app.setNotFoundHandler((request, reply) => reply.code(404).send({
    error: "NOT_FOUND",
    message: "El recurso solicitado no existe",
    requestId: request.id
  }));

  app.addHook("onSend", async (_request, reply) => {
    if (!reply.hasHeader("cache-control")) reply.header("cache-control", "no-store");
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
        `SELECT u.id, u.person_id, u.role, u.disabled_at, u.credential_version, p.email
         FROM users u
         JOIN persons p ON p.id = u.person_id
         JOIN refresh_sessions s ON s.id = $2 AND s.user_id = u.id
         WHERE u.id = $1 AND s.revoked_at IS NULL AND s.expires_at > now()`,
        [payload.sub, payload.sid]
      );
      if (!result.rowCount || result.rows[0].disabled_at
          || result.rows[0].credential_version !== payload.cv) throw new Error("disabled");
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

  function requireWebOrigin(request, reply) {
    const origin = request.headers.origin;
    if (typeof origin !== "string" || !config.ADMIN_WEB_ORIGINS.includes(origin)) {
      reply.code(403).send({ error: "ORIGIN_REJECTED", message: "Solicitud no autorizada" });
      return false;
    }
    return true;
  }

  function requireCookieCsrf(request, reply) {
    if (!request.cookies.hd_refresh) {
      reply.code(401).send({ error: "NO_REFRESH_TOKEN", message: "La sesión ha caducado" });
      return false;
    }
    const header = request.headers["x-csrf-token"];
    const csrfCookie = request.cookies.hd_csrf;
    if (typeof header !== "string" || typeof csrfCookie !== "string"
        || header.length > 128 || csrfCookie.length > 128
        || header.length !== csrfCookie.length
        || !crypto.timingSafeEqual(Buffer.from(header), Buffer.from(csrfCookie))) {
      reply.code(403).send({ error: "CSRF_REJECTED", message: "Solicitud no autorizada" });
      return false;
    }
    return requireWebOrigin(request, reply);
  }

  async function audit(request, action, targetType, targetId, metadata = {}) {
    const hmac = (value) =>
      value ? crypto.createHmac("sha256", config.AUDIT_HMAC_SECRET).update(value).digest("hex") : null;
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

  async function sessionPayload(reply, user, request, session, clientType) {
    const csrfToken = randomToken(24);
    const accessToken = await signAccessToken(user, config, {
      id: session.id,
      clientType
    });
    if (clientType === "web") {
      reply.setCookie("hd_refresh", session.refreshToken, cookieOptions(config, "/api/v1/auth/web"));
      reply.setCookie("hd_csrf", csrfToken, {
        httpOnly: false,
        secure: config.COOKIE_SECURE,
        sameSite: "strict",
        path: "/",
        maxAge: config.REFRESH_TOKEN_DAYS * 86_400
      });
    }
    const payload = {
      accessToken,
      expiresIn: config.ACCESS_TOKEN_MINUTES * 60,
      user: { id: user.id, email: user.email, role: user.role }
    };
    if (clientType === "mobile") payload.refreshToken = session.refreshToken;
    return payload;
  }

  async function issueSession(reply, user, request, clientType) {
    const session = await createRefreshSession({
      client: db,
      config,
      user,
      clientType,
      userAgent: request.headers["user-agent"]
    });
    return sessionPayload(reply, user, request, session, clientType);
  }

  function accountRateKey(request) {
    const candidate = typeof request.body?.email === "string"
      ? request.body.email.trim().toLowerCase().slice(0, 254)
      : "unknown";
    const accountHash = crypto.createHmac("sha256", config.AUDIT_HMAC_SECRET)
      .update(candidate)
      .digest("hex");
    return `${request.ip}:${accountHash}`;
  }

  async function recordFailedLogin(userId) {
    if (!userId) return;
    await db.transaction(async (client) => {
      const current = await client.query(
        `SELECT failed_login_attempts AS "failedLoginAttempts",
                failed_login_window_started_at AS "failedLoginWindowStartedAt",
                locked_until AS "lockedUntil"
         FROM users WHERE id = $1 FOR UPDATE`,
        [userId]
      );
      if (!current.rowCount) return;
      const next = nextFailedLoginState(
        current.rows[0],
        new Date(),
        loginPolicyFromConfig(config)
      );
      if (!next.changed) return;
      await client.query(
        `UPDATE users
         SET failed_login_attempts = $2,
             failed_login_window_started_at = $3,
             locked_until = $4
         WHERE id = $1`,
        [userId, next.failedLoginAttempts, next.failedLoginWindowStartedAt, next.lockedUntil]
      );
    });
  }

  app.get("/health", async () => {
    await db.query("SELECT 1");
    return { status: "ok" };
  });

  app.get("/api/v1/locations/postal-code", {
    config: { rateLimit: { max: 60, timeWindow: "1 minute" } }
  }, async (request) => {
    const query = z.object({ postalCode: z.string().regex(/^[0-9]{5}$/) }).parse(request.query);
    const result = await db.query(
      `SELECT t.id AS poblacionid, t.name AS poblacion,
              p.id::int AS provinciaid, p.name AS provincia
       FROM postal_codes pc
       JOIN towns t ON t.id = pc.town_id
       JOIN provinces p ON p.id = t.province_id
       WHERE pc.postal_code = $1
       ORDER BY t.name, t.id`,
      [query.postalCode]
    );
    return { data: result.rows };
  });

  app.post("/api/v1/auth/register", {
    config: { rateLimit: { max: 5, timeWindow: "1 hour", keyGenerator: accountRateKey } }
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
        return null;
      }
      throw error;
    });
    if (user) await audit(request, "user.registered", "user", user.id);
    else await audit(request, "user.registration_requested", "user", null);
    return reply.code(202).send({
      message: "Si los datos permiten crear la cuenta, recibirás un correo de confirmación.",
      emailConfirmationQueued: true
    });
  });

  function htmlReplyHeaders(reply) {
    reply.header("content-security-policy", "default-src 'none'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
    reply.header("referrer-policy", "no-referrer");
    return reply.type("text/html; charset=utf-8");
  }

  app.get("/api/v1/auth/verify-email", {
    config: { rateLimit: { max: 20, timeWindow: "15 minutes" } }
  }, async (request, reply) => {
    const query = z.object({ token: z.string().min(32).max(200) }).parse(request.query);
    const safeToken = query.token.replaceAll("&", "&amp;").replaceAll('"', "&quot;")
      .replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    return htmlReplyHeaders(reply).send(`<!doctype html><html lang="es"><meta charset="utf-8">
      <meta name="referrer" content="no-referrer"><title>Confirmar cuenta</title>
      <body><h1>Confirmar cuenta</h1><p>La cuenta no se modificará hasta que pulses el botón.</p>
      <form method="post" action="/api/v1/auth/verify-email">
      <input type="hidden" name="token" value="${safeToken}"><button type="submit">Confirmar mi cuenta</button>
      </form></body></html>`);
  });

  app.post("/api/v1/auth/verify-email", {
    config: { rateLimit: { max: 10, timeWindow: "15 minutes" } }
  }, async (request, reply) => {
    const input = z.object({ token: z.string().min(32).max(200) }).parse(request.body);
    const verified = await db.transaction(async (client) => {
      const token = await client.query(
        `SELECT id, user_id FROM email_verification_tokens
         WHERE token_hash = $1 AND consumed_at IS NULL AND expires_at > now()
         FOR UPDATE`,
        [hashToken(input.token)]
      );
      if (!token.rowCount) return false;
      const updated = await client.query(
        `UPDATE users SET email_verified_at = COALESCE(email_verified_at, now())
         WHERE id = $1 AND disabled_at IS NULL RETURNING id`,
        [token.rows[0].user_id]
      );
      if (!updated.rowCount) return false;
      await client.query("UPDATE email_verification_tokens SET consumed_at = now() WHERE id = $1", [token.rows[0].id]);
      return true;
    });
    if (!verified) {
      return htmlReplyHeaders(reply).code(400).send(
        "<!doctype html><meta charset=utf-8><title>Enlace no válido</title><p>El enlace no es válido o ha caducado.</p>"
      );
    }
    return htmlReplyHeaders(reply).send(
      "<!doctype html><meta charset=utf-8><title>Cuenta confirmada</title><p>Tu cuenta ya está confirmada.</p>"
    );
  });

  async function loginHandler(clientType, request, reply) {
    if (clientType === "web") {
      if (!requireWebOrigin(request, reply)) return;
    }
    const input = z.object({ email, password: z.string().min(1).max(128) }).parse(request.body);
    const result = await db.query(
      `SELECT u.id, u.person_id, u.password_hash, u.role, u.email_verified_at,
              u.disabled_at, u.locked_until AS "lockedUntil",
              u.failed_login_attempts AS "failedLoginAttempts",
              u.failed_login_window_started_at AS "failedLoginWindowStartedAt",
              u.credential_version, p.email
       FROM users u JOIN persons p ON p.id = u.person_id
       WHERE p.email = $1`,
      [input.email]
    );
    const user = result.rows[0];
    const dummyHash = "$argon2id$v=19$m=19456,t=2,p=1$MDAwMDAwMDAwMDAwMDAwMA$Mmmq8fP/S+XvUZm72Q8+OiYF5Y31e4l7MWHsiVLHwi8";
    const passwordOk = await verifyPassword(user?.password_hash || dummyHash, input.password).catch(() => false);
    // A correct credential may clear a soft lock. Otherwise an attacker who
    // knows an email address could deny its owner access with five bad guesses.
    const rejected = !user || !passwordOk || user.disabled_at || !user.email_verified_at;
    if (rejected) {
      if (user && !passwordOk) await recordFailedLogin(user.id);
      await audit(request, "auth.login_failed", "user", user?.id, { reason: "credentials_rejected" });
      return reply.code(401).send({ error: "INVALID_CREDENTIALS", message: "No se pudo iniciar sesión con esas credenciales" });
    }

    const accepted = await db.transaction(async (client) => {
      const locked = await client.query(
        `SELECT u.id, u.person_id, u.password_hash, u.role, u.email_verified_at,
                u.disabled_at, u.locked_until, u.credential_version, p.email
         FROM users u JOIN persons p ON p.id = u.person_id
         WHERE u.id = $1 FOR UPDATE OF u`,
        [user.id]
      );
      if (!locked.rowCount || locked.rows[0].disabled_at || !locked.rows[0].email_verified_at) return false;
      if (!await verifyPassword(locked.rows[0].password_hash, input.password).catch(() => false)) return false;
      const passwordHash = passwordNeedsRehash(locked.rows[0].password_hash)
        ? await hashPassword(input.password)
        : null;
      await client.query(
        `UPDATE users SET failed_login_attempts = 0,
           failed_login_window_started_at = NULL, locked_until = NULL,
           last_login_at = now(), password_hash = COALESCE($2, password_hash)
         WHERE id = $1`,
        [user.id, passwordHash]
      );
      return locked.rows[0];
    });
    if (!accepted) {
      return reply.code(401).send({ error: "INVALID_CREDENTIALS", message: "No se pudo iniciar sesión con esas credenciales" });
    }
    const session = await issueSession(reply, accepted, request, clientType);
    request.auth = accepted;
    await audit(request, "auth.login_succeeded", "user", accepted.id, { clientType });
    return session;
  }

  const loginOptions = { config: { rateLimit: { max: 8, timeWindow: "15 minutes", keyGenerator: accountRateKey } } };
  app.post("/api/v1/auth/web/login", loginOptions, (request, reply) => loginHandler("web", request, reply));
  app.post("/api/v1/auth/mobile/login", loginOptions, (request, reply) => loginHandler("mobile", request, reply));
  app.post("/api/v1/auth/login", loginOptions, (request, reply) => loginHandler("mobile", request, reply));

  async function refreshHandler(clientType, request, reply) {
    let oldToken;
    if (clientType === "web") {
      if (!requireCookieCsrf(request, reply)) return;
      oldToken = request.cookies.hd_refresh;
    } else {
      oldToken = z.object({ refreshToken: z.string().min(32).max(200) }).parse(request.body || {}).refreshToken;
    }
    const rotated = await rotateRefreshSession({
      db,
      config,
      refreshToken: oldToken,
      clientType,
      userAgent: request.headers["user-agent"]
    });
    if (rotated.status !== "rotated") {
      if (clientType === "web") {
        reply.clearCookie("hd_refresh", cookieOptions(config, "/api/v1/auth/web"));
        reply.clearCookie("hd_csrf", { path: "/" });
      }
      if (rotated.status === "reused") {
        await audit(request, "auth.refresh_reuse_detected", "user", rotated.userId, { clientType });
      }
      return reply.code(401).send({ error: "INVALID_REFRESH_TOKEN", message: "La sesión ha caducado" });
    }
    return sessionPayload(reply, rotated.user, request, rotated.session, clientType);
  }

  const refreshOptions = { config: { rateLimit: { max: 30, timeWindow: "15 minutes" } } };
  app.post("/api/v1/auth/web/refresh", refreshOptions, (request, reply) => refreshHandler("web", request, reply));
  app.post("/api/v1/auth/mobile/refresh", refreshOptions, (request, reply) => refreshHandler("mobile", request, reply));
  app.post("/api/v1/auth/refresh", refreshOptions, (request, reply) => refreshHandler("mobile", request, reply));

  async function logoutHandler(clientType, request, reply) {
    let token;
    if (clientType === "web") {
      if (!requireCookieCsrf(request, reply)) return;
      token = request.cookies.hd_refresh;
    } else {
      token = z.object({ refreshToken: z.string().min(32).max(200) }).parse(request.body || {}).refreshToken;
    }
    await revokeRefreshToken(db, token, clientType);
    if (clientType === "web") {
      reply.clearCookie("hd_refresh", cookieOptions(config, "/api/v1/auth/web"));
      reply.clearCookie("hd_csrf", { path: "/" });
    }
    return reply.code(204).send();
  }

  app.post("/api/v1/auth/web/logout", (request, reply) => logoutHandler("web", request, reply));
  app.post("/api/v1/auth/mobile/logout", (request, reply) => logoutHandler("mobile", request, reply));
  app.post("/api/v1/auth/logout", (request, reply) => logoutHandler("mobile", request, reply));

  app.post("/api/v1/auth/password/forgot", {
    config: { rateLimit: { max: 5, timeWindow: "1 hour", keyGenerator: accountRateKey } }
  }, async (request, reply) => {
    const input = z.object({ email }).parse(request.body);
    const result = await db.query(
      `SELECT u.id, p.email, p.first_name
       FROM users u JOIN persons p ON p.id = u.person_id
       WHERE p.email = $1 AND u.disabled_at IS NULL`,
      [input.email]
    );
    if (result.rowCount) {
      const resetToken = randomToken(48);
      await db.transaction(async (client) => {
        await client.query(
          "UPDATE password_reset_tokens SET consumed_at = now() WHERE user_id = $1 AND consumed_at IS NULL",
          [result.rows[0].id]
        );
        await client.query(
          `INSERT INTO password_reset_tokens(user_id, token_hash, expires_at)
           VALUES ($1, $2, now() + interval '1 hour')`,
          [result.rows[0].id, hashToken(resetToken)]
        );
        await client.query(
          `INSERT INTO email_outbox(template, recipient, payload)
           VALUES ('password_reset', $1, $2)`,
          [result.rows[0].email, JSON.stringify({
            name: result.rows[0].first_name,
            resetUrl: `${config.PUBLIC_BASE_URL}/api/v1/auth/password/reset?token=${encodeURIComponent(resetToken)}`
          })]
        );
      });
    }
    await audit(request, "auth.password_reset_requested", "user", result.rows[0]?.id);
    return reply.code(202).send({
      message: "Si existe una cuenta asociada, recibirás instrucciones para recuperar el acceso."
    });
  });

  app.get("/api/v1/auth/password/reset", {
    config: { rateLimit: { max: 20, timeWindow: "15 minutes" } }
  }, async (request, reply) => {
    const query = z.object({ token: z.string().min(32).max(200) }).parse(request.query);
    const safeToken = query.token.replaceAll("&", "&amp;").replaceAll('"', "&quot;")
      .replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    return htmlReplyHeaders(reply).send(`<!doctype html><html lang="es"><meta charset="utf-8">
      <meta name="referrer" content="no-referrer"><title>Cambiar contraseña</title>
      <body><h1>Cambiar contraseña</h1><form method="post" action="/api/v1/auth/password/reset">
      <input type="hidden" name="token" value="${safeToken}">
      <label>Nueva contraseña <input type="password" name="password" minlength="12" maxlength="128" required></label>
      <button type="submit">Cambiar contraseña</button></form></body></html>`);
  });

  app.post("/api/v1/auth/password/reset", {
    config: { rateLimit: { max: 10, timeWindow: "1 hour" } }
  }, async (request, reply) => {
    const input = z.object({ token: z.string().min(32).max(200), password }).parse(request.body);
    const passwordHash = await hashPassword(input.password);
    const reset = await db.transaction(async (client) => {
      const token = await client.query(
        `SELECT t.id, t.user_id FROM password_reset_tokens t
         JOIN users u ON u.id = t.user_id
         WHERE t.token_hash = $1 AND t.consumed_at IS NULL AND t.expires_at > now()
           AND u.disabled_at IS NULL
         FOR UPDATE OF t, u`,
        [hashToken(input.token)]
      );
      if (!token.rowCount) return null;
      const updated = await client.query(
        `UPDATE users SET password_hash = $2, password_reset_required = false,
           credential_version = credential_version + 1,
           failed_login_attempts = 0, failed_login_window_started_at = NULL,
           locked_until = NULL
         WHERE id = $1 AND disabled_at IS NULL RETURNING id`,
        [token.rows[0].user_id, passwordHash]
      );
      if (!updated.rowCount) return null;
      await client.query("UPDATE password_reset_tokens SET consumed_at = now() WHERE id = $1", [token.rows[0].id]);
      await revokeAllUserSessions(client, token.rows[0].user_id);
      return token.rows[0].user_id;
    });
    if (!reset) {
      const payload = { error: "INVALID_RESET_TOKEN", message: "El enlace no es válido o ha caducado" };
      return request.headers["content-type"]?.startsWith("application/json")
        ? reply.code(400).send(payload)
        : htmlReplyHeaders(reply).code(400).send("<!doctype html><meta charset=utf-8><title>Enlace no válido</title><p>El enlace no es válido o ha caducado.</p>");
    }
    await audit(request, "auth.password_reset_completed", "user", reset);
    return request.headers["content-type"]?.startsWith("application/json")
      ? reply.send({ message: "Contraseña actualizada" })
      : htmlReplyHeaders(reply).send("<!doctype html><meta charset=utf-8><title>Contraseña actualizada</title><p>La contraseña se ha cambiado y las sesiones anteriores se han cerrado.</p>");
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

  app.put("/api/v1/users/me/password", {
    preHandler: authenticate,
    config: { rateLimit: { max: 5, timeWindow: "1 hour" } }
  }, async (request, reply) => {
    const input = z.object({
      currentPassword: z.string().min(1).max(128),
      newPassword: password
    }).refine((value) => value.currentPassword !== value.newPassword, {
      path: ["newPassword"],
      message: "La nueva contraseña debe ser diferente"
    }).parse(request.body);
    const newHash = await hashPassword(input.newPassword);
    const changed = await db.transaction(async (client) => {
      const selected = await client.query(
        "SELECT password_hash FROM users WHERE id=$1 AND disabled_at IS NULL FOR UPDATE",
        [request.auth.id]
      );
      if (!selected.rowCount || !await verifyPassword(
        selected.rows[0].password_hash,
        input.currentPassword
      ).catch(() => false)) return false;
      await client.query(
        `UPDATE users SET password_hash=$2, credential_version=credential_version+1,
           password_reset_required=false, failed_login_attempts=0,
           failed_login_window_started_at=NULL, locked_until=NULL
         WHERE id=$1`,
        [request.auth.id, newHash]
      );
      await revokeAllUserSessions(client, request.auth.id);
      return true;
    });
    if (!changed) {
      return reply.code(401).send({
        error: "INVALID_CREDENTIALS",
        message: "No se pudo cambiar la contraseña"
      });
    }
    await audit(request, "auth.password_changed", "user", request.auth.id);
    return { message: "Contraseña actualizada; inicia sesión de nuevo" };
  });

  app.delete("/api/v1/users/me", { preHandler: authenticate }, async (request, reply) => {
    const input = z.object({ password: z.string().min(1).max(128) }).parse(request.body);
    const current = await db.query(
      "SELECT password_hash FROM users WHERE id = $1 AND disabled_at IS NULL",
      [request.auth.id]
    );
    if (!current.rowCount || !await verifyPassword(current.rows[0].password_hash, input.password)) {
      return reply.code(401).send({ error: "INVALID_CREDENTIALS", message: "La contraseña no es correcta" });
    }
    const anonymized = await db.transaction((client) => anonymizeUserAccount(client, {
      userId: request.auth.id,
      actorUserId: request.auth.id,
      action: "user.anonymized_self"
    }));
    if (!anonymized) return reply.code(404).send({ error: "NOT_FOUND", message: "Cuenta no encontrada" });
    await removeAvatarFile(
      config.AVATAR_DIRECTORY,
      anonymized.avatarStorageKey,
      anonymized.avatarContentType
    ).catch((error) => {
      request.log.warn({ err: error, personId: anonymized.person_id }, "No se pudo retirar el avatar anonimizado");
    });
    reply.clearCookie("hd_refresh", cookieOptions(config, "/api/v1/auth/web"));
    reply.clearCookie("hd_csrf", { path: "/" });
    return reply.send({ message: "Cuenta anonimizada" });
  });

  app.post("/api/v1/users/me/avatar", {
    preHandler: authenticate,
    config: { rateLimit: { max: 10, timeWindow: "1 hour" } }
  }, async (request, reply) => {
    const upload = await request.file();
    if (!upload) {
      return reply.code(400).send({ error: "FILE_REQUIRED", message: "Debes seleccionar una imagen" });
    }
    const buffer = await upload.toBuffer();
    const type = await fileTypeFromBuffer(buffer);
    if (!type || !["image/jpeg", "image/png", "image/webp"].includes(type.mime)) {
      return reply.code(415).send({ error: "INVALID_IMAGE", message: "La imagen debe ser JPEG, PNG o WebP" });
    }
    let image;
    try {
      image = await reconstructAvatar(buffer);
    } catch (error) {
      if (error.statusCode) throw error;
      const invalid = new Error("The uploaded image could not be decoded");
      invalid.statusCode = 415;
      invalid.code = "INVALID_IMAGE";
      throw invalid;
    }
    const storageKey = crypto.randomUUID();
    const target = await storeAvatarFile(config.AVATAR_DIRECTORY, storageKey, image.buffer);
    let previous;
    try {
      previous = await db.transaction(async (client) => {
        const account = await client.query(
          "SELECT disabled_at FROM users WHERE person_id=$1 FOR UPDATE",
          [request.auth.person_id]
        );
        if (!account.rowCount || account.rows[0].disabled_at) {
          const error = new Error("Account is no longer active");
          error.statusCode = 401;
          error.code = "INVALID_TOKEN";
          throw error;
        }
        await client.query("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", [request.auth.person_id]);
        const old = await client.query(
          "SELECT storage_key::text, content_type FROM avatars WHERE person_id = $1 FOR UPDATE",
          [request.auth.person_id]
        );
        await client.query(
          `INSERT INTO avatars(person_id, storage_key, content_type, byte_size, sha256, original_name, width, height)
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
           ON CONFLICT(person_id) DO UPDATE SET storage_key=EXCLUDED.storage_key,
             content_type=EXCLUDED.content_type, byte_size=EXCLUDED.byte_size,
             sha256=EXCLUDED.sha256, original_name=EXCLUDED.original_name,
             width=EXCLUDED.width, height=EXCLUDED.height, created_at=now()`,
          [
            request.auth.person_id, storageKey, image.contentType, image.buffer.length,
            image.sha256, path.basename(upload.filename || "avatar").slice(0, 255),
            image.width, image.height
          ]
        );
        return old.rows[0];
      });
    } catch (error) {
      await fs.unlink(target).catch(() => {});
      throw error;
    }
    if (previous) {
      await removeAvatarFile(config.AVATAR_DIRECTORY, previous.storage_key, previous.content_type).catch((error) => {
        request.log.warn({ err: error, storageKey: previous.storage_key }, "No se pudo retirar el avatar anterior");
      });
    }
    await audit(request, "avatar.updated", "person", request.auth.person_id, {
      inputContentType: type.mime,
      storedContentType: image.contentType,
      width: image.width,
      height: image.height
    });
    return reply.code(201).send({ message: "Imagen de perfil actualizada" });
  });

  app.get("/api/v1/users/me/avatar", { preHandler: authenticate }, async (request, reply) => {
    const result = await db.query(
      "SELECT storage_key::text, content_type FROM avatars WHERE person_id = $1",
      [request.auth.person_id]
    );
    if (!result.rowCount) return reply.code(404).send({ error: "NOT_FOUND", message: "No hay imagen de perfil" });
    let data;
    try {
      data = await fs.readFile(avatarPath(
        config.AVATAR_DIRECTORY,
        result.rows[0].storage_key,
        result.rows[0].content_type
      ));
    } catch (error) {
      if (error.code === "ENOENT") {
        request.log.warn({ personId: request.auth.person_id }, "El avatar referenciado no existe");
        return reply.code(404).send({ error: "NOT_FOUND", message: "No hay imagen de perfil" });
      }
      throw error;
    }
    return reply
      .header("content-disposition", "inline; filename=avatar.webp")
      .header("x-content-type-options", "nosniff")
      .header("cache-control", "private, no-store")
      .type(result.rows[0].content_type)
      .send(data);
  });

  app.get("/api/v1/rooms/availability", async (request) => {
    const query = z.object({
      checkIn: dateString,
      checkOut: dateString,
      guests: z.coerce.number().int().min(1).max(20).default(1)
    }).parse(request.query);
    validateReservationDates(query, config);
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

  async function createReservations(inputs, personId) {
    for (const input of inputs) validateReservationDates(input, config);
    if (new Set(inputs.map((input) => input.roomId)).size !== inputs.length) {
      const error = new Error("A room cannot occur twice in the same operation");
      error.statusCode = 400;
      error.code = "ROOM_UNAVAILABLE";
      throw error;
    }
    return db.transaction(async (client) => {
      const selectedRooms = await client.query(
        `SELECT id, price_per_night, max_guests FROM rooms
         WHERE id = ANY($1::int[]) AND active ORDER BY id FOR UPDATE`,
        [inputs.map((input) => input.roomId)]
      );
      const rooms = new Map(selectedRooms.rows.map((room) => [room.id, room]));
      const created = [];
      for (const input of inputs) {
        const room = rooms.get(input.roomId);
        if (!room || input.guests > room.max_guests) {
          const error = new Error("La habitación no existe o no admite ese número de huéspedes");
          error.statusCode = 400;
          error.code = "ROOM_UNAVAILABLE";
          throw error;
        }
        const inserted = await client.query(
          `INSERT INTO reservations(room_id, person_id, check_in, check_out, guests, total_price, notes)
           VALUES ($1, $2, $3, $4, $5,
             ($4::date - $3::date) * $6::numeric, $7)
           RETURNING id, room_id AS "roomId", status, total_price AS "totalPrice"`,
          [
            input.roomId, personId, input.checkIn, input.checkOut,
            input.guests, room.price_per_night, input.notes || null
          ]
        );
        created.push(inserted.rows[0]);
      }
      return created;
    }).catch((error) => {
      if (error.code === "23P01") {
        const conflict = new Error("La habitación acaba de ser reservada para esas fechas");
        conflict.statusCode = 409;
        conflict.code = "ROOM_ALREADY_BOOKED";
        throw conflict;
      }
      throw error;
    });
  }

  app.post("/api/v1/reservations", { preHandler: authenticate }, async (request, reply) => {
    const input = reservationInputSchema.parse(request.body);
    const [reservation] = await createReservations([input], request.auth.person_id);
    await audit(request, "reservation.created", "reservation", reservation.id);
    return reply.code(201).send(reservation);
  });

  app.post("/api/v1/reservations/bulk", { preHandler: authenticate }, async (request, reply) => {
    const input = z.object({
      reservations: z.array(reservationInputSchema).min(1).max(10)
    }).parse(request.body);
    const reservations = await createReservations(input.reservations, request.auth.person_id);
    for (const reservation of reservations) {
      await audit(request, "reservation.created", "reservation", reservation.id, { bulk: true });
    }
    return reply.code(201).send({ data: reservations });
  });

  app.get("/api/v1/reservations/me", { preHandler: authenticate }, async (request) => {
    const query = z.object({
      limit: z.coerce.number().int().min(1).max(100).default(50),
      cursor: z.string().max(500).optional()
    }).parse(request.query);
    const cursor = query.cursor
      ? z.object({ createdAt: z.iso.datetime(), id: z.uuid() }).parse(decodeCursor(query.cursor))
      : null;
    const result = await db.query(
      `SELECT b.id, b.check_in::text AS "checkIn", b.check_out::text AS "checkOut",
              b.guests, b.status, b.total_price AS "totalPrice",
              b.created_at AS "createdAt", r.id AS "roomId", r.name AS "roomName"
       FROM reservations b JOIN rooms r ON r.id = b.room_id
       WHERE b.person_id = $1
         AND ($2::timestamptz IS NULL OR (b.created_at, b.id) < ($2::timestamptz, $3::uuid))
       ORDER BY b.created_at DESC, b.id DESC LIMIT $4`,
      [request.auth.person_id, cursor?.createdAt || null, cursor?.id || null, query.limit + 1]
    );
    return pageResult(result.rows, query.limit, (row) => ({ createdAt: row.createdAt, id: row.id }));
  });

  app.post("/api/v1/reservations/:id/cancel", { preHandler: authenticate }, async (request, reply) => {
    const params = z.object({ id: z.uuid() }).parse(request.params);
    const result = await db.transaction(async (client) => {
      const current = await client.query(
        `SELECT id, status, check_in::text AS "checkIn", check_out::text AS "checkOut"
         FROM reservations WHERE id = $1 AND person_id = $2 FOR UPDATE`,
        [params.id, request.auth.person_id]
      );
      if (!current.rowCount) return { notFound: true };
      const reservation = current.rows[0];
      if (reservation.status === "cancelled") return { id: reservation.id, status: "cancelled", idempotent: true };
      if (!["pending", "confirmed"].includes(reservation.status)
          || !canCustomerCancel(reservation.checkIn, config)) {
        const error = new Error("Cancellation is not permitted");
        error.statusCode = 409;
        error.code = "CANCELLATION_NOT_ALLOWED";
        throw error;
      }
      await client.query(
        `UPDATE reservations SET status = 'cancelled', cancelled_at = now(),
           cancellation_reason = 'customer_request' WHERE id = $1`,
        [reservation.id]
      );
      await client.query(
        `INSERT INTO reservation_status_history
           (reservation_id, actor_user_id, previous_status, new_status, reason)
         VALUES ($1, $2, $3, 'cancelled', 'customer_request')`,
        [reservation.id, request.auth.id, reservation.status]
      );
      return { id: reservation.id, status: "cancelled", idempotent: false };
    });
    if (result.notFound) return reply.code(404).send({ error: "NOT_FOUND", message: "Reserva no encontrada" });
    await audit(request, "reservation.cancelled_by_customer", "reservation", params.id, {
      idempotent: result.idempotent
    });
    return result;
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
      limit: z.coerce.number().int().min(1).max(100).default(50),
      cursor: z.string().max(500).optional()
    }).parse(request.query);
    const cursor = query.cursor
      ? z.object({ createdAt: z.iso.datetime(), id: z.uuid() }).parse(decodeCursor(query.cursor))
      : null;
    const result = await db.query(
      `SELECT b.id, b.check_in::text AS "checkIn", b.check_out::text AS "checkOut",
              b.guests, b.status, b.total_price AS "totalPrice",
              b.created_at AS "createdAt",
              r.id AS "roomId", r.name AS "roomName",
              p.first_name || ' ' || p.last_name_1 AS "guestName", p.email
       FROM reservations b
       JOIN rooms r ON r.id = b.room_id JOIN persons p ON p.id = b.person_id
       WHERE ($1::reservation_status IS NULL OR b.status = $1)
         AND ($2::timestamptz IS NULL OR (b.created_at, b.id) < ($2::timestamptz, $3::uuid))
       ORDER BY b.created_at DESC, b.id DESC LIMIT $4`,
      [query.status || null, cursor?.createdAt || null, cursor?.id || null, query.limit + 1]
    );
    return pageResult(result.rows, query.limit, (row) => ({ createdAt: row.createdAt, id: row.id }));
  });

  app.patch("/api/v1/admin/reservations/:id", {
    preHandler: requireRole("admin", "staff"),
    config: { rateLimit: { max: 30, timeWindow: "1 minute" } }
  }, async (request, reply) => {
    const params = z.object({ id: z.uuid() }).parse(request.params);
    const input = z.object({
      status: z.enum(["pending", "confirmed", "cancelled", "completed"]),
      reason: z.string().trim().min(3).max(500).optional()
    }).superRefine((value, context) => {
      if (value.status === "cancelled" && !value.reason) {
        context.addIssue({ code: "custom", path: ["reason"], message: "El motivo es obligatorio al cancelar" });
      }
    }).parse(request.body);
    const result = await db.transaction(async (client) => {
      const current = await client.query(
        `SELECT id, status, check_in::text AS "checkIn", check_out::text AS "checkOut"
         FROM reservations WHERE id = $1 FOR UPDATE`,
        [params.id]
      );
      if (!current.rowCount) return null;
      const transition = assertReservationTransition(
        current.rows[0].status,
        input.status,
        current.rows[0],
        config
      );
      if (transition.idempotent) return { ...current.rows[0], idempotent: true };
      const updated = await client.query(
        `UPDATE reservations SET status = $2::reservation_status,
           cancelled_at = CASE WHEN $2::reservation_status = 'cancelled' THEN now() ELSE cancelled_at END,
           cancellation_reason = CASE WHEN $2::reservation_status = 'cancelled' THEN $3 ELSE cancellation_reason END
         WHERE id = $1 RETURNING id, status`,
        [params.id, input.status, input.reason || null]
      );
      await client.query(
        `INSERT INTO reservation_status_history
           (reservation_id, actor_user_id, previous_status, new_status, reason)
         VALUES ($1, $2, $3, $4, $5)`,
        [params.id, request.auth.id, current.rows[0].status, input.status, input.reason || null]
      );
      return {
        ...updated.rows[0],
        previousStatus: current.rows[0].status,
        idempotent: false
      };
    }).catch((error) => {
      if (error.code === "23P01") {
        const conflict = new Error("Overlapping reservation");
        conflict.statusCode = 409;
        conflict.code = "ROOM_ALREADY_BOOKED";
        throw conflict;
      }
      throw error;
    });
    if (!result) return reply.code(404).send({ error: "NOT_FOUND", message: "Reserva no encontrada" });
    await audit(request, "reservation.status_changed", "reservation", params.id, {
      previousStatus: result.previousStatus || result.status,
      newStatus: result.status,
      reason: input.reason || null,
      idempotent: result.idempotent
    });
    return result;
  });

  app.get("/api/v1/admin/rooms", { preHandler: requireRole("admin", "staff") }, async () => {
    const result = await db.query(
      `SELECT id, name, description, price_per_night AS "pricePerNight",
              area_m2 AS "areaM2", max_guests AS "maxGuests",
              image_url AS "imageUrl", active FROM rooms ORDER BY id`
    );
    return { data: result.rows };
  });

  app.post("/api/v1/admin/rooms", {
    preHandler: requireRole("admin"),
    config: { rateLimit: { max: 20, timeWindow: "1 minute" } }
  }, async (request, reply) => {
    const input = roomSchema.parse(request.body);
    const result = await db.query(
      `INSERT INTO rooms(name, description, price_per_night, area_m2, max_guests, image_url, active)
       VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id`,
      [input.name, input.description, input.pricePerNight, input.areaM2, input.maxGuests, input.imageUrl || null, input.active]
    );
    await audit(request, "room.created", "room", result.rows[0].id);
    return reply.code(201).send(result.rows[0]);
  });

  app.put("/api/v1/admin/rooms/:id", {
    preHandler: requireRole("admin"),
    config: { rateLimit: { max: 20, timeWindow: "1 minute" } }
  }, async (request, reply) => {
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

  app.get("/api/v1/admin/users", { preHandler: requireRole("admin") }, async (request) => {
    const query = z.object({
      limit: z.coerce.number().int().min(1).max(100).default(50),
      cursor: z.string().max(500).optional()
    }).parse(request.query);
    const cursor = query.cursor
      ? z.object({ createdAt: z.iso.datetime(), id: z.uuid() }).parse(decodeCursor(query.cursor))
      : null;
    const result = await db.query(
      `SELECT u.id, p.id AS "personId", p.email,
              p.first_name || ' ' || p.last_name_1 AS name,
              u.role, u.email_verified_at AS "emailVerifiedAt",
              u.disabled_at AS "disabledAt", u.created_at AS "createdAt"
       FROM users u JOIN persons p ON p.id = u.person_id
       WHERE ($1::timestamptz IS NULL OR (u.created_at, u.id) < ($1::timestamptz, $2::uuid))
       ORDER BY u.created_at DESC, u.id DESC LIMIT $3`,
      [cursor?.createdAt || null, cursor?.id || null, query.limit + 1]
    );
    return pageResult(result.rows, query.limit, (row) => ({ createdAt: row.createdAt, id: row.id }));
  });

  app.delete("/api/v1/admin/users/:id", {
    preHandler: requireRole("admin"),
    config: { rateLimit: { max: 10, timeWindow: "1 hour" } }
  }, async (request, reply) => {
    const params = z.object({ id: z.uuid() }).parse(request.params);
    if (params.id === request.auth.id) {
      return reply.code(400).send({ error: "SELF_DELETE_FORBIDDEN", message: "No puedes eliminar tu propia cuenta desde el panel" });
    }
    const result = await db.transaction((client) => anonymizeUserAccount(client, {
      userId: params.id,
      actorUserId: request.auth.id,
      action: "admin.user_anonymized"
    }));
    if (!result) return reply.code(404).send({ error: "NOT_FOUND", message: "Usuario no encontrado" });
    await removeAvatarFile(
      config.AVATAR_DIRECTORY,
      result.avatarStorageKey,
      result.avatarContentType
    ).catch((error) => {
      request.log.warn({ err: error, personId: result.person_id }, "No se pudo retirar el avatar anonimizado");
    });
    return reply.code(204).send();
  });

  app.get("/api/v1/admin/audit", { preHandler: requireRole("admin") }, async (request) => {
    const query = z.object({
      limit: z.coerce.number().int().min(1).max(100).default(50),
      cursor: z.string().max(500).optional()
    }).parse(request.query);
    const cursor = query.cursor
      ? z.object({ createdAt: z.iso.datetime(), id: z.string().regex(/^[0-9]+$/) }).parse(decodeCursor(query.cursor))
      : null;
    const result = await db.query(
      `SELECT id, action, target_type AS "targetType", target_id AS "targetId",
              metadata, created_at AS "createdAt"
       FROM audit_log
       WHERE ($1::timestamptz IS NULL OR (created_at, id) < ($1::timestamptz, $2::bigint))
       ORDER BY created_at DESC, id DESC LIMIT $3`,
      [cursor?.createdAt || null, cursor?.id || null, query.limit + 1]
    );
    return pageResult(result.rows, query.limit, (row) => ({ createdAt: row.createdAt, id: String(row.id) }));
  });

  return app;
}
