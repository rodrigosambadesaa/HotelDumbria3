import assert from "node:assert/strict";
import test from "node:test";

import { isValidSpanishDocument, normalizeSpanishDocument } from "../src/identity.js";

test("normaliza y valida DNI y NIE españoles", () => {
  assert.equal(normalizeSpanishDocument(" 12345678-z "), "12345678Z");
  assert.equal(isValidSpanishDocument("12345678Z"), true);
  assert.equal(isValidSpanishDocument("X1234567L"), true);
  assert.equal(isValidSpanishDocument("Y1234567X"), true);
  assert.equal(isValidSpanishDocument("12345678A"), false);
  assert.equal(isValidSpanishDocument("PASAPORTE"), false);
});
