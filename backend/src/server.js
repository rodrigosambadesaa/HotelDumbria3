import { loadConfig } from "./config.js";
import { createDatabase } from "./db.js";
import { createMailer } from "./mailer.js";
import { createOutboxProcessor } from "./outbox.js";
import { buildApp } from "./app.js";

const config = loadConfig();
const db = createDatabase(config.DATABASE_URL);
const mailer = createMailer(config);
const app = await buildApp({ config, db, mailer });
const outbox = createOutboxProcessor({ db, mailer, config, logger: app.log });

async function shutdown(signal) {
  app.log.info({ signal }, "Cerrando el servicio");
  outbox.stop();
  await app.close();
  await db.close();
  process.exit(0);
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));

await app.listen({ host: "0.0.0.0", port: config.PORT });
await outbox.processOne();
