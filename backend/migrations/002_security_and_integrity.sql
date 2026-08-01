DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'session_client') THEN
    CREATE TYPE session_client AS ENUM ('web', 'mobile');
  END IF;
END $$;

ALTER TYPE email_status ADD VALUE IF NOT EXISTS 'dead';

ALTER TABLE users
  ADD COLUMN credential_version integer NOT NULL DEFAULT 1,
  ADD COLUMN failed_login_window_started_at timestamptz;

ALTER TABLE users
  ADD CONSTRAINT users_credential_version_positive CHECK (credential_version > 0),
  ADD CONSTRAINT users_failed_login_attempts_valid CHECK (failed_login_attempts BETWEEN 0 AND 100);

ALTER TABLE refresh_sessions
  ADD COLUMN family_id uuid,
  ADD COLUMN client_type session_client NOT NULL DEFAULT 'mobile',
  ADD COLUMN compromised_at timestamptz,
  ADD COLUMN last_used_at timestamptz;

UPDATE refresh_sessions SET family_id = gen_random_uuid() WHERE family_id IS NULL;
UPDATE refresh_sessions
SET revoked_at = COALESCE(revoked_at, created_at)
WHERE replaced_by IS NOT NULL OR compromised_at IS NOT NULL;
ALTER TABLE refresh_sessions
  ALTER COLUMN family_id SET NOT NULL,
  ALTER COLUMN family_id SET DEFAULT gen_random_uuid(),
  ADD CONSTRAINT refresh_sessions_not_self_replaced CHECK (replaced_by IS NULL OR replaced_by <> id),
  ADD CONSTRAINT refresh_sessions_replacement_is_revoked CHECK (replaced_by IS NULL OR revoked_at IS NOT NULL),
  ADD CONSTRAINT refresh_sessions_compromise_is_revoked CHECK (compromised_at IS NULL OR revoked_at IS NOT NULL),
  ADD CONSTRAINT refresh_sessions_expiry_after_creation CHECK (expires_at > created_at);

CREATE INDEX refresh_sessions_family_idx ON refresh_sessions(family_id);
CREATE INDEX refresh_sessions_active_idx
  ON refresh_sessions(user_id, expires_at)
  WHERE revoked_at IS NULL;
CREATE INDEX users_pagination_idx ON users(created_at DESC, id DESC);

CREATE TABLE password_reset_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash char(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (expires_at > created_at),
  CHECK (consumed_at IS NULL OR consumed_at >= created_at)
);
CREATE INDEX password_reset_tokens_active_idx
  ON password_reset_tokens(user_id, expires_at)
  WHERE consumed_at IS NULL;

ALTER TABLE email_verification_tokens
  ADD CONSTRAINT email_verification_expiry_after_creation CHECK (expires_at > created_at),
  ADD CONSTRAINT email_verification_consumed_after_creation
    CHECK (consumed_at IS NULL OR consumed_at >= created_at);

ALTER TABLE persons ADD COLUMN anonymized_at timestamptz;

ALTER TABLE avatars
  ADD COLUMN width integer,
  ADD COLUMN height integer,
  ADD CONSTRAINT avatars_width_valid CHECK (width IS NULL OR width BETWEEN 1 AND 4096),
  ADD CONSTRAINT avatars_height_valid CHECK (height IS NULL OR height BETWEEN 1 AND 4096);

ALTER TABLE reservations
  ADD COLUMN cancelled_at timestamptz,
  ADD COLUMN cancellation_reason varchar(500),
  ADD CONSTRAINT reservations_maximum_stay CHECK (check_out - check_in <= 30);

UPDATE reservations
SET cancelled_at = COALESCE(cancelled_at, updated_at)
WHERE status = 'cancelled';
ALTER TABLE reservations
  ADD CONSTRAINT reservations_cancellation_consistent CHECK (
    status <> 'cancelled' OR cancelled_at IS NOT NULL
  );

ALTER TABLE reservations DROP CONSTRAINT reservations_person_id_fkey;
ALTER TABLE reservations
  ADD CONSTRAINT reservations_person_id_fkey
  FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE RESTRICT;
CREATE INDEX reservations_pagination_idx ON reservations(created_at DESC, id DESC);

CREATE TABLE reservation_status_history (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  reservation_id uuid NOT NULL REFERENCES reservations(id) ON DELETE RESTRICT,
  actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  previous_status reservation_status NOT NULL,
  new_status reservation_status NOT NULL,
  reason varchar(500),
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (previous_status <> new_status)
);
CREATE INDEX reservation_status_history_reservation_idx
  ON reservation_status_history(reservation_id, created_at DESC);

ALTER TABLE email_outbox
  ADD COLUMN claimed_at timestamptz,
  ADD COLUMN worker_id uuid,
  ADD CONSTRAINT email_outbox_attempts_valid CHECK (attempts BETWEEN 0 AND 20);

ALTER TABLE email_outbox
  ADD CONSTRAINT email_outbox_payload_object CHECK (jsonb_typeof(payload) = 'object');

UPDATE email_outbox
SET status = 'failed', last_error = COALESCE(last_error, 'Recovered during migration')
WHERE status = 'sending';
UPDATE email_outbox SET sent_at = COALESCE(sent_at, created_at) WHERE status = 'sent';

ALTER TABLE email_outbox
  ADD CONSTRAINT email_outbox_claim_consistent CHECK (
    (status = 'sending' AND claimed_at IS NOT NULL AND worker_id IS NOT NULL)
    OR (status <> 'sending' AND claimed_at IS NULL AND worker_id IS NULL)
  ),
  ADD CONSTRAINT email_outbox_sent_consistent CHECK (
    (status = 'sent' AND sent_at IS NOT NULL) OR status <> 'sent'
  );

CREATE INDEX audit_log_pagination_idx ON audit_log(created_at DESC, id DESC);
