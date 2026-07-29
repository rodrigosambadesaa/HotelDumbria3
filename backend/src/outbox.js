export function createOutboxProcessor({ db, mailer, config, logger }) {
  let running = false;

  async function processOne() {
    if (running) return;
    running = true;
    let message;
    try {
      message = await db.transaction(async (client) => {
        const result = await client.query(
          `SELECT id, template, recipient, payload
           FROM email_outbox
           WHERE status IN ('pending', 'failed') AND next_attempt_at <= now()
           ORDER BY created_at
           FOR UPDATE SKIP LOCKED LIMIT 1`
        );
        if (!result.rowCount) return null;
        await client.query(
          "UPDATE email_outbox SET status = 'sending', attempts = attempts + 1 WHERE id = $1",
          [result.rows[0].id]
        );
        return result.rows[0];
      });
      if (!message) return;
      if (message.template !== "registration_confirmation") {
        throw new Error("Plantilla de correo desconocida");
      }
      await mailer.sendRegistrationConfirmation({
        email: message.recipient,
        name: message.payload.name,
        verificationUrl: message.payload.verificationUrl
      });
      await db.query(
        "UPDATE email_outbox SET status = 'sent', sent_at = now(), last_error = NULL WHERE id = $1",
        [message.id]
      );
    } catch (error) {
      logger.warn({ err: error, outboxId: message?.id }, "No se pudo enviar un correo de la cola");
      if (message) {
        await db.query(
          `UPDATE email_outbox
           SET status = 'failed',
               next_attempt_at = now() + make_interval(secs => LEAST(3600, 30 * power(2, attempts)::int)),
               last_error = left($2, 500)
           WHERE id = $1`,
          [message.id, "Error SMTP temporal"]
        );
      }
    } finally {
      running = false;
    }
  }

  const interval = setInterval(processOne, 15_000);
  interval.unref();

  return {
    processOne,
    stop() {
      clearInterval(interval);
    }
  };
}
