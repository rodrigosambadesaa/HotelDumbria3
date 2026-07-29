import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadConfig } from "./config.js";
import { createDatabase } from "./db.js";

const directory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../migrations");
const config = loadConfig();
const db = createDatabase(config.DATABASE_URL);

try {
  await db.query(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      filename text PRIMARY KEY,
      applied_at timestamptz NOT NULL DEFAULT now()
    )
  `);
  const applied = new Set(
    (await db.query("SELECT filename FROM schema_migrations")).rows.map((row) => row.filename)
  );
  const files = (await fs.readdir(directory)).filter((name) => name.endsWith(".sql")).sort();
  for (const filename of files) {
    if (applied.has(filename)) continue;
    const sql = await fs.readFile(path.join(directory, filename), "utf8");
    await db.transaction(async (client) => {
      await client.query(sql);
      await client.query("INSERT INTO schema_migrations(filename) VALUES ($1)", [filename]);
    });
    console.info(`Migración aplicada: ${filename}`);
  }
} finally {
  await db.close();
}
