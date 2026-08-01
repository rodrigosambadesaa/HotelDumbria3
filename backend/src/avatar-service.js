import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import sharp from "sharp";

const OUTPUT_CONTENT_TYPE = "image/webp";
const OUTPUT_EXTENSION = "webp";
const MAX_INPUT_DIMENSION = 8_192;
const MAX_INPUT_PIXELS = 25_000_000;
const MAX_OUTPUT_DIMENSION = 1_024;
const ORPHAN_GRACE_MS = 60 * 60 * 1_000;

export function avatarPath(directory, storageKey, contentType = OUTPUT_CONTENT_TYPE) {
  if (!/^[0-9a-f-]{36}$/i.test(storageKey)) throw new Error("Invalid avatar storage key");
  const extension = contentType === "image/jpeg" ? "jpg"
    : contentType === "image/png" ? "png"
      : contentType === OUTPUT_CONTENT_TYPE ? OUTPUT_EXTENSION : null;
  if (!extension) throw new Error("Invalid avatar content type");
  return path.join(directory, `${storageKey}.${extension}`);
}

export async function reconstructAvatar(input) {
  const image = sharp(input, {
    animated: false,
    failOn: "warning",
    limitInputPixels: MAX_INPUT_PIXELS,
    sequentialRead: true
  });
  const metadata = await image.metadata();
  if (!metadata.width || !metadata.height
      || metadata.width > MAX_INPUT_DIMENSION || metadata.height > MAX_INPUT_DIMENSION
      || metadata.width * metadata.height > MAX_INPUT_PIXELS) {
    throw Object.assign(new Error("Image dimensions are not allowed"), {
      statusCode: 413,
      code: "IMAGE_DIMENSIONS_EXCEEDED"
    });
  }
  const { data, info } = await image
    .rotate()
    .resize({
      width: MAX_OUTPUT_DIMENSION,
      height: MAX_OUTPUT_DIMENSION,
      fit: "inside",
      withoutEnlargement: true
    })
    .webp({ quality: 82, effort: 4, smartSubsample: true })
    .toBuffer({ resolveWithObject: true });
  return {
    buffer: data,
    contentType: OUTPUT_CONTENT_TYPE,
    width: info.width,
    height: info.height,
    sha256: crypto.createHash("sha256").update(data).digest("hex")
  };
}

export async function storeAvatarFile(directory, storageKey, buffer) {
  await fs.mkdir(directory, { recursive: true, mode: 0o700 });
  const target = avatarPath(directory, storageKey);
  await fs.writeFile(target, buffer, { mode: 0o600, flag: "wx" });
  return target;
}

export async function removeAvatarFile(directory, storageKey, contentType = OUTPUT_CONTENT_TYPE) {
  if (!storageKey) return;
  await fs.unlink(avatarPath(directory, storageKey, contentType)).catch((error) => {
    if (error.code !== "ENOENT") throw error;
  });
}

export async function cleanupOrphanAvatars({ db, directory, logger, now = Date.now() }) {
  await fs.mkdir(directory, { recursive: true, mode: 0o700 });
  const referenced = await db.query("SELECT storage_key::text FROM avatars");
  const activeKeys = new Set(referenced.rows.map((row) => row.storage_key));
  const entries = await fs.readdir(directory, { withFileTypes: true });
  let removed = 0;
  for (const entry of entries) {
    const match = /^([0-9a-f-]{36})\.(?:webp|png|jpg)$/i.exec(entry.name);
    if (!entry.isFile() || !match || activeKeys.has(match[1])) continue;
    const target = path.join(directory, entry.name);
    const stats = await fs.stat(target).catch(() => null);
    if (!stats || now - stats.mtimeMs < ORPHAN_GRACE_MS) continue;
    await fs.unlink(target).catch((error) => {
      if (error.code !== "ENOENT") throw error;
    });
    removed += 1;
  }
  if (removed) logger?.info({ removed }, "Avatares huérfanos eliminados");
  return removed;
}
