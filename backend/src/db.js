import pg from "pg";

const { Pool } = pg;

export function createDatabase(connectionString) {
  const pool = new Pool({
    connectionString,
    max: 10,
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 5_000,
    application_name: "hotel-dumbria-api"
  });

  pool.on("error", (error) => {
    console.error({ message: "Unexpected PostgreSQL pool error", error: error.message });
  });

  return {
    query(text, values = []) {
      return pool.query(text, values);
    },
    async transaction(operation) {
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        const result = await operation(client);
        await client.query("COMMIT");
        return result;
      } catch (error) {
        await client.query("ROLLBACK");
        throw error;
      } finally {
        client.release();
      }
    },
    close() {
      return pool.end();
    }
  };
}
