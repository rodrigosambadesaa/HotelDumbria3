import assert from "node:assert/strict";
import test from "node:test";

import { decodeCursor, encodeCursor, pageResult } from "../src/pagination.js";

test("los cursores son opacos, limitados y producen páginas estables", () => {
  const value = { createdAt: "2026-08-01T10:00:00.000Z", id: "abc" };
  assert.deepEqual(decodeCursor(encodeCursor(value)), value);
  assert.throws(() => decodeCursor("%%%"), /Invalid cursor/);
  assert.throws(() => decodeCursor("a".repeat(501)), /Invalid cursor/);
  const page = pageResult([{ id: 3 }, { id: 2 }, { id: 1 }], 2, (row) => ({ id: row.id }));
  assert.deepEqual(page.data, [{ id: 3 }, { id: 2 }]);
  assert.deepEqual(decodeCursor(page.nextCursor), { id: 2 });
});
