import { loadConfig } from "./config.js";
import { createDatabase } from "./db.js";
import { createMailer } from "./mailer.js";
import { createOutboxProcessor } from "./outbox.js";
import { buildApp } from "./app.js";
import { cleanupOrphanAvatars } from "./avatar-service.js";

const config = loadConfig();
const db = createDatabase(config.DATABASE_URL);
const mailer = createMailer(config);
const app = await buildApp({ config, db, mailer });
const outbox = createOutboxProcessor({ db, mailer, config, logger: app.log });
let shutdownPromise;
const avatarCleanupInterval = setInterval(() => {
  cleanupOrphanAvatars({ db, directory: config.AVATAR_DIRECTORY, logger: app.log })
    .catch((error) => app.log.error({ err: error }, "Falló la limpieza de avatares huérfanos"));
}, 24 * 60 * 60 * 1_000);
avatarCleanupInterval.unref();

function shutdown(signal) {
  if (shutdownPromise) return shutdownPromise;
  shutdownPromise = (async () => {
    app.log.info({ signal }, "Cerrando el servicio");
    const timeout = setTimeout(() => {
      app.log.fatal({ signal }, "El apagado superó el tiempo máximo");
      process.exit(1);
    }, config.SHUTDOWN_TIMEOUT_MS);
    try {
      await outbox.stop();
      clearInterval(avatarCleanupInterval);
      await app.close();
      await db.close();
      process.exitCode = 0;
    } catch (error) {
      app.log.error({ err: error, signal }, "El servicio no pudo cerrarse limpiamente");
      process.exitCode = 1;
    } finally {
      clearTimeout(timeout);
    }
  })();
  return shutdownPromise;
}

process.once("SIGTERM", () => void shutdown("SIGTERM"));
process.once("SIGINT", () => void shutdown("SIGINT"));

await app.listen({ host: "0.0.0.0", port: config.PORT });
await cleanupOrphanAvatars({ db, directory: config.AVATAR_DIRECTORY, logger: app.log });
await outbox.processBatch();
