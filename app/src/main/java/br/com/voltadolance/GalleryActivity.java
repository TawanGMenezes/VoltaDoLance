package br.com.voltadolance;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.util.Size;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.DateFormat;
import java.util.Date;

public final class GalleryActivity extends Activity {
    private final ExecutorService thumbnails = Executors.newSingleThreadExecutor();
    private final CancellationSignal cancellation = new CancellationSignal();
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
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
                while (count < 100 && cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String name = cursor.getString(1);
                    long seconds = cursor.getLong(2);
                    Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    TextView item = label(name + "\n" +
                            DateFormat.getDateTimeInstance().format(new Date(seconds * 1000L)), 15);
                    LinearLayout row = new LinearLayout(this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    FrameLayout frame = new FrameLayout(this);
                    ImageView thumbnail = new ImageView(this);
                    thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    thumbnail.setBackgroundColor(0xFF254257);
                    thumbnail.setContentDescription("Miniatura do vídeo " + name);
                    frame.addView(thumbnail, new FrameLayout.LayoutParams(-1,-1));
                    TextView play = label("▶",24); play.setGravity(Gravity.CENTER);
                    play.setPadding(0,0,0,0); play.setBackgroundColor(0x44000000);
                    frame.addView(play,new FrameLayout.LayoutParams(-1,-1));
                    row.addView(frame,new LinearLayout.LayoutParams(dp(96),dp(64)));
                    row.addView(item,new LinearLayout.LayoutParams(0,-2,1));
                    row.setContentDescription("Reproduzir " + name);
                    row.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "video/mp4");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try { startActivity(intent); }
                        catch (ActivityNotFoundException e) {
                            Toast.makeText(this,"Instale um reprodutor de vídeo para abrir o lance.",Toast.LENGTH_LONG).show();
                        }
                    });
                    list.addView(row);
                    thumbnails.execute(() -> {
                        try {
                            Bitmap bitmap = getContentResolver().loadThumbnail(uri,new Size(192,128),cancellation);
                            runOnUiThread(() -> {
                                if (!isDestroyed()) thumbnail.setImageBitmap(bitmap);
                                else bitmap.recycle();
                            });
                        } catch (Exception ignored) { /* Playback icon remains if thumbnail unavailable. */ }
                    });
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
    @Override protected void onDestroy() {
        cancellation.cancel(); thumbnails.shutdownNow(); super.onDestroy();
    }
}
