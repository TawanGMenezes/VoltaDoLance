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
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.hardware.camera2.CameraManager;
import android.widget.EditText;
import java.util.List;
import java.util.Locale;
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
    private Button settingsButton;
    private Button galleryButton;
    private TextView recLabel;
    private TextView recDot;
    private TextView historyHint;
    private TextView savedNotice;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable uiTick = new Runnable() {
        @Override public void run() { refreshButtons(); uiHandler.postDelayed(this, 250); }
    };
    private View shade;
    private boolean dimmed;
    private SharedPreferences preferences;
    private int past = 15;
    private int height = 720;
    private int postMinutes = 10;
    private String modeKey;
    private String startPhrase;
    private String stopPhrase;
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
        modeKey = preferences.getString("video_mode", height == 1080 ? "1920x1080@30" : "1280x720@30");
        postMinutes = preferences.getInt("post_minutes", 10);
        if (postMinutes != 0 && postMinutes != 3 && postMinutes != 10 && postMinutes != 20 && postMinutes != 30 && postMinutes != 60) postMinutes = 10;
        startPhrase = preferences.getString("start_phrase", "Marcar momento");
        stopPhrase = preferences.getString("stop_phrase", "Encerrar vídeo");
        useVoice = preferences.getBoolean("voice", false);
        buildUi();
        cameraLoop = new CameraLoop(this, preview, new CameraLoop.Listener() {
            @Override public void onStatus(String message) {
                stateLabel.setText(message);
                refreshButtons();
            }
            @Override public void onSaved(Uri uri) {
                stateLabel.setText("Vídeo salvo • apenas no seu aparelho");
                savedNotice.setText("✓ Vídeo salvo • abra a Galeria");
                savedNotice.setVisibility(View.VISIBLE);
                uiHandler.postDelayed(() -> savedNotice.setVisibility(View.GONE), 5000);
                Toast.makeText(MainActivity.this, "Momento salvo na galeria!", Toast.LENGTH_LONG).show();
                refreshButtons();
            }
            @Override public void onError(String error) {
                stateLabel.setText(error);
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                refreshButtons();
            }
        });
        cameraLoop.setPastSeconds(past);
        cameraLoop.setMode(modeKey);
        cameraLoop.setPostMinutes(postMinutes);
        voice = new OnDeviceVoice(this, new OnDeviceVoice.Callback() {
            @Override public void onMark() { cameraLoop.markMoment(); refreshButtons(); }
            @Override public void onFinish() { cameraLoop.finishAndSave(); refreshButtons(); }
            @Override public void onUnavailable() {
                useVoice = false; preferences.edit().putBoolean("voice", false).apply();
                subtitle.setText("Comandos locais indisponíveis • use o botão");
            }
        });
        voice.setPhrases(startPhrase, stopPhrase);
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
        Button gallery = button("▶ Galeria", 0xFF254257, WHITE);
        galleryButton = gallery;
        gallery.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
        top.addView(gallery, new LinearLayout.LayoutParams(dp(94), dp(48)));
        Button settings = button("⚙", 0xFF254257, WHITE);
        settingsButton = settings;
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
        savedNotice = text("", 13, 0xFF95E1B5, true);
        savedNotice.setVisibility(View.GONE); bottom.addView(savedNotice);
        TextView hint = text("Memória recente de até " + past + " s (alfa: pequenos intervalos possíveis).",
                12, 0xFFC6D7DF, false);
        hint.setId(View.generateViewId()); bottom.addView(hint);
        historyHint = hint;
        recordButton = button("GUARDAR MOMENTO", ACCENT, BACKGROUND);
        recordButton.setId(R.id.mark_moment);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-1, dp(62));
        bLp.topMargin = dp(14); bottom.addView(recordButton, bLp);
        recordButton.setOnClickListener(v -> { cameraLoop.markMoment(); refreshButtons(); });
        stopButton = button("ENCERRAR E SALVAR", 0xFF2B4F60, WHITE);
        stopButton.setId(R.id.finish_save);
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
        LinearLayout indicator = new LinearLayout(this);
        indicator.setPadding(dp(16), dp(10), dp(16), dp(10));
        indicator.setGravity(Gravity.CENTER); indicator.setBackground(bg(0xED091E2C, 16));
        recDot = text("●  ", 22, 0xFFFF4545, true);
        recLabel = text("PREPARANDO", 18, WHITE, true);
        recLabel.setId(R.id.rec_indicator);
        indicator.addView(recDot); indicator.addView(recLabel);
        FrameLayout.LayoutParams recLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        recLp.topMargin = dp(104); root.addView(indicator, recLp);
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
        CaptureState state = cameraLoop.state();
        boolean active = state.isRecording(), exporting = state.isSaving();
        recordButton.setEnabled(state.canMark()); stopButton.setEnabled(state.canFinish());
        recordButton.setText(exporting ? "SALVANDO…" : active ? "MOMENTO EM GRAVAÇÃO" : "GUARDAR MOMENTO");
        stopButton.setText(exporting ? "AGUARDE O VÍDEO SER SALVO" : "ENCERRAR E SALVAR");
        recordButton.setAlpha(state.canMark() ? 1f : 0.45f);
        stopButton.setAlpha(state.canFinish() ? 1f : 0.45f);
        settingsButton.setEnabled(!active && !exporting);
        galleryButton.setEnabled(!active && !exporting);
        settingsButton.setAlpha(settingsButton.isEnabled() ? 1f : 0.45f);
        galleryButton.setAlpha(galleryButton.isEnabled() ? 1f : 0.45f);
        recDot.setVisibility(active ? View.VISIBLE : View.GONE);
        recDot.setAlpha((SystemClock.elapsedRealtime() / 500) % 2 == 0 ? 1f : 0.2f);
        String label;
        if (active) {
            long seconds = state.elapsed(SystemClock.elapsedRealtime()) / 1000;
            label = String.format(Locale.ROOT, "REC %02d:%02d:%02d", seconds/3600, (seconds/60)%60, seconds%60);
        } else if (exporting) label = "SALVANDO…";
        else if (state.phase() == CaptureState.Phase.BUFFERING) label = "HISTÓRICO ATIVO";
        else if (state.phase() == CaptureState.Phase.SAVED) label = "VÍDEO SALVO";
        else if (state.phase() == CaptureState.Phase.ERROR) label = "CAPTURA PAUSADA";
        else if (state.phase() == CaptureState.Phase.STOPPED) label = "CÂMERA PARADA";
        else label = "PREPARANDO…";
        recLabel.setText(label);
        recLabel.setTextColor(active ? 0xFFFF6565 : WHITE);
        historyHint.setText("Histórico: até " + past + " s • após marcar: " + durationLabel()
                + "\nFragmentos de ~4 s; pequenos intervalos possíveis.");
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
        updateVoiceSubtitle();
    }
    private String durationLabel() { return postMinutes == 0 ? "sem limite fixo" : postMinutes + " min"; }
    private void updateVoiceSubtitle() {
        subtitle.setText(useVoice ? "Voz local: “" + startPhrase + "”" : "Capture com um toque");
    }
    private void openSettings() {
        if (cameraLoop.isCapturing()) return;
        String[] options = {
            "Histórico antes do lance: " + past + " s",
            "Qualidade atual: " + cameraLoop.modeLabel(),
            "Duração após marcar: " + durationLabel(),
            "Voz local: " + (useVoice ? "ativada" : "desativada"),
            "Editar frases de voz",
            "Como funciona e privacidade"
        };
        new AlertDialog.Builder(this).setTitle("Ajustar sua captura")
            .setItems(options, (dialog, index) -> {
                switch (index) {
                    case 0:
                        new AlertDialog.Builder(this).setTitle("Quanto tempo guardar antes?")
                            .setSingleChoiceItems(new String[]{"15 segundos", "30 segundos", "60 segundos"},
                                past == 15 ? 0 : past == 30 ? 1 : 2, (d, choice) -> {
                                    past = new int[]{15,30,60}[choice];
                                    preferences.edit().putInt("past", past).apply();
                                    cameraLoop.setPastSeconds(past); d.dismiss(); refreshButtons();
                                }).setNegativeButton("Voltar", null).show();
                        break;
                    case 1: chooseQuality(); break;
                    case 2:
                        int[] durations = {3,10,20,30,60,0};
                        int selected = 1;
                        for (int i=0; i<durations.length; i++) if (durations[i]==postMinutes) selected=i;
                        new AlertDialog.Builder(this).setTitle("Duração após marcar")
                            .setSingleChoiceItems(new String[]{"3 minutos", "10 minutos", "20 minutos", "30 minutos", "60 minutos", "Sem limite fixo"},
                                selected, (d, choice) -> {
                                    postMinutes=durations[choice]; cameraLoop.setPostMinutes(postMinutes);
                                    preferences.edit().putInt("post_minutes",postMinutes).apply();
                                    d.dismiss(); refreshButtons();
                                    new AlertDialog.Builder(this).setTitle("Proteção da gravação")
                                        .setMessage("A captura tentará encerrar e salvar com pouco espaço, bateria em 5% (fora do carregador) ou aquecimento elevado. Sem limite fixo não significa espaço ou bateria infinitos. Mantenha esta tela aberta.")
                                        .setPositiveButton("Entendi",null).show();
                                }).setNegativeButton("Voltar",null).show();
                        break;
                    case 3:
                        if (useVoice) { voice.disable(); useVoice=false; }
                        else useVoice=voice.enable();
                        preferences.edit().putBoolean("voice",useVoice).apply(); updateVoiceSubtitle();
                        break;
                    case 4: editPhrases(); break;
                    default:
                        new AlertDialog.Builder(this).setTitle("Privacidade e limites do teste")
                            .setMessage("Seus vídeos ficam no aparelho, sem transmissão, assinatura ou cobrança. A câmera funciona com esta tela aberta. Ao sair, um lance marcado será finalizado com tentativa de salvamento; o histórico sem marcação é descartado. Fragmentos de ~4 s podem ter lacunas. Voz exige reconhecimento no dispositivo e pode disputar o microfone: use os botões se indisponível. A qualidade depende da câmera e pode cair para um modo compatível.")
                            .setPositiveButton("Entendi",null).show();
                }
            }).setNegativeButton("Fechar",null).show();
    }
    private void chooseQuality() {
        try {
            CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);
            List<CameraModes.Mode> modes=CameraModes.detect(manager.getCameraCharacteristics(CameraModes.backCamera(manager)));
            if (modes.isEmpty()) throw new IllegalStateException("Nenhuma qualidade disponível");
            String[] labels=new String[modes.size()]; int selected=-1;
            for(int i=0;i<modes.size();i++) {
                labels[i]=modes.get(i).label(); if(modes.get(i).key().equals(modeKey)) selected=i;
            }
            new AlertDialog.Builder(this).setTitle("Modos informados pela câmera")
                .setSingleChoiceItems(labels,selected,(d,choice)->{
                    modeKey=modes.get(choice).key(); preferences.edit().putString("video_mode",modeKey).apply();
                    cameraLoop.setMode(modeKey);
                    cameraLoop.shutdown(); cameraLoop.resumeActivity(); cameraLoop.start();
                    d.dismiss(); Toast.makeText(this,"Qualidade solicitada: "+modes.get(choice).label()+". Se rejeitada, tentaremos um modo inferior.",Toast.LENGTH_LONG).show();
                }).setNegativeButton("Voltar",null).show();
        } catch(Exception e) { Toast.makeText(this,"Não foi possível consultar a câmera: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    private void editPhrases() {
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24),dp(12),dp(24),0);
        form.addView(text("Para marcar o momento",14,BACKGROUND,true));
        EditText start=new EditText(this); start.setSingleLine(true); start.setText(startPhrase); form.addView(start);
        form.addView(text("Para encerrar e salvar",14,BACKGROUND,true));
        EditText stop=new EditText(this); stop.setSingleLine(true); stop.setText(stopPhrase); form.addView(stop);
        TextView help=text("Diga a frase inteira. Acentos e pontuação são ignorados. As frases ficam apenas neste aparelho.",13,BACKGROUND,false);
        form.addView(help);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Suas frases de voz").setView(form)
            .setPositiveButton("Salvar",null).setNegativeButton("Cancelar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            String a=start.getText().toString().trim(),b=stop.getText().toString().trim();
            try {
                voice.setPhrases(a,b); startPhrase=a; stopPhrase=b;
                preferences.edit().putString("start_phrase",a).putString("stop_phrase",b).apply();
                updateVoiceSubtitle(); dialog.dismiss();
            } catch(IllegalArgumentException e) { help.setText(e.getMessage()); help.setTextColor(0xFFBB2222); }
        })); dialog.show();
    }
    @Override protected void onResume() {
        super.onResume();
        uiHandler.removeCallbacks(uiTick); uiHandler.post(uiTick);
        if (cameraLoop != null && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            cameraLoop.resumeActivity(); cameraLoop.start();
            if (voice != null && useVoice) voice.enable();
        }
    }
    @Override protected void onPause() {
        uiHandler.removeCallbacks(uiTick);
        if (voice != null) voice.disable();
        // Não promete funcionamento com tela bloqueada: encerra captura ao sair da tela.
        if (cameraLoop != null) cameraLoop.shutdown();
        if (dimmed) toggleShade();
        super.onPause();
    }
    @Override public void onBackPressed() {
        new AlertDialog.Builder(this).setTitle("Sair da captura?")
            .setMessage("Ao sair, um lance marcado será encerrado com tentativa de salvamento. O histórico sem marcação será descartado.")
            .setNegativeButton("Continuar", null)
            .setPositiveButton("Sair", (d,w) -> MainActivity.super.onBackPressed()).show();
    }
}
