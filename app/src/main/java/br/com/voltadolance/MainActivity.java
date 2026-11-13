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
import android.media.ToneGenerator;
import android.media.AudioManager;
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
public final class MainActivity extends ThemedActivity {
    private int BACKGROUND;
    private int WHITE;
    private static final int ACCENT = 0xFFF37D68;
    private static final int PERMISSIONS = 82;
    private CameraLoop cameraLoop;
    private volatile OnDeviceVoice voice;
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
    private TextView voiceLabel;
    private Button pauseButton;
    private ToneGenerator tones;
    private CaptureState.Phase lastSoundPhase = CaptureState.Phase.PREPARING;
    private String voiceStatus = "Voz desligada";
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
    private boolean sounds = true, foreground, navigating;
    private AlertDialog noticeDialog;

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
        BACKGROUND=palette.surface; WHITE=palette.text;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferences = getSharedPreferences("local_settings", MODE_PRIVATE);
        loadPreferences();
        buildUi();
        try { tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 75); } catch (RuntimeException ignored) { }
        cameraLoop = new CameraLoop(this, preview, new CameraLoop.Listener() {
            @Override public void onStatus(String message) {
                stateLabel.setText(message);
                refreshButtons();
            }
            @Override public void onSaved(Uri uri) {
                stateLabel.setText("Vídeo salvo • apenas no seu aparelho" +
                        (cameraLoop.completionReason().isEmpty() ? "" : " • " + cameraLoop.completionReason()));
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
            @Override public void onPcm(byte[] pcm, int length) {
                OnDeviceVoice current = voice;
                if (current != null) current.onPcm(pcm, length);
            }
        });
        cameraLoop.setPastSeconds(past);
        cameraLoop.setMode(modeKey);
        cameraLoop.setPostMinutes(postMinutes);
        voice = new OnDeviceVoice(this, new OnDeviceVoice.Callback() {
            @Override public void onMark() { if (foreground && !navigating) { cameraLoop.markMoment(); refreshButtons(); } }
            @Override public void onFinish() { if (foreground && !navigating) { cameraLoop.finishAndSave(); refreshButtons(); } }
            @Override public void onUnavailable() {
                Toast.makeText(MainActivity.this, "A voz está tentando se recuperar. Confira o estado da escuta na tela; os botões continuam disponíveis.", Toast.LENGTH_LONG).show();
            }
            @Override public void onState(String status) {
                voiceStatus = status; updateVoiceSubtitle();
            }
        });
        voice.setPhrases(startPhrase, stopPhrase);
        if(savedInstanceState!=null && savedInstanceState.getBoolean("manual_pause")) cameraLoop.pauseHistory();
        refreshButtons();
        requestPermissionsIfNeeded();
    }
    private void loadPreferences() {
        CaptureSettings settings=new CaptureSettings(preferences);
        boolean phrasesChanged=!settings.start.equals(startPhrase)||!settings.stop.equals(stopPhrase);
        past=settings.past;modeKey=settings.mode;postMinutes=settings.postMinutes;
        startPhrase=settings.start;stopPhrase=settings.stop;useVoice=settings.voice;sounds=settings.sounds;
        if(cameraLoop!=null){cameraLoop.setPastSeconds(past);cameraLoop.setMode(modeKey);cameraLoop.setPostMinutes(postMinutes);}
        if(voice!=null && phrasesChanged)voice.setPhrases(startPhrase,stopPhrase);
    }
    private void buildUi() {
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(palette.background);
        preview=new TextureView(this);root.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16),dp(10),dp(12),dp(10));top.setBackgroundColor(palette.surface);
        android.widget.ImageView logo=new android.widget.ImageView(this);logo.setImageResource(R.drawable.ic_lancevivo);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        top.addView(logo,new LinearLayout.LayoutParams(dp(36),dp(36)));
        LinearLayout title=new LinearLayout(this);title.setOrientation(1);title.setPadding(dp(10),0,dp(4),0);
        title.addView(text("LanceVivo",22,WHITE,true));subtitle=text("Viva a ação.",12,palette.secondary,false);title.addView(subtitle);
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        galleryButton=button("▣",palette.tint,WHITE);galleryButton.setContentDescription("Abrir galeria de vídeos");
        galleryButton.setOnClickListener(v->navigate(GalleryActivity.class));top.addView(galleryButton,new LinearLayout.LayoutParams(dp(48),dp(48)));
        settingsButton=button("⚙",palette.tint,WHITE);settingsButton.setId(R.id.settings_button);settingsButton.setContentDescription("Abrir configurações");
        settingsButton.setOnClickListener(v->openSettings());LinearLayout.LayoutParams gearLp=new LinearLayout.LayoutParams(dp(48),dp(48));gearLp.leftMargin=dp(8);top.addView(settingsButton,gearLp);
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(1);bottom.setPadding(dp(18),dp(14),dp(18),dp(16));bottom.setBackground(bg(palette.surface,20));
        stateLabel=text("Preparando câmera…",14,palette.secondary,true);stateLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);bottom.addView(stateLabel);
        savedNotice=text("",14,palette.dark?0xFF95E1B5:0xFF14633A,true);savedNotice.setVisibility(View.GONE);bottom.addView(savedNotice);
        historyHint=text("",12,palette.secondary,false);historyHint.setPadding(0,dp(6),0,dp(6));bottom.addView(historyHint);
        voiceLabel=text("Voz desligada",14,palette.secondary,false);voiceLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);bottom.addView(voiceLabel);
        recordButton=button("Marcar lance",ACCENT,0xFF172B4D);recordButton.setId(R.id.mark_moment);recordButton.setMinimumHeight(dp(56));recordButton.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams markLp=new LinearLayout.LayoutParams(-1,-2);markLp.topMargin=dp(12);bottom.addView(recordButton,markLp);
        recordButton.setOnClickListener(v->{
            if(!foreground||navigating||!AlphaNotice.accepted(this))return;
            CaptureState.Phase phase=cameraLoop.state().phase();
            if(phase==CaptureState.Phase.ERROR||phase==CaptureState.Phase.STOPPED||phase==CaptureState.Phase.SAVED){cameraLoop.resumeCapture();if(useVoice)voice.enable();}
            else cameraLoop.markMoment();refreshButtons();
        });
        stopButton=button("Encerrar e salvar",palette.primary,palette.dark?0xFF172B4D:0xFFFFFFFF);stopButton.setId(R.id.finish_save);stopButton.setMinimumHeight(dp(56));stopButton.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams stopLp=new LinearLayout.LayoutParams(-1,-2);stopLp.topMargin=dp(8);bottom.addView(stopButton,stopLp);
        stopButton.setOnClickListener(v->{cameraLoop.finishAndSave();refreshButtons();});
        LinearLayout utilities=new LinearLayout(this);utilities.setOrientation(0);
        Button hide=button("Ocultar prévia",palette.tint,WHITE);hide.setMinimumHeight(dp(48));hide.setOnClickListener(v->toggleShade());
        pauseButton=button("Pausar histórico",palette.tint,WHITE);pauseButton.setMinimumHeight(dp(48));
        pauseButton.setOnClickListener(v->{if(cameraLoop==null||cameraLoop.isCapturing())return;voice.disable();voiceStatus="Voz pausada junto com a câmera";updateVoiceSubtitle();cameraLoop.pauseHistory();refreshButtons();});
        LinearLayout.LayoutParams utilityLp=new LinearLayout.LayoutParams(0,-2,1);utilityLp.topMargin=dp(10);utilities.addView(hide,utilityLp);
        LinearLayout.LayoutParams pauseLp=new LinearLayout.LayoutParams(0,-2,1);pauseLp.topMargin=dp(10);pauseLp.leftMargin=dp(8);utilities.addView(pauseButton,pauseLp);bottom.addView(utilities);
        android.widget.ScrollView controls=new android.widget.ScrollView(this);controls.setFillViewport(false);controls.addView(bottom);
        FrameLayout.LayoutParams bottomLp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);bottomLp.setMargins(dp(12),0,dp(12),dp(12));root.addView(controls,bottomLp);
        // Keep controls reachable on smaller screens and large fonts without covering the header.
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int available=root.getHeight()-root.getPaddingTop()-root.getPaddingBottom()-top.getHeight()-dp(86);
            int wanted=bottom.getMeasuredHeight();int h=Math.max(dp(100),Math.min(wanted,available));
            if(bottomLp.height!=h){bottomLp.height=h;controls.setLayoutParams(bottomLp);}
        });
        TextView black=text("Prévia oculta\n\nCâmera e voz continuam ativas.\nToque para voltar",18,0xFFFFFFFF,true);black.setGravity(Gravity.CENTER);black.setBackgroundColor(Color.BLACK);black.setOnClickListener(v->toggleShade());black.setVisibility(View.GONE);shade=black;root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout indicator=new LinearLayout(this);indicator.setGravity(Gravity.CENTER);indicator.setPadding(dp(14),dp(8),dp(14),dp(8));indicator.setBackground(bg(0xFF172B4D,14));
        recDot=text("●  ",22,0xFFFF8D7A,true);recLabel=text("PREPARANDO",16,0xFFFFFFFF,true);recLabel.setId(R.id.rec_indicator);recLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);indicator.addView(recDot);indicator.addView(recLabel);
        FrameLayout.LayoutParams recLp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);recLp.topMargin=dp(92);root.addView(indicator,recLp);
        top.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{recLp.topMargin=b-root.getPaddingTop()+dp(10);indicator.setLayoutParams(recLp);});
        setContentView(root);refreshButtons();
    }
    private void toggleShade() {
        dimmed = !dimmed;
        shade.setVisibility(dimmed ? View.VISIBLE : View.GONE);
        getWindow().getDecorView().setSystemUiVisibility(dimmed ?
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY : palette.dark ? 0 :
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    private void refreshButtons() {
        if (cameraLoop == null || recordButton == null) return;
        CaptureState state = cameraLoop.state();
        if (useVoice && voiceLabel != null) {
            voiceLabel.setText(state.isSaving() ? "Voz aguardando o salvamento" :
                    state.phase() == CaptureState.Phase.STOPPED ? "Voz pausada junto com a câmera" : voiceStatus);
        }
        CaptureCues.Cue cue = CaptureCues.between(lastSoundPhase, state.phase());
        lastSoundPhase = state.phase();
        if (tones != null && sounds) {
            try {
                if (cue == CaptureCues.Cue.START) tones.startTone(ToneGenerator.TONE_PROP_BEEP, 140);
                else if (cue == CaptureCues.Cue.STOP) tones.startTone(ToneGenerator.TONE_PROP_BEEP2, 250);
            } catch (RuntimeException ignored) { }
        }
        boolean active = state.isRecording(), exporting = state.isSaving();
        boolean retry = state.phase() == CaptureState.Phase.ERROR || state.phase() == CaptureState.Phase.STOPPED || state.phase() == CaptureState.Phase.SAVED;
        recordButton.setEnabled(state.canMark() || retry); stopButton.setEnabled(state.canFinish());
        recordButton.setText(exporting ? "SALVANDO…" : active ? "MOMENTO EM GRAVAÇÃO" : retry ? "RETOMAR CÂMERA" : "Marcar lance");
        stopButton.setText(exporting ? "AGUARDE O VÍDEO SER SALVO" : "ENCERRAR E SALVAR");
        recordButton.setAlpha(recordButton.isEnabled() ? 1f : 0.45f);
        stopButton.setAlpha(state.canFinish() ? 1f : 0.45f);
        stopButton.setVisibility(active || exporting ? View.VISIBLE : View.GONE);
        settingsButton.setEnabled(!active && !exporting);
        galleryButton.setEnabled(!active && !exporting);
        pauseButton.setEnabled(!active && !exporting && cameraLoop.isRunning());
        pauseButton.setAlpha(pauseButton.isEnabled() ? 1f : 0.45f);
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
        recLabel.setTextColor(active ? 0xFFFFB4A5 : 0xFFFFFFFF);
        historyHint.setText("Até " + past + " s antes • " + durationLabel() + " após marcar");
        refreshAppearanceIfNeeded();
    }
    private void requestPermissionsIfNeeded() {
        if(!AlphaNotice.accepted(this)){showNotice();return;}
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
        if(!foreground||navigating||!AlphaNotice.accepted(this))return;
        cameraLoop.resumeActivity();cameraLoop.start();
        if(useVoice && !cameraLoop.isManuallyPaused())voice.enable();
        updateVoiceSubtitle();
    }
    private String durationLabel(){return postMinutes==0?"sem limite fixo":postMinutes+" min";}
    private void updateVoiceSubtitle(){
        subtitle.setText(useVoice?"Diga “"+startPhrase+"”":"Viva a ação.");
        if(voiceLabel!=null)voiceLabel.setText(useVoice?voiceStatus:"Voz desligada • use o botão");
    }
    private void openSettings(){navigate(SettingsActivity.class);}
    private void navigate(Class<?> destination){
        if(cameraLoop==null||cameraLoop.isCapturing()||navigating)return;
        navigating=true;if(voice!=null)voice.disable();startActivity(new Intent(this,destination));
    }
    private void showNotice(){
        if(noticeDialog!=null&&noticeDialog.isShowing())return;
        LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(20),dp(10),dp(20),0);
        android.widget.ScrollView scroller=new android.widget.ScrollView(this);TextView notice=text(AlphaNotice.TEXT,15,palette.text,false);scroller.addView(notice);
        body.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        android.widget.CheckBox accept=new android.widget.CheckBox(this);accept.setText("Li e aceito os termos desta versão de teste gratuita");accept.setTextColor(palette.text);accept.setMinHeight(dp(56));body.addView(accept);
        noticeDialog=new AlertDialog.Builder(this).setTitle("Bem-vindo ao LanceVivo").setView(body).setCancelable(false)
                .setNegativeButton("Sair",(d,w)->finish()).setPositiveButton("Aceitar e continuar",null).create();
        noticeDialog.setOnShowListener(v->{
            Button yes=noticeDialog.getButton(AlertDialog.BUTTON_POSITIVE);yes.setEnabled(false);
            accept.setOnCheckedChangeListener((b,checked)->yes.setEnabled(checked));
            yes.setOnClickListener(w->{AlphaNotice.accept(this);noticeDialog.dismiss();requestPermissionsIfNeeded();});
            android.view.Window window=noticeDialog.getWindow();if(window!=null)window.setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*0.88));
        });noticeDialog.show();
    }
    @Override protected boolean canRefreshAppearance(){return cameraLoop==null||!cameraLoop.isCapturing();}
    @Override protected void onResume(){
        super.onResume();if(appearanceRecreation)return;foreground=true;navigating=false;
        loadPreferences();updateVoiceSubtitle();uiHandler.removeCallbacks(uiTick);uiHandler.post(uiTick);
        if(AlphaNotice.accepted(this)&&cameraLoop!=null&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)startCapture();
    }
    @Override protected void onSaveInstanceState(Bundle out){
        if(cameraLoop!=null)out.putBoolean("manual_pause",cameraLoop.isManuallyPaused());super.onSaveInstanceState(out);
    }
    @Override protected void onPause(){
        foreground=false;uiHandler.removeCallbacks(uiTick);if(voice!=null)voice.disable();
        if(cameraLoop!=null)cameraLoop.shutdown();if(dimmed)toggleShade();super.onPause();
    }
    @Override public void onBackPressed() {
        new AlertDialog.Builder(this).setTitle("Sair da captura?")
            .setMessage("Ao sair, um lance marcado será encerrado com tentativa de salvamento. O histórico sem marcação será descartado.")
            .setNegativeButton("Continuar", null)
            .setPositiveButton("Sair", (d,w) -> MainActivity.super.onBackPressed()).show();
    }
    @Override protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        if (noticeDialog != null && noticeDialog.isShowing()) noticeDialog.dismiss();
        if (voice != null) voice.close();
        if (tones != null) { tones.release(); tones = null; }
        if (cameraLoop != null) cameraLoop.release();
        super.onDestroy();
    }
}
