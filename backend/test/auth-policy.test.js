import assert from "node:assert/strict";
import test from "node:test";

import { isLocked, nextFailedLoginState } from "../src/auth-policy.js";

const start = new Date("2026-08-01T10:00:00.000Z");
const policy = { threshold: 5, windowMinutes: 15, lockMinutes: 15 };

test("menos de cinco fallos no bloquean y el quinto fija un único bloqueo", () => {
  let state = { failedLoginAttempts: 0, failedLoginWindowStartedAt: null, lockedUntil: null };
  for (let attempt = 1; attempt <= 5; attempt += 1) {
    state = nextFailedLoginState(state, new Date(start.getTime() + attempt * 1000), policy);
    assert.equal(state.failedLoginAttempts, attempt);
    assert.equal(state.locked, attempt === 5);
  }
  assert.equal(state.lockedUntil.toISOString(), "2026-08-01T10:15:05.000Z");
});

test("los intentos durante el bloqueo no lo prolongan", () => {
  const locked = {
    failedLoginAttempts: 5,
    failedLoginWindowStartedAt: start,
    lockedUntil: new Date("2026-08-01T10:15:00.000Z")
  };
  const next = nextFailedLoginState(locked, new Date("2026-08-01T10:10:00.000Z"), policy);
  assert.equal(next.changed, false);
  assert.equal(next.lockedUntil.toISOString(), "2026-08-01T10:15:00.000Z");
});

test("tras expirar, un nuevo fallo inicia otra ventana y un login puede continuar", () => {
  const expired = {
    failedLoginAttempts: 5,
    failedLoginWindowStartedAt: start,
    lockedUntil: new Date("2026-08-01T10:15:00.000Z")
  };
  const now = new Date("2026-08-01T10:15:01.000Z");
  assert.equal(isLocked(expired, now), false);
  const next = nextFailedLoginState(expired, now, policy);
  assert.equal(next.failedLoginAttempts, 1);
  assert.equal(next.failedLoginWindowStartedAt, now);
  assert.equal(next.lockedUntil, null);
});
