package br.com.voltadolance;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Publica MP4 apenas quando completamente pronto (IS_PENDING). */
public final class VideoStore {
    private VideoStore() { }
    public static Uri save(Context context, File source) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "Lance_" + stamp + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VoltaDoLance/");
        values.put(MediaStore.Video.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Não foi possível criar arquivo na galeria");
        boolean success = false;
        try {
            try (FileInputStream input = new FileInputStream(source);
                 OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("Sem acesso ao destino");
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = input.read(buf)) != -1) output.write(buf, 0, n);
                output.flush();
            }
            ContentValues complete = new ContentValues();
            complete.put(MediaStore.Video.Media.IS_PENDING, 0);
            resolver.update(uri, complete, null, null);
            success = true;
            return uri;
        } finally {
            if (!success) resolver.delete(uri, null, null);
        }
    }
}
