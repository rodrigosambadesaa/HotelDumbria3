import { z } from "zod";
import { isIP } from "node:net";

const booleanFromString = z
  .enum(["true", "false"])
  .default("false")
  .transform((value) => value === "true");

const schema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  PORT: z.coerce.number().int().min(1).max(65535).default(8080),
  DATABASE_URL: z.string().min(1),
  JWT_SECRET: z.string().min(32),
  AUDIT_HMAC_SECRET: z.string().min(32),
  ACCESS_TOKEN_MINUTES: z.coerce.number().int().min(5).max(60).default(15),
  REFRESH_TOKEN_DAYS: z.coerce.number().int().min(1).max(90).default(30),
  PUBLIC_BASE_URL: z.string().url(),
  ADMIN_WEB_ORIGIN: z.string().url(),
  CORS_ALLOWED_ORIGINS: z.string().optional().default(""),
  TRUSTED_PROXIES: z.string().optional().default(""),
  COOKIE_SECURE: booleanFromString,
  SMTP_HOST: z.string().min(1),
  SMTP_PORT: z.coerce.number().int().min(1).max(65535),
  SMTP_SECURE: booleanFromString,
  SMTP_USER: z.string().optional().default(""),
  SMTP_PASSWORD: z.string().optional().default(""),
  MAIL_FROM: z.string().email(),
  MAIL_FROM_NAME: z.string().min(1).default("Hotel Dumbría"),
  AVATAR_DIRECTORY: z.string().min(1).default("/data/avatars"),
  HOTEL_TIME_ZONE: z.string().min(1).default("Europe/Madrid"),
  MAX_RESERVATION_NIGHTS: z.coerce.number().int().min(1).max(30).default(30),
  BOOKING_HORIZON_MONTHS: z.coerce.number().int().min(1).max(36).default(18),
  CANCELLATION_NOTICE_HOURS: z.coerce.number().int().min(0).max(720).default(48),
  CHECK_IN_HOUR_LOCAL: z.coerce.number().int().min(0).max(23).default(15),
  LOGIN_FAILURE_THRESHOLD: z.coerce.number().int().min(3).max(20).default(5),
  LOGIN_FAILURE_WINDOW_MINUTES: z.coerce.number().int().min(1).max(120).default(15),
  LOGIN_LOCK_MINUTES: z.coerce.number().int().min(1).max(120).default(15),
  OUTBOX_BATCH_SIZE: z.coerce.number().int().min(1).max(100).default(20),
  OUTBOX_MAX_ATTEMPTS: z.coerce.number().int().min(1).max(20).default(6),
  SHUTDOWN_TIMEOUT_MS: z.coerce.number().int().min(1000).max(60_000).default(10_000),
  LOG_LEVEL: z.enum(["fatal", "error", "warn", "info", "debug", "trace"]).default("info")
});

export function loadConfig(environment = process.env) {
  const parsed = schema.safeParse(environment);
  if (!parsed.success) {
    const fields = parsed.error.issues.map((issue) => issue.path.join(".")).join(", ");
    throw new Error(`Configuración inválida o ausente: ${fields}`);
  }
  const splitList = (value) => value.split(",").map((item) => item.trim()).filter(Boolean);
  const allowedOrigins = splitList(parsed.data.CORS_ALLOWED_ORIGINS);
  if (!allowedOrigins.length) allowedOrigins.push(parsed.data.ADMIN_WEB_ORIGIN);
  for (const origin of allowedOrigins) {
    let parsedOrigin;
    try {
      parsedOrigin = new URL(origin);
    } catch {
      throw new Error("Configuración inválida: CORS_ALLOWED_ORIGINS");
    }
    if (!['http:', 'https:'].includes(parsedOrigin.protocol) || parsedOrigin.origin !== origin) {
      throw new Error("Configuración inválida: CORS_ALLOWED_ORIGINS debe contener orígenes exactos");
    }
  }
  const trustedProxies = splitList(parsed.data.TRUSTED_PROXIES);
  for (const proxy of trustedProxies) {
    const [address, prefix, extra] = proxy.split("/");
    const family = isIP(address);
    const maxPrefix = family === 4 ? 32 : 128;
    if (!family || extra !== undefined || (prefix !== undefined
        && (!/^\d+$/.test(prefix) || Number(prefix) > maxPrefix))) {
      throw new Error("Configuración inválida: TRUSTED_PROXIES debe contener IP o CIDR exactos");
    }
  }
  if (parsed.data.NODE_ENV === "production") {
    if (!parsed.data.COOKIE_SECURE
        || !parsed.data.PUBLIC_BASE_URL.startsWith("https://")
        || allowedOrigins.some((origin) => !origin.startsWith("https://"))) {
      throw new Error("Configuración de producción inválida: HTTPS y cookies Secure son obligatorios");
    }
    if (!trustedProxies.length) {
      throw new Error("Configuración de producción inválida: TRUSTED_PROXIES es obligatorio");
    }
  }
  return {
    ...parsed.data,
    ADMIN_WEB_ORIGINS: allowedOrigins,
    TRUSTED_PROXIES: trustedProxies
  };
}
