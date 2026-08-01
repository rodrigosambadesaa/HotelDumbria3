import crypto from "node:crypto";

import { hashToken, randomToken, tokenExpiry } from "./auth.js";

function sessionUserAgentHash(userAgent, config) {
  return crypto.createHmac("sha256", config.AUDIT_HMAC_SECRET)
    .update(userAgent || "unknown")
    .digest("hex");
}

export async function createRefreshSession({
  client,
  config,
  user,
  clientType,
  userAgent,
  familyId = crypto.randomUUID()
}) {
  const refreshToken = randomToken(48);
  const result = await client.query(
    `INSERT INTO refresh_sessions
       (user_id, token_hash, user_agent_hash, expires_at, family_id, client_type)
     VALUES ($1, $2, $3, $4, $5, $6)
     RETURNING id, family_id AS "familyId", client_type AS "clientType"`,
    [
      user.id,
      hashToken(refreshToken),
      sessionUserAgentHash(userAgent, config),
      tokenExpiry(config.REFRESH_TOKEN_DAYS),
      familyId,
      clientType
    ]
  );
  return { ...result.rows[0], refreshToken };
}

export async function rotateRefreshSession({
  db,
  config,
  refreshToken,
  clientType,
  userAgent
}) {
  return db.transaction(async (client) => {
    const found = await client.query(
      `SELECT s.id, s.user_id, s.family_id, s.client_type, s.expires_at,
              s.revoked_at, s.replaced_by, s.compromised_at,
              u.person_id, u.role, u.disabled_at, u.credential_version,
              p.email
       FROM refresh_sessions s
       JOIN users u ON u.id = s.user_id
       JOIN persons p ON p.id = u.person_id
       WHERE s.token_hash = $1
       FOR UPDATE OF s, u`,
      [hashToken(refreshToken)]
    );
    if (!found.rowCount) return { status: "invalid" };

    const session = found.rows[0];
    const reused = session.revoked_at || session.replaced_by || session.compromised_at;
    if (reused || session.client_type !== clientType) {
      await client.query(
        `UPDATE refresh_sessions
         SET revoked_at = COALESCE(revoked_at, now()), compromised_at = COALESCE(compromised_at, now())
         WHERE family_id = $1`,
        [session.family_id]
      );
      return { status: "reused", userId: session.user_id, familyId: session.family_id };
    }
    if (session.disabled_at || new Date(session.expires_at).getTime() <= Date.now()) {
      await client.query(
        "UPDATE refresh_sessions SET revoked_at = COALESCE(revoked_at, now()) WHERE id = $1",
        [session.id]
      );
      return { status: "invalid", userId: session.user_id };
    }

    const user = {
      id: session.user_id,
      person_id: session.person_id,
      role: session.role,
      email: session.email,
      credential_version: session.credential_version
    };
    const replacement = await createRefreshSession({
      client,
      config,
      user,
      clientType,
      userAgent,
      familyId: session.family_id
    });
    await client.query(
      `UPDATE refresh_sessions
       SET revoked_at = now(), replaced_by = $2, last_used_at = now()
       WHERE id = $1 AND revoked_at IS NULL AND replaced_by IS NULL`,
      [session.id, replacement.id]
    );
    return { status: "rotated", user, session: replacement };
  });
}

export function revokeAllUserSessions(client, userId, { compromised = false } = {}) {
  return client.query(
    `UPDATE refresh_sessions
     SET revoked_at = COALESCE(revoked_at, now()),
         compromised_at = CASE WHEN $2 THEN COALESCE(compromised_at, now()) ELSE compromised_at END
     WHERE user_id = $1`,
    [userId, compromised]
  );
}

export function revokeRefreshToken(client, refreshToken, clientType) {
  return client.query(
    `UPDATE refresh_sessions SET revoked_at = COALESCE(revoked_at, now())
     WHERE token_hash = $1 AND client_type = $2`,
    [hashToken(refreshToken), clientType]
  );
}
