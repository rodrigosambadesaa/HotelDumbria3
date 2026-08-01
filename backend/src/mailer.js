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
    async sendRegistrationConfirmation({ email, name, verificationUrl, messageId }) {
      await transport.sendMail({
        from: { address: config.MAIL_FROM, name: config.MAIL_FROM_NAME },
        to: email,
        messageId,
        subject: "Confirma tu cuenta de Hotel Dumbría",
        text: `Hola ${name}. Confirma tu cuenta visitando: ${verificationUrl}`,
        html: `<p>Hola ${escapeHtml(name)},</p>
          <p>Confirma tu cuenta de Hotel Dumbría usando el siguiente enlace:</p>
          <p><a href="${escapeHtml(verificationUrl)}">Confirmar mi cuenta</a></p>
          <p>El enlace caduca en 24 horas.</p>`
      });
    },
    async sendPasswordReset({ email, name, resetUrl, messageId }) {
      await transport.sendMail({
        from: { address: config.MAIL_FROM, name: config.MAIL_FROM_NAME },
        to: email,
        messageId,
        subject: "Recupera el acceso a tu cuenta",
        text: `Hola ${name}. Si solicitaste cambiar tu contraseña, abre: ${resetUrl}`,
        html: `<p>Hola ${escapeHtml(name)},</p>
          <p>Si solicitaste cambiar tu contraseña, utiliza el siguiente enlace:</p>
          <p><a href="${escapeHtml(resetUrl)}">Cambiar mi contraseña</a></p>
          <p>El enlace caduca en una hora. Si no lo solicitaste, ignora este mensaje.</p>`
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
