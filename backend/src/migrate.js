import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadConfig } from "./config.js";
import { createDatabase } from "./db.js";

const directory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../migrations");
const config = loadConfig();
const db = createDatabase(config.DATABASE_URL);

try {
  await db.withClient(async (client) => {
    await client.query("SELECT pg_advisory_lock(hashtext('hotel_schema_migrations'))");
    try {
      await client.query(`
        CREATE TABLE IF NOT EXISTS schema_migrations (
          filename text PRIMARY KEY,
          applied_at timestamptz NOT NULL DEFAULT now()
        )
      `);
      const applied = new Set(
        (await client.query("SELECT filename FROM schema_migrations")).rows.map((row) => row.filename)
      );
      const files = (await fs.readdir(directory)).filter((name) => name.endsWith(".sql")).sort();
      for (const filename of files) {
        if (applied.has(filename)) continue;
        const sql = await fs.readFile(path.join(directory, filename), "utf8");
        await client.query("BEGIN");
        try {
          await client.query(sql);
          await client.query("INSERT INTO schema_migrations(filename) VALUES ($1)", [filename]);
          await client.query("COMMIT");
        } catch (error) {
          await client.query("ROLLBACK");
          throw error;
        }
        console.info(`Migración aplicada: ${filename}`);
      }
    } finally {
      await client.query("SELECT pg_advisory_unlock(hashtext('hotel_schema_migrations'))");
    }
  });
} finally {
  await db.close();
}
