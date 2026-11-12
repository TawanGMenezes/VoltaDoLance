package br.com.voltadolance;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.TextureView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Telas e microtextos inéditos para um protótipo comercial futuro. */
public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xFF091E2C;
    private static final int WHITE = 0xFFF2F6F7;
    private static final int ACCENT = 0xFFFFB765;
    private static final int PERMISSIONS = 82;
    private CameraLoop cameraLoop;
    private OnDeviceVoice voice;
    private TextureView preview;
    private TextView subtitle;
    private TextView stateLabel;
    private Button recordButton;
    private Button stopButton;
    private View shade;
    private boolean dimmed;
    private SharedPreferences preferences;
    private int past = 15;
    private int height = 720;
    private boolean useVoice;

    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable result = new GradientDrawable(); result.setColor(color); result.setCornerRadius(dp(radius));
        return result;
    }
    private Button button(String label, int bgColor, int textColor) {
        Button b = new Button(this); b.setText(label); b.setTextColor(textColor);
        b.setTextSize(14); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(bg(bgColor, 14)); return b;
    }
    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferences = getSharedPreferences("local_settings", MODE_PRIVATE);
        past = preferences.getInt("past", 15);
        height = preferences.getInt("video_height", 720);
        useVoice = preferences.getBoolean("voice", false);
        buildUi();
        cameraLoop = new CameraLoop(this, preview, new CameraLoop.Listener() {
            @Override public void onStatus(String message) {
                stateLabel.setText(message);
                refreshButtons();
            }
            @Override public void onSaved(Uri uri) {
                stateLabel.setText("Vídeo salvo • apenas no seu aparelho");
                Toast.makeText(MainActivity.this, "Momento salvo na galeria!", Toast.LENGTH_LONG).show();
                refreshButtons();
                cameraLoop.start();
            }
            @Override public void onError(String error) {
                stateLabel.setText(error);
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                refreshButtons();
            }
        });
        cameraLoop.setPastSeconds(past);
        cameraLoop.setHeight(height);
        voice = new OnDeviceVoice(this, new OnDeviceVoice.Callback() {
            @Override public void onMark() { cameraLoop.markMoment(); refreshButtons(); }
            @Override public void onFinish() { cameraLoop.finishAndSave(); refreshButtons(); }
            @Override public void onUnavailable() {
                useVoice = false; preferences.edit().putBoolean("voice", false).apply();
                subtitle.setText("Comandos locais indisponíveis • use o botão");
            }
        });
        requestPermissionsIfNeeded();
    }
    private void buildUi() {
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(BACKGROUND);
        preview = new TextureView(this);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL); top.setPadding(dp(16), dp(16), dp(16), dp(16));
        top.setBackground(bg(0xDD091E2C, 16));
        LinearLayout titleBlock = new LinearLayout(this); titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("VOLTA DO LANCE", 18, WHITE, true);
        subtitle = text("Se acontecer, você não perde.", 12, 0xFFC6D7DF, false);
        titleBlock.addView(brand); titleBlock.addView(subtitle);
        top.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1));
        Button gallery = button("Galeria", 0xFF254257, WHITE);
        gallery.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
        top.addView(gallery, new LinearLayout.LayoutParams(dp(94), dp(48)));
        Button settings = button("⚙", 0xFF254257, WHITE);
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(52), dp(48));
        settingsLp.leftMargin = dp(8); top.addView(settings, settingsLp);
        settings.setOnClickListener(v -> openSettings());
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        topLp.setMargins(dp(12), dp(12), dp(12), 0); root.addView(top, topLp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(dp(16), dp(16), dp(16), dp(20));
        bottom.setOrientation(LinearLayout.VERTICAL); bottom.setBackground(bg(0xED091E2C, 22));
        stateLabel = text("Iniciando…", 14, ACCENT, true);
        bottom.addView(stateLabel);
        TextView hint = text("Memória recente de até " + past + " s (alfa: pequenos intervalos possíveis).",
                12, 0xFFC6D7DF, false);
        hint.setId(View.generateViewId()); bottom.addView(hint);
        recordButton = button("GUARDAR MOMENTO", ACCENT, BACKGROUND);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-1, dp(62));
        bLp.topMargin = dp(14); bottom.addView(recordButton, bLp);
        recordButton.setOnClickListener(v -> { cameraLoop.markMoment(); refreshButtons(); });
        stopButton = button("ENCERRAR E SALVAR", 0xFF2B4F60, WHITE);
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(-1, dp(54));
        stopLp.topMargin = dp(8); bottom.addView(stopButton, stopLp);
        stopButton.setOnClickListener(v -> { cameraLoop.finishAndSave(); refreshButtons(); });
        Button dark = button("Ocultar prévia • economizar tela", 0xFF132B3A, WHITE);
        LinearLayout.LayoutParams darkLp = new LinearLayout.LayoutParams(-1, dp(48));
        darkLp.topMargin = dp(10); bottom.addView(dark, darkLp);
        dark.setOnClickListener(v -> toggleShade());
        TextView originalNotice = text("Prévia ativa enquanto esta tela estiver aberta.",
                11, 0xFF9BB0BC, false); originalNotice.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams legalLp = new LinearLayout.LayoutParams(-1, -2);
        legalLp.topMargin = dp(10); bottom.addView(originalNotice, legalLp);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bottomLp.setMargins(dp(12), 0, dp(12), dp(16)); root.addView(bottom, bottomLp);

        TextView black = text("Prévia oculta\n\nA câmera permanece ligada enquanto o app estiver aberto.\n\nToque para voltar", 18, WHITE, true);
        black.setGravity(Gravity.CENTER); black.setBackgroundColor(Color.BLACK);
        black.setOnClickListener(v -> toggleShade()); black.setVisibility(View.GONE);
        shade = black; root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        refreshButtons();
    }
    private void toggleShade() {
        dimmed = !dimmed;
        shade.setVisibility(dimmed ? View.VISIBLE : View.GONE);
        getWindow().getDecorView().setSystemUiVisibility(dimmed ?
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY : 0);
    }
    private void refreshButtons() {
        if (cameraLoop == null || recordButton == null) return;
        boolean active = cameraLoop.isCapturing();
        recordButton.setEnabled(!active); stopButton.setEnabled(active);
        recordButton.setAlpha(active ? 0.4f : 1f); stopButton.setAlpha(active ? 1f : 0.4f);
    }
    private void requestPermissionsIfNeeded() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, PERMISSIONS);
        } else startCapture();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == PERMISSIONS) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startCapture();
            else new AlertDialog.Builder(this).setTitle("Precisamos de sua autorização")
                    .setMessage("Para preparar os segundos anteriores, precisamos usar câmera e microfone enquanto você mantém o aplicativo aberto. Não enviamos vídeos para servidores.")
                    .setPositiveButton("Tentar novamente", (d,w) -> requestPermissionsIfNeeded())
                    .setNegativeButton("Agora não", null).show();
        }
    }
    private void startCapture() {
        cameraLoop.resumeActivity(); cameraLoop.start();
        if (useVoice && !voice.enable()) useVoice = false;
        subtitle.setText(useVoice ? "Ouvido local ativo • diga ‘Marcar momento’" : "Capture com um toque");
    }
    private void openSettings() {
        String[] options = {
            "Histórico antes do lance: " + past + " s",
            "Qualidade: " + (height == 1080 ? "1080p" : "720p") + " / 30 fps",
            "Duração após marcar: 3 min (teste)",
            "Voz local (experimental): " + (useVoice ? "ativada" : "desativada"),
            "Como funciona e privacidade"
        };
        new AlertDialog.Builder(this).setTitle("Ajustar sua captura")
                .setItems(options, (dialog, index) -> {
                    switch (index) {
                        case 0:
                            String[] times = {"15 segundos", "30 segundos", "60 segundos"};
                            new AlertDialog.Builder(this).setTitle("Quanto tempo guardar antes?")
                                .setSingleChoiceItems(times, past == 15 ? 0 : past == 30 ? 1 : 2,
                                    (d, choice) -> {
                                        past = choice == 0 ? 15 : choice == 1 ? 30 : 60;
                                        preferences.edit().putInt("past", past).apply();
                                        cameraLoop.setPastSeconds(past);
                                        d.dismiss();
                                        Toast.makeText(this, "Histórico definido em " + past + " s", Toast.LENGTH_SHORT).show();
                                    }).setNegativeButton("Voltar", null).show();
                            break;
                        case 1:
                            new AlertDialog.Builder(this).setTitle("Qualidade de gravação")
                                .setSingleChoiceItems(new String[]{"720p (menor consumo)", "1080p (mais detalhes)"},
                                    height == 1080 ? 1 : 0, (d, choice) -> {
                                        height = choice == 0 ? 720 : 1080;
                                        preferences.edit().putInt("video_height", height).apply();
                                        cameraLoop.setHeight(height);
                                        d.dismiss();
                                        Toast.makeText(this, "A nova qualidade vale na próxima abertura da câmera", Toast.LENGTH_LONG).show();
                                    }).setNegativeButton("Voltar", null).show();
                            break;
                        case 2:
                            new AlertDialog.Builder(this)
                                .setTitle("Duração do lance nesta versão")
                                .setMessage("Para testar a estabilidade, a gravação após marcar o momento está limitada a 3 minutos. Na versão seguinte, este ajuste oferecerá outras durações e um modo sem limite fixo, com aviso de espaço e bateria.")
                                .setPositiveButton("Entendi", null).show();
                            break;
                        case 3:
                            useVoice = !useVoice;
                            preferences.edit().putBoolean("voice", useVoice).apply();
                            if (useVoice) useVoice = voice.enable(); else voice.disable();
                            subtitle.setText(useVoice ? "Diga ‘Marcar momento’ para gravar" : "Capture com um toque");
                            Toast.makeText(this, useVoice ? "Comando por voz ativado para teste" : "Voz desativada",
                                    Toast.LENGTH_LONG).show();
                            break;
                        default:
                            new AlertDialog.Builder(this).setTitle("Privacidade e limites do teste")
                                .setMessage("Seus vídeos ficam no aparelho e não são transmitidos. A câmera funciona somente com esta tela aberta. O histórico usa pequenos fragmentos e pode conter lacunas entre eles. Comandos de voz dependem do suporte offline do Android e ainda precisam ser testados com a gravação simultânea. Esta versão não cobra nada.")
                                .setPositiveButton("Entendi", null).show();
                    }
                }).setNegativeButton("Fechar", null).show();
    }
    @Override protected void onResume() {
        super.onResume();
        if (cameraLoop != null && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            cameraLoop.resumeActivity(); cameraLoop.start();
            if (voice != null && useVoice) voice.enable();
        }
    }
    @Override protected void onPause() {
        if (voice != null) voice.disable();
        // Não promete funcionamento com tela bloqueada: encerra captura ao sair da tela.
        if (cameraLoop != null) cameraLoop.shutdown();
        if (dimmed) toggleShade();
        super.onPause();
    }
    @Override public void onBackPressed() {
        new AlertDialog.Builder(this).setTitle("Sair da captura?")
            .setMessage("Ao sair, o histórico temporário será descartado. Para guardar seu lance, finalize o vídeo primeiro.")
            .setNegativeButton("Continuar", null)
            .setPositiveButton("Sair", (d,w) -> MainActivity.super.onBackPressed()).show();
    }
}
