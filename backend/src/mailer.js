import nodemailer from "nodemailer";

export function createMailer(config) {
  const transport = nodemailer.createTransport({
    host: config.SMTP_HOST,
    port: config.SMTP_PORT,
    secure: config.SMTP_SECURE,
    auth: config.SMTP_USER
      ? { user: config.SMTP_USER, pass: config.SMTP_PASSWORD }
      : undefined,
    disableFileAccess: true,
    disableUrlAccess: true
  });

  return {
    async sendRegistrationConfirmation({ email, name, verificationUrl }) {
      await transport.sendMail({
        from: { address: config.MAIL_FROM, name: config.MAIL_FROM_NAME },
        to: email,
        subject: "Confirma tu cuenta de Hotel Dumbría",
        text: `Hola ${name}. Confirma tu cuenta visitando: ${verificationUrl}`,
        html: `<p>Hola ${escapeHtml(name)},</p>
          <p>Confirma tu cuenta de Hotel Dumbría usando el siguiente enlace:</p>
          <p><a href="${escapeHtml(verificationUrl)}">Confirmar mi cuenta</a></p>
          <p>El enlace caduca en 24 horas.</p>`
      });
    }
  };
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
