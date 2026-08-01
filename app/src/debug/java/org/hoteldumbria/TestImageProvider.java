package org.hoteldumbria;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/** Debug-only provider for testing image selection without persistable URI access. */
public final class TestImageProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "image/png";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        MatrixCursor result = new MatrixCursor(new String[]{
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
        });
        result.addRow(new Object[]{"provider-image.png", 1024});
        return result;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (FileOutputStream output = new FileOutputStream(pipe[1].getFileDescriptor())) {
                    Bitmap bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.BLUE);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                    bitmap.recycle();
                } catch (Exception ignored) {
                    // The reader may close early if the test fails.
                } finally {
                    try { pipe[1].close(); } catch (Exception ignored) { }
                }
            }, "test-image-provider").start();
            return pipe[0];
        } catch (Exception error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
}
