import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import sharp from "sharp";

import {
  avatarPath,
  cleanupOrphanAvatars,
  reconstructAvatar,
  storeAvatarFile
} from "../src/avatar-service.js";

async function encoded(format, width = 32, height = 24) {
  return sharp({
    create: { width, height, channels: 3, background: { r: 30, g: 100, b: 180 } }
  }).toFormat(format).toBuffer();
}

for (const format of ["jpeg", "png", "webp"]) {
  test(`reconstruye ${format} como WebP controlado`, async () => {
    const result = await reconstructAvatar(await encoded(format));
    const metadata = await sharp(result.buffer).metadata();
    assert.equal(result.contentType, "image/webp");
    assert.equal(metadata.format, "webp");
    assert.equal(result.width, 32);
    assert.equal(result.height, 24);
  });
}

test("rechaza bytes falsos y dimensiones excesivas", async () => {
  await assert.rejects(reconstructAvatar(Buffer.from("not-an-image")));
  await assert.rejects(
    reconstructAvatar(await encoded("png", 8_193, 1)),
    (error) => error.code === "IMAGE_DIMENSIONS_EXCEEDED"
  );
});

test("elimina metadatos y limita las dimensiones de salida", async () => {
  const source = await sharp({
    create: { width: 1_600, height: 1_200, channels: 3, background: "red" }
  }).jpeg().withMetadata({ exif: { IFD0: { Artist: "sensitive-location" } } }).toBuffer();
  const result = await reconstructAvatar(source);
  const metadata = await sharp(result.buffer).metadata();
  assert.equal(metadata.width, 1_024);
  assert.equal(metadata.height, 768);
  assert.equal(metadata.exif, undefined);
  assert.equal(metadata.gps, undefined);
});

test("las rutas no admiten traversal y el limpiador conserva archivos referenciados", async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), "avatars-"));
  const active = "01234567-89ab-4cde-8fab-0123456789ab";
  const orphan = "11234567-89ab-4cde-8fab-0123456789ab";
  try {
    await storeAvatarFile(directory, active, Buffer.from("active"));
    await storeAvatarFile(directory, orphan, Buffer.from("orphan"));
    const old = new Date(Date.now() - 2 * 60 * 60 * 1_000);
    await fs.utimes(avatarPath(directory, active), old, old);
    await fs.utimes(avatarPath(directory, orphan), old, old);
    const removed = await cleanupOrphanAvatars({
      db: { query: async () => ({ rows: [{ storage_key: active }] }) },
      directory,
      now: Date.now()
    });
    assert.equal(removed, 1);
    assert.equal((await fs.readFile(avatarPath(directory, active))).toString(), "active");
    await assert.rejects(fs.access(avatarPath(directory, orphan)));
    assert.throws(() => avatarPath(directory, "../escape"));
  } finally {
    await fs.rm(directory, { recursive: true, force: true });
  }
});
