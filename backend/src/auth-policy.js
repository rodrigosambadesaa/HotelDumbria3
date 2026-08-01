export function isLocked(state, now = new Date()) {
  return Boolean(state.lockedUntil && new Date(state.lockedUntil).getTime() > now.getTime());
}

/**
 * Account policy: failures are counted in a fixed window. The lock is created
 * exactly when the threshold is reached. Requests received while locked do not
 * change the counter or move lockedUntil. After expiry, the next failure starts
 * a new window at one.
 */
export function nextFailedLoginState(state, now = new Date(), policy = {}) {
  const threshold = policy.threshold ?? 5;
  const windowMinutes = policy.windowMinutes ?? 15;
  const lockMinutes = policy.lockMinutes ?? 15;
  if (isLocked(state, now)) {
    return {
      failedLoginAttempts: state.failedLoginAttempts ?? 0,
      failedLoginWindowStartedAt: state.failedLoginWindowStartedAt ?? null,
      lockedUntil: new Date(state.lockedUntil),
      changed: false,
      locked: true
    };
  }

  const windowStarted = state.failedLoginWindowStartedAt
    ? new Date(state.failedLoginWindowStartedAt)
    : null;
  const windowExpired = !windowStarted
    || now.getTime() - windowStarted.getTime() >= windowMinutes * 60_000
    || (state.lockedUntil && new Date(state.lockedUntil).getTime() <= now.getTime());
  const attempts = windowExpired ? 1 : (state.failedLoginAttempts ?? 0) + 1;
  const startedAt = windowExpired ? now : windowStarted;
  const reachedThreshold = attempts >= threshold;

  return {
    failedLoginAttempts: attempts,
    failedLoginWindowStartedAt: startedAt,
    lockedUntil: reachedThreshold
      ? new Date(now.getTime() + lockMinutes * 60_000)
      : null,
    changed: true,
    locked: reachedThreshold
  };
}

export function loginPolicyFromConfig(config) {
  return {
    threshold: config.LOGIN_FAILURE_THRESHOLD,
    windowMinutes: config.LOGIN_FAILURE_WINDOW_MINUTES,
    lockMinutes: config.LOGIN_LOCK_MINUTES
  };
}
