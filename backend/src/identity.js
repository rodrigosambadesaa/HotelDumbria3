const DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";

export function normalizeSpanishDocument(value) {
  return String(value).trim().toUpperCase().replace(/[\s-]/g, "");
}

export function isValidSpanishDocument(value) {
  const normalized = normalizeSpanishDocument(value);
  let numeric;
  let letter;
  if (/^[0-9]{8}[A-Z]$/.test(normalized)) {
    numeric = normalized.slice(0, 8);
    letter = normalized[8];
  } else if (/^[XYZ][0-9]{7}[A-Z]$/.test(normalized)) {
    numeric = `${{ X: "0", Y: "1", Z: "2" }[normalized[0]]}${normalized.slice(1, 8)}`;
    letter = normalized[8];
  } else {
    return false;
  }
  return DNI_LETTERS[Number(numeric) % 23] === letter;
}
