import { loadConfig } from "./config.js";
import { createDatabase } from "./db.js";
import { hashPassword } from "./auth.js";

const config = loadConfig();
const email = process.env.ADMIN_BOOTSTRAP_EMAIL;
const password = process.env.ADMIN_BOOTSTRAP_PASSWORD;
const db = createDatabase(config.DATABASE_URL);

if (!email || !password || password.length < 14) {
  throw new Error("ADMIN_BOOTSTRAP_EMAIL y una contraseña de al menos 14 caracteres son obligatorios");
}

try {
  const passwordHash = await hashPassword(password);
  await db.transaction(async (client) => {
    const person = await client.query(
      `INSERT INTO persons (national_id, email, first_name, last_name_1, role)
       VALUES ($1, lower($2), $3, $4, 'admin')
       ON CONFLICT (email) DO UPDATE SET role = 'admin'
       RETURNING id`,
      ["ADMIN0000", email, "Administración", "Hotel Dumbría"]
    );
    await client.query(
      `INSERT INTO users (person_id, password_hash, role, email_verified_at)
       VALUES ($1, $2, 'admin', now())
       ON CONFLICT (person_id) DO UPDATE
       SET role = 'admin'`,
      [person.rows[0].id, passwordHash]
    );
  });
  console.info(`Administrador preparado: ${email}`);
} finally {
  await db.close();
}
