export function encodeCursor(value) {
  return Buffer.from(JSON.stringify(value), "utf8").toString("base64url");
}

export function decodeCursor(cursor) {
  if (!cursor) return null;
  try {
    if (typeof cursor !== "string" || cursor.length > 500) throw new Error("size");
    const decoded = JSON.parse(Buffer.from(cursor, "base64url").toString("utf8"));
    if (!decoded || typeof decoded !== "object" || Array.isArray(decoded)) throw new Error("shape");
    return decoded;
  } catch {
    const error = new Error("Invalid cursor");
    error.statusCode = 400;
    error.code = "INVALID_CURSOR";
    throw error;
  }
}

export function pageResult(rows, limit, cursorFromRow) {
  const hasMore = rows.length > limit;
  const data = hasMore ? rows.slice(0, limit) : rows;
  return {
    data,
    nextCursor: hasMore ? encodeCursor(cursorFromRow(data[data.length - 1])) : null
  };
}
