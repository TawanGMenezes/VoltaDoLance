package br.com.voltadolance;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;

public final class GalleryActivity extends Activity {
    private TextView label(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(Color.WHITE); view.setPadding(24, 22, 24, 22); return view;
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(0xFF091E2C);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(12, 32, 12, 20);
        TextView title = label("Momentos guardados", 26);
        title.setOnClickListener(v -> finish());
        list.addView(title);
        String[] projection = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED};
        int count = 0;
        try (Cursor cursor = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?",
                new String[]{"Movies/VoltaDoLance/%"},
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String name = cursor.getString(1);
                    long seconds = cursor.getLong(2);
                    Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    TextView item = label("▶  " + name + "\n     " +
                            DateFormat.getDateTimeInstance().format(new Date(seconds * 1000L)), 15);
                    item.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "video/mp4");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    });
                    list.addView(item);
                    View line = new View(this); line.setBackgroundColor(0xFF355164);
                    list.addView(line, new LinearLayout.LayoutParams(-1, 1));
                    count++;
                }
            }
        } catch (Exception e) {
            list.addView(label("Não foi possível ler os seus vídeos: " + e.getMessage(), 14));
        }
        if (count == 0) {
            TextView empty = label("Ainda não há lances guardados.\n\nVolte à câmera, marque um momento e finalize a gravação.", 16);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty);
        }
        scroll.addView(list); setContentView(scroll);
    }
}
