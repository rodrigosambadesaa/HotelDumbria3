import mysql from "mysql2/promise";
import pg from "pg";

const source = await mysql.createConnection(process.env.LEGACY_DATABASE_URL);
const target = new pg.Client({ connectionString: process.env.DATABASE_URL });
await target.connect();

const report = { persons: 0, usersRequiringReset: 0, rooms: 0, reservations: 0, skipped: 0 };

try {
  await target.query("BEGIN");

  const [persons] = await source.query(`
    SELECT nif, email, nombre, apellido_1, apellido_2, sexo, calle,
           numero, codigo_postal, tipo_persona FROM persona
  `);
  for (const person of persons) {
    if (!person.email || !person.nif) { report.skipped++; continue; }
    const role = Number(person.tipo_persona) === 1 ? "admin" : "customer";
    const inserted = await target.query(
      `INSERT INTO persons
       (national_id, email, first_name, last_name_1, last_name_2, gender,
        street, street_number, postal_code, role)
       VALUES ($1, lower($2), $3, $4, $5, $6, $7, $8, $9, $10)
       ON CONFLICT (national_id) DO UPDATE SET
         email=EXCLUDED.email, first_name=EXCLUDED.first_name,
         last_name_1=EXCLUDED.last_name_1, last_name_2=EXCLUDED.last_name_2,
         gender=EXCLUDED.gender, street=EXCLUDED.street,
         street_number=EXCLUDED.street_number, postal_code=EXCLUDED.postal_code
       RETURNING id`,
      [
        String(person.nif).trim().toUpperCase(), String(person.email).trim().toLowerCase(),
        person.nombre || "Sin nombre", person.apellido_1 || "Sin apellido",
        person.apellido_2 || null, person.sexo || null, person.calle || null,
        person.numero == null ? null : String(person.numero),
        person.codigo_postal == null ? null : String(person.codigo_postal).padStart(5, "0"),
        role
      ]
    );
    report.persons++;
    const [legacyUsers] = await source.query("SELECT 1 FROM usuario WHERE nif = ? LIMIT 1", [person.nif]);
    if (legacyUsers.length) {
      await target.query(
        `INSERT INTO users(person_id, password_hash, role, password_reset_required)
         VALUES ($1, NULL, $2, true)
         ON CONFLICT(person_id) DO UPDATE SET password_reset_required=true`,
        [inserted.rows[0].id, role]
      );
      report.usersRequiringReset++;
    }
  }

  const [rooms] = await source.query(
    "SELECT numero, precio, area, num_huespedes, imagen FROM habitacion"
  );
  for (const room of rooms) {
    await target.query(
      `INSERT INTO rooms(id, name, description, price_per_night, area_m2,
                         max_guests, image_url, active)
       VALUES ($1, $2, '', $3, $4, $5, NULL, true)
       ON CONFLICT(id) DO UPDATE SET price_per_night=EXCLUDED.price_per_night,
         area_m2=EXCLUDED.area_m2, max_guests=EXCLUDED.max_guests`,
      [room.numero, `Habitación ${room.numero}`, room.precio, room.area, room.num_huespedes]
    );
    report.rooms++;
  }

  const [reservations] = await source.query(
    "SELECT numero, nif, fecha_entrada, fecha_salida FROM reservashabitaciones"
  );
  for (const booking of reservations) {
    const owner = await target.query("SELECT id FROM persons WHERE national_id=$1", [
      String(booking.nif).trim().toUpperCase()
    ]);
    if (!owner.rowCount) { report.skipped++; continue; }
    try {
      await target.query(
        `INSERT INTO reservations
         (room_id, person_id, check_in, check_out, guests, status, total_price, notes)
         SELECT $1, $2, $3::date, $4::date, 1, 'confirmed',
                ($4::date-$3::date) * price_per_night, 'Importada del sistema legado'
         FROM rooms WHERE id=$1 ON CONFLICT DO NOTHING`,
        [booking.numero, owner.rows[0].id, booking.fecha_entrada, booking.fecha_salida]
      );
      report.reservations++;
    } catch (error) {
      if (error.code === "23P01") report.skipped++;
      else throw error;
    }
  }

  await target.query("COMMIT");
  console.log(JSON.stringify(report));
} catch (error) {
  await target.query("ROLLBACK");
  throw error;
} finally {
  await source.end();
  await target.end();
}
