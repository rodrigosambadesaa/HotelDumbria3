import crypto from "node:crypto";

export function createOutboxProcessor({ db, mailer, config, logger }) {
  const workerId = crypto.randomUUID();
  let running = false;
  let stopped = false;
  let idlePromise = Promise.resolve();
  let markIdle;

  async function claimOne() {
    return db.transaction(async (client) => {
      await client.query(
        `UPDATE email_outbox
         SET status = 'failed', claimed_at = NULL, worker_id = NULL,
             last_error = 'Procesamiento interrumpido antes de confirmar el envío'
         WHERE status = 'sending' AND claimed_at < now() - interval '10 minutes'
           AND attempts < $1`,
        [config.OUTBOX_MAX_ATTEMPTS]
      );
      await client.query(
        `UPDATE email_outbox SET status = 'dead', claimed_at = NULL, worker_id = NULL,
           payload = '{}'::jsonb
         WHERE status IN ('pending', 'failed', 'sending') AND attempts >= $1`,
        [config.OUTBOX_MAX_ATTEMPTS]
      );
      const selected = await client.query(
        `SELECT id, template, recipient, payload, attempts
         FROM email_outbox
         WHERE status IN ('pending', 'failed') AND next_attempt_at <= now()
           AND attempts < $1
         ORDER BY created_at, id
         FOR UPDATE SKIP LOCKED LIMIT 1`,
        [config.OUTBOX_MAX_ATTEMPTS]
      );
      if (!selected.rowCount) return null;
      const claimed = await client.query(
        `UPDATE email_outbox
         SET status = 'sending', attempts = attempts + 1,
             claimed_at = now(), worker_id = $2
         WHERE id = $1
         RETURNING attempts`,
        [selected.rows[0].id, workerId]
      );
      return { ...selected.rows[0], attempts: claimed.rows[0].attempts };
    });
  }

  async function deliver(message) {
    const messageId = `<${message.id}@hotel-outbox.local>`;
    if (message.template === "registration_confirmation") {
      await mailer.sendRegistrationConfirmation({
        email: message.recipient,
        name: message.payload.name,
        verificationUrl: message.payload.verificationUrl,
        messageId
      });
      return;
    }
    if (message.template === "password_reset") {
      await mailer.sendPasswordReset({
        email: message.recipient,
        name: message.payload.name,
        resetUrl: message.payload.resetUrl,
        messageId
      });
      return;
    }
    throw new Error("Unknown email template");
  }

  async function processOne() {
    const message = await claimOne();
    if (!message) return false;
    try {
      await deliver(message);
      await db.query(
        `UPDATE email_outbox
         SET status = 'sent', sent_at = now(), last_error = NULL,
             claimed_at = NULL, worker_id = NULL, payload = '{}'::jsonb
         WHERE id = $1 AND status = 'sending' AND worker_id = $2`,
        [message.id, workerId]
      );
    } catch (error) {
      logger.warn({ outboxId: message.id, attempt: message.attempts }, "No se pudo enviar un correo de la cola");
      const terminal = message.attempts >= config.OUTBOX_MAX_ATTEMPTS;
      const delaySeconds = Math.min(3600, 30 * (2 ** Math.max(0, message.attempts - 1)));
      await db.query(
        `UPDATE email_outbox
         SET status = CASE WHEN $3 THEN 'dead'::email_status ELSE 'failed'::email_status END,
             next_attempt_at = now() + ($4 * interval '1 second'),
             last_error = $5, claimed_at = NULL, worker_id = NULL,
             payload = CASE WHEN $3 THEN '{}'::jsonb ELSE payload END
         WHERE id = $1 AND worker_id = $2`,
        [message.id, workerId, terminal, delaySeconds, "Error temporal o permanente al enviar el correo"]
      );
    }
    return true;
  }

  async function processBatch() {
    if (running || stopped) return 0;
    running = true;
    idlePromise = new Promise((resolve) => { markIdle = resolve; });
    let processed = 0;
    try {
      while (!stopped && processed < config.OUTBOX_BATCH_SIZE && await processOne()) processed += 1;
      return processed;
    } finally {
      running = false;
      markIdle();
    }
  }

  const interval = setInterval(() => {
    processBatch().catch((error) => logger.error({ err: error }, "Falló el procesador de correo"));
  }, 15_000);
  interval.unref();

  return {
    processOne,
    processBatch,
    async stop() {
      if (stopped) return idlePromise;
      stopped = true;
      clearInterval(interval);
      await idlePromise;
    }
  };
}
