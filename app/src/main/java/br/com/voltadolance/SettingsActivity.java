package br.com.voltadolance;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.StatFs;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.List;
import java.util.Locale;

/** Native settings. Camera and microphone belong exclusively to the paused capture Activity. */
public final class SettingsActivity extends ThemedActivity {
    private SharedPreferences prefs;
    private LinearLayout content;
    private ScrollView scroll;
    private String page="home",draftStart,draftStop;
    private EditText startInput,stopInput;
    private int restoreScroll;
    private int pageGeneration;
    private final java.util.concurrent.ExecutorService availability=java.util.concurrent.Executors.newSingleThreadExecutor();
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(palette.text);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);prefs=getSharedPreferences("local_settings",0);
        if(state!=null){page=state.getString("page","home");draftStart=state.getString("draft_start");draftStop=state.getString("draft_stop");restoreScroll=state.getInt("scroll");}
        show(page);if(state!=null)scroll.post(()->scroll.scrollTo(0,restoreScroll));
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        rememberDraft();out.putString("page",page);out.putString("draft_start",draftStart);out.putString("draft_stop",draftStop);
        out.putInt("scroll",scroll.getScrollY());super.onSaveInstanceState(out);
    }
    private void rememberDraft(){if(startInput!=null){draftStart=startInput.getText().toString();draftStop=stopInput.getText().toString();}}
    private void show(String next) {
        rememberDraft();page=next;pageGeneration++;startInput=null;stopInput=null;
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(palette.surface);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),dp(8),dp(16),dp(8));
        Button back=new Button(this);back.setText("‹");back.setTextSize(32);back.setTextColor(palette.text);back.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        back.setContentDescription("Voltar");back.setOnClickListener(v->goBack());header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(56)));
        String title=next.equals("home")?"Configurações":next.equals("voice")?"Comandos de voz":next.equals("capture")?"Captura":next.equals("sound")?"Sons e avisos":next.equals("energy")?"Energia e espaço":next.equals("plans")?"Planos e benefícios":"Privacidade e ajuda";
        header.addView(text(title,24,true),new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        scroll=new ScrollView(this);scroll.setId(R.id.settings_scroll);scroll.setFillViewport(true);
        content=new LinearLayout(this);content.setOrientation(1);content.setPadding(dp(24),dp(12),dp(24),dp(32));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        if(next.equals("home"))home();else if(next.equals("voice"))voice();else if(next.equals("capture"))capture();
        else if(next.equals("sound"))sound();else if(next.equals("energy"))energy();else if(next.equals("plans"))plans();else help();
    }
    private void note(String value){TextView t=text(value,14,false);t.setTextColor(palette.secondary);t.setPadding(dp(16),dp(14),dp(16),dp(14));t.setBackground(shape(palette.tint,12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(16);content.addView(t,lp);}
    private void paragraph(String value){TextView t=text(value,16,false);t.setLineSpacing(dp(3),1);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(20);content.addView(t,lp);}
    private View row(String title,String summary,int id,Runnable action) {
        LinearLayout row=new LinearLayout(this);row.setOrientation(0);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(18),0,dp(18));row.setMinimumHeight(dp(72));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(1);labels.addView(text(title,18,true));
        TextView sub=text(summary,14,false);sub.setTextColor(palette.secondary);sub.setPadding(0,dp(5),dp(8),0);labels.addView(sub);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text("›",28,false);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow);
        if(id!=0)row.setId(id);row.setFocusable(true);row.setContentDescription(title+". "+summary);
        row.setBackgroundResource(android.R.drawable.list_selector_background);row.setOnClickListener(v->action.run());content.addView(row,new LinearLayout.LayoutParams(-1,-2));
        View divider=new View(this);divider.setBackgroundColor(palette.line);content.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));return row;
    }
    private void action(String label,int id,Runnable run) {
        Button b=new Button(this);b.setText(label);b.setTextSize(16);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(palette.dark?0xFF172B4D:0xFFFFFFFF);b.setBackground(shape(palette.primary,12));b.setPadding(dp(16),dp(14),dp(16),dp(14));b.setMinimumHeight(dp(56));
        if(id!=0)b.setId(id);b.setOnClickListener(v->run.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);content.addView(b,lp);
    }
    private void toggle(String label,String summary,String key,boolean value,int id) {
        Switch sw=new Switch(this);sw.setText(label);sw.setTextSize(17);sw.setTextColor(palette.text);sw.setChecked(value);sw.setMinimumHeight(dp(56));sw.setId(id);sw.setPadding(0,dp(10),0,dp(10));
        content.addView(sw,new LinearLayout.LayoutParams(-1,-2));sw.setOnCheckedChangeListener((b,checked)->prefs.edit().putBoolean(key,checked).apply());paragraph(summary);
    }
    private void choose(String title,String[] options,int selected,java.util.function.IntConsumer handler){ChoiceDialog.show(this,title,options,selected,handler);}
    private void home() {
        CaptureSettings s=new CaptureSettings(prefs);note("Câmera e voz pausadas nesta tela. Ao voltar, o histórico começa novamente.");
        row("Captura",s.past+" s antes • "+(s.postMinutes==0?"sem limite fixo depois":"até "+s.postMinutes+" min depois"),R.id.capture_setting,()->show("capture"));
        row("Comandos de voz","Personalize suas frases",R.id.voice_setting,()->show("voice"));
        row("Sons e avisos",s.sounds?"Um sinal ao iniciar, dois ao encerrar":"Bipes desligados",0,()->show("sound"));
        String current=AppAppearance.choice(this);int index=java.util.Arrays.asList(AppAppearance.VALUES).indexOf(current);
        row("Aparência",AppAppearance.LABELS[index],R.id.appearance_setting,()->choose("Aparência",AppAppearance.LABELS,index,n->{prefs.edit().putString("appearance",AppAppearance.VALUES[n]).apply();refreshAppearanceIfNeeded();if(!appearanceRecreation)show("home");}));
        row("Energia e espaço","Entenda o consumo e o histórico",0,()->show("energy"));
        row("Planos e benefícios","Demonstração • nenhuma cobrança",R.id.plans_setting,()->show("plans"));
        row("Privacidade e ajuda","Termos do teste e funcionamento",0,()->show("help"));
        TextView brand=text("LanceVivo • 0.5.0 alfa\nViva a ação. Guarde o momento.",13,false);brand.setTextColor(palette.secondary);brand.setPadding(0,dp(24),0,0);content.addView(brand);
    }
    private void capture() {
        CaptureSettings s=new CaptureSettings(prefs);
        dualOption(s);
        note("Antes e depois são tempos diferentes. O histórico não acumula horas de vídeo não marcado.");
        int[] before={15,30,60};row("Antes de marcar",s.past+" segundos",R.id.history_setting,()->choose("Segundos anteriores",new String[]{"15 segundos","30 segundos","60 segundos"},s.past==15?0:s.past==30?1:2,n->{prefs.edit().putInt("past",before[n]).apply();show("capture");}));
        paragraph("Inclui até esses segundos anteriores ao comando. O trecho antigo que você não marcou é descartado continuamente.");
        int[] after={3,10,20,30,60,0};int selected=1;for(int i=0;i<after.length;i++)if(after[i]==s.postMinutes)selected=i;final int choice=selected;
        row("Após marcar",s.postMinutes==0?"Sem limite fixo":s.postMinutes+" minutos",R.id.duration_setting,()->choose("Limite após marcar",new String[]{"3 minutos","10 minutos","20 minutos","30 minutos","60 minutos","Sem limite fixo"},choice,n->{prefs.edit().putInt("post_minutes",after[n]).apply();show("capture");}));
        paragraph("Tempo máximo da continuação após o comando. Você pode encerrar antes. Sem limite fixo ainda depende de espaço, bateria e temperatura.");
        row(s.dualVideo?"Qualidade no vídeo duplo":"Qualidade solicitada",s.dualVideo?"Automática • até 720p / 30 fps":s.mode.replace("@"," • ")+" fps",0,this::quality);
        paragraph("Somente modos informados pela câmera e pelo codificador do aparelho. Se um modo for rejeitado na abertura, o app tenta uma opção inferior. Não há promessa de 4K ou 60 fps em todos os celulares.");
    }
    private void quality() {
        if(prefs.getBoolean("dual_video",false)){Toast.makeText(this,"No vídeo duplo a qualidade é ajustada para um formato compatível com as duas câmeras. Desative para escolher a qualidade de uma câmera.",Toast.LENGTH_LONG).show();return;}
        try {
            CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);
            CaptureSettings settings=new CaptureSettings(prefs);
            List<CameraModes.Mode> modes=CameraModes.detect(manager.getCameraCharacteristics(CameraModes.cameraForFacing(manager,settings.facing)));
            if(modes.isEmpty())throw new IllegalStateException();String[] labels=new String[modes.size()];int selected=-1;
            for(int i=0;i<modes.size();i++){labels[i]=modes.get(i).label();if(modes.get(i).key().equals(new CaptureSettings(prefs).mode))selected=i;}
            choose(settings.facing==android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT?"Qualidade da frontal":"Qualidade da traseira",labels,selected,n->{prefs.edit().putString("video_mode",modes.get(n).key()).apply();show("capture");});
        }catch(Exception e){Toast.makeText(this,"Não foi possível consultar a câmera. A preferência atual foi mantida.",Toast.LENGTH_LONG).show();}
    }
    private void dualOption(CaptureSettings settings){
        Switch option=new Switch(this);option.setText("Vídeo duplo");option.setTextSize(18);option.setTextColor(palette.text);option.setMinimumHeight(dp(56));option.setId(R.id.dual_video);option.setTag("dual_video_toggle");option.setEnabled(false);option.setChecked(false);content.addView(option,new LinearLayout.LayoutParams(-1,-2));
        note("Traseira na imagem principal e frontal no canto superior direito, também no vídeo salvo. Só será ativado quando o celular disponibilizar as duas câmeras juntas para este aplicativo.");
        TextView status=text("Verificando compatibilidade do aparelho…",14,false);status.setId(R.id.dual_video_status);status.setTextColor(palette.secondary);status.setPadding(0,0,0,dp(16));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);content.addView(status);
        paragraph("Pode reduzir a qualidade e aumentar o consumo de bateria. O histórico e os comandos de voz continuam funcionando. Ativar ou desativar reinicia somente o histórico não marcado ao voltar à câmera.");
        final int generation=pageGeneration;
        availability.execute(()->{DualCameraSupport.Result support=DualCameraSupport.inspect(getApplicationContext());runOnUiThread(()->{
            if(isDestroyed()||isFinishing()||generation!=pageGeneration)return;
            status.setText(support.message);option.setChecked(support.available&&settings.dualVideo);option.setEnabled(support.available);
            if(!support.available&&settings.dualVideo){prefs.edit().putBoolean("dual_video",false).apply();show("capture");return;}
            option.setOnCheckedChangeListener((button,checked)->{
                SharedPreferences.Editor edit=prefs.edit().putBoolean("dual_video",checked);if(checked)edit.putInt("camera_facing",android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK);edit.apply();show("capture");
            });
        });});
    }
    private void voice() {
        CaptureSettings s=new CaptureSettings(prefs);toggle("Comandos de voz ativados","Reconhecimento no aparelho. Nenhum microfone fica ouvindo enquanto você ajusta esta tela.","voice",s.voice,R.id.voice_enabled);
        TextView a=text("Para começar",17,true);content.addView(a);startInput=new EditText(this);startInput.setId(R.id.start_phrase);startInput.setSingleLine();startInput.setText(draftStart==null?s.start:draftStart);startInput.setTextColor(palette.text);startInput.setContentDescription("Frase para começar");content.addView(startInput,new LinearLayout.LayoutParams(-1,dp(64)));
        TextView b=text("Para encerrar e salvar",17,true);b.setPadding(0,dp(20),0,0);content.addView(b);stopInput=new EditText(this);stopInput.setId(R.id.stop_phrase);stopInput.setSingleLine();stopInput.setText(draftStop==null?s.stop:draftStop);stopInput.setTextColor(palette.text);stopInput.setContentDescription("Frase para encerrar e salvar");content.addView(stopInput,new LinearLayout.LayoutParams(-1,dp(64)));
        note("Diga exatamente a frase configurada, sem palavras extras. Para usar encerrar, escreva encerrar no campo de finalização. Maiúsculas, acentos e pontuação são ignorados.");
        TextView error=text("",14,false);error.setTextColor(palette.dark?0xFFFFB4A5:0xFF9F281D);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);content.addView(error);
        action("Salvar frases",R.id.save_phrases,()->{try{CaptureSettings.savePhrases(prefs,startInput.getText().toString(),stopInput.getText().toString());rememberDraft();error.setText("");Toast.makeText(this,"Frases salvas neste aparelho",Toast.LENGTH_SHORT).show();}catch(IllegalArgumentException e){error.setText(e.getMessage());}});
        paragraph("Teste suas frases na câmera antes de confiar no comando durante um lance. Ruído, vento e distância podem dificultar o reconhecimento. Voltar sem salvar descarta somente as alterações das frases.");
    }
    private void sound(){toggle("Bipes de gravação","Um bip quando o lance começa; dois ao encerrar. O volume depende do volume de mídia do celular. Este ajuste não desliga o áudio do vídeo nem os comandos de voz.","sounds",prefs.getBoolean("sounds",true),R.id.sound_enabled);}
    private void energy(){
        try{long free=new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes();note(String.format(Locale.forLanguageTag("pt-BR"),"Espaço livre observado agora: %.1f GB",free/1_000_000_000.0));}catch(Exception ignored){}
        paragraph("A câmera, o codificador e o microfone permanecem ativos com o histórico ligado. Ocultar a prévia cobre a imagem, mas não desliga a captura. A economia de bateria ainda não foi medida no seu aparelho.");
        paragraph("Pause o histórico nos intervalos. Isso desliga a câmera e o microfone. Sair do app ou bloquear a tela também para a captura; um lance marcado recebe tentativa de salvamento.");
        paragraph("Espaço baixo, bateria em 5% fora do carregador e aquecimento elevado podem encerrar a gravação. O histórico antigo é descartado, mas os lances que você salva ocupam espaço e precisam ser gerenciados na galeria.");
        note("Um modo automático de economia será avaliado depois das medições. Esta versão não altera a economia de energia do Android.");
    }
    private void plans(){
        PlanDemo demo=new PlanDemo(prefs);note("DEMONSTRAÇÃO NO APK\nSem compra, cartão ou cobrança. Os estados abaixo são locais e não bloqueiam a gravação. Não há conta ou assinatura real nesta alfa.");
        TextView status=text("",17,true);status.setId(R.id.demo_status);String state=demo.state(System.currentTimeMillis());
        status.setText("Estado simulado: "+(state.equals("trial")?"teste grátis ativo":state.equals("subscribed")?"assinatura ativa":state.equals("expired")?"acesso expirado":"sem plano"));content.addView(status);
        paragraph("Proposta LanceVivo\n30 dias grátis para testar. Depois, renovação automática no plano escolhido somente em uma futura contratação real autorizada.");
        row("Mensal","R$ 9,99 por mês • cobrança mensal",0,()->confirmPlan("monthly"));
        row("Anual","R$ 94,99 por ano • uma cobrança anual",0,()->confirmPlan("annual"));
        paragraph("O anual equivale a aproximadamente R$ 7,92 por mês; o valor anual é cobrado de uma vez, sem parcelamento implícito. Na demonstração, nenhum valor será cobrado.");
        action("Simular 30 dias grátis",R.id.demo_start_trial,()->confirmPlan("monthly"));
        action("Simular fim do teste",R.id.demo_expire,()->{demo.expire();show("plans");});
        action("Simular assinatura ativa",0,()->{demo.subscribe(prefs.getString("demo_plan","monthly"));show("plans");});
        action("Reiniciar demonstração",0,()->{demo.reset();show("plans");});
        paragraph("Conta do proprietário\nNa versão comercial, sua conta poderá receber isenção da assinatura, validada no servidor. Não haverá funções administrativas no aplicativo. Como ainda não existe login, esta alfa não identifica contas.");
        paragraph("Indique um amigo — proposta\nO benefício será liberado somente depois da primeira cobrança paga do indicado e da validação das regras. Bônus, limites e mecanismo ainda estão em definição. Não há códigos ou recompensas reais neste APK.");
        note("Na futura versão paga: cancelar pela loja evita novas renovações conforme as condições da contratação. Desinstalar o aplicativo não cancela assinatura. Vídeos já salvos continuarão acessíveis. Esses fluxos ainda não estão conectados a uma loja.");
    }
    private void confirmPlan(String plan){new AlertDialog.Builder(this).setTitle("Confirmar simulação")
        .setMessage("Plano "+(plan.equals("annual")?"anual: R$ 94,99 por ano":"mensal: R$ 9,99 por mês")+" após 30 dias grátis na proposta.\n\nEsta ação apenas inicia uma demonstração local. Não há pagamento ou renovação real.")
        .setNegativeButton("Agora não",null).setPositiveButton("Iniciar demonstração",(d,n)->{new PlanDemo(prefs).startTrial(System.currentTimeMillis(),plan);show("plans");}).show();}
    private void help(){
        paragraph("LanceVivo • versão 0.5.0 alfa\nViva a ação. Guarde o momento.");
        row("Termos e privacidade do teste","Leia o aviso aceito na primeira abertura",0,()->new AlertDialog.Builder(this).setTitle("Termos e privacidade").setMessage(AlphaNotice.TEXT).setPositiveButton("Fechar",null).show());
        paragraph("Para guardar um lance\n1. Espere HISTÓRICO ATIVO e confira a escuta.\n2. Diga exatamente a frase de início ou toque em Marcar lance. Confira REC e o bip.\n3. Diga exatamente a frase de finalização ou toque em Encerrar e salvar. Espere a confirmação de vídeo salvo.");
        note("Nome e identidade escolhidos para teste; registro de marca e dados do responsável comercial ainda precisam ser concluídos antes do lançamento pago.");
    }
    private void goBack(){if(page.equals("home"))finish();else{draftStart=null;draftStop=null;startInput=null;stopInput=null;show("home");}}
    @Override public void onBackPressed(){goBack();}
    @Override protected void onDestroy(){availability.shutdownNow();super.onDestroy();}
}
