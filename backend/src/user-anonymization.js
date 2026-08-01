import { revokeAllUserSessions } from "./sessions.js";

export async function anonymizeUserAccount(client, { userId, actorUserId, action }) {
  const selected = await client.query(
    `SELECT u.id, u.person_id, p.anonymized_at,
            a.storage_key::text AS "avatarStorageKey", a.content_type AS "avatarContentType"
     FROM users u
     JOIN persons p ON p.id = u.person_id
     LEFT JOIN avatars a ON a.person_id = p.id
     WHERE u.id = $1 FOR UPDATE OF u, p`,
    [userId]
  );
  if (!selected.rowCount) return null;
  const account = selected.rows[0];
  if (account.anonymized_at) {
    return { ...account, idempotent: true };
  }
  await client.query(
    "SELECT pg_advisory_xact_lock(hashtextextended($1, 0))",
    [account.person_id]
  );

  await revokeAllUserSessions(client, userId);
  await client.query(
    "UPDATE email_verification_tokens SET consumed_at = COALESCE(consumed_at, now()) WHERE user_id = $1",
    [userId]
  );
  await client.query(
    "UPDATE password_reset_tokens SET consumed_at = COALESCE(consumed_at, now()) WHERE user_id = $1",
    [userId]
  );
  await client.query("DELETE FROM avatars WHERE person_id = $1", [account.person_id]);
  await client.query(
    `UPDATE users SET disabled_at = COALESCE(disabled_at, now()), password_hash = NULL,
       password_reset_required = true, credential_version = credential_version + 1,
       failed_login_attempts = 0, failed_login_window_started_at = NULL,
       locked_until = NULL, email_verified_at = NULL
     WHERE id = $1`,
    [userId]
  );
  await client.query(
    `UPDATE persons SET national_id = 'ANON-' || left(replace(id::text, '-', ''), 11),
       email = 'deleted+' || id::text || '@invalid.example',
       first_name = 'Cuenta', last_name_1 = 'eliminada', last_name_2 = NULL,
       gender = NULL, street = NULL, street_number = NULL, postal_code = NULL,
       anonymized_at = now()
     WHERE id = $1`,
    [account.person_id]
  );
  await client.query(
    `INSERT INTO audit_log(actor_user_id, action, target_type, target_id)
     VALUES ($1, $2, 'person', $3)`,
    [actorUserId, action, account.person_id]
  );
  return { ...account, idempotent: false };
}
