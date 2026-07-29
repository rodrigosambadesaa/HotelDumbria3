import crypto from "node:crypto";
import argon2 from "argon2";
import { SignJWT, jwtVerify } from "jose";

const encoder = new TextEncoder();

export async function hashPassword(password) {
  return argon2.hash(password, {
    type: argon2.argon2id,
    memoryCost: 19_456,
    timeCost: 2,
    parallelism: 1
  });
}

export function verifyPassword(hash, password) {
  return argon2.verify(hash, password, { type: argon2.argon2id });
}

export function passwordNeedsRehash(hash) {
  return argon2.needsRehash(hash, {
    type: argon2.argon2id,
    memoryCost: 19_456,
    timeCost: 2,
    parallelism: 1
  });
}

export function randomToken(bytes = 32) {
  return crypto.randomBytes(bytes).toString("base64url");
}

export function hashToken(token) {
  return crypto.createHash("sha256").update(token).digest("hex");
}

export async function signAccessToken(user, config) {
  const now = Math.floor(Date.now() / 1000);
  return new SignJWT({ role: user.role, email: user.email })
    .setProtectedHeader({ alg: "HS256", typ: "JWT" })
    .setSubject(user.id)
    .setIssuer("hotel-dumbria-api")
    .setAudience("hotel-dumbria-clients")
    .setIssuedAt(now)
    .setExpirationTime(now + config.ACCESS_TOKEN_MINUTES * 60)
    .sign(encoder.encode(config.JWT_SECRET));
}

export async function verifyAccessToken(token, config) {
  const { payload } = await jwtVerify(token, encoder.encode(config.JWT_SECRET), {
    issuer: "hotel-dumbria-api",
    audience: "hotel-dumbria-clients",
    algorithms: ["HS256"]
  });
  return payload;
}

export function tokenExpiry(days) {
  return new Date(Date.now() + days * 86_400_000);
}

export function cookieOptions(config, path = "/api/v1/auth") {
  return {
    httpOnly: true,
    secure: config.COOKIE_SECURE,
    sameSite: "strict",
    path,
    maxAge: config.REFRESH_TOKEN_DAYS * 86_400
  };
}
