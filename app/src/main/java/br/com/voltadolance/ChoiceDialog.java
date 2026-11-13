package br.com.voltadolance;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;
import java.util.function.IntConsumer;

/** Shared LanceVivo choice cards; selection commits only after an explicit row tap. */
final class ChoiceDialog {
    private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    private static GradientDrawable card(Activity a,int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(a,radius));if(stroke!=0)d.setStroke(dp(a,1),stroke);return d;}
    static Dialog show(Activity a,String title,String[] options,int selected,IntConsumer commit){
        AppAppearance.Palette p=AppAppearance.palette(a);Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout body=new LinearLayout(a);body.setOrientation(1);body.setPadding(dp(a,20),dp(a,24),dp(a,20),dp(a,16));body.setBackground(card(a,p.surface,0,24));
        TextView heading=new TextView(a);heading.setText(title);heading.setTextSize(22);heading.setTextColor(p.text);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);heading.setPadding(dp(a,4),0,dp(a,4),dp(a,6));body.addView(heading);
        TextView hint=new TextView(a);hint.setText("Escolha uma opção");hint.setTextSize(14);hint.setTextColor(p.secondary);hint.setPadding(dp(a,4),0,0,dp(a,18));body.addView(hint);
        ScrollView scroll=new ScrollView(a){@Override protected void onMeasure(int w,int h){int max=(int)(getResources().getDisplayMetrics().heightPixels*0.55f);if(View.MeasureSpec.getMode(h)!=View.MeasureSpec.UNSPECIFIED)max=Math.min(max,View.MeasureSpec.getSize(h));super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(max,View.MeasureSpec.AT_MOST));}};
        LinearLayout choices=new LinearLayout(a);choices.setOrientation(1);scroll.addView(choices);body.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        for(int i=0;i<options.length;i++){
            final int index=i;boolean checked=i==selected;LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(a,12),dp(a,12),dp(a,16),dp(a,12));row.setMinimumHeight(dp(a,60));
            row.setBackground(card(a,checked?p.tint:p.surface,checked?p.accent:p.line,14));row.setTag("choice_"+i);row.setSelected(checked);row.setFocusable(true);row.setContentDescription(options[i]+(checked?", selecionado":""));
            RadioButton radio=new RadioButton(a);radio.setChecked(checked);radio.setClickable(false);radio.setFocusable(false);radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);radio.setButtonTintList(ColorStateList.valueOf(checked?(p.dark?p.accent:0xFFA53D2D):p.secondary));
            row.addView(radio,new LinearLayout.LayoutParams(dp(a,40),dp(a,36)));TextView label=new TextView(a);label.setText(options[i]);label.setTextSize(17);label.setTextColor(p.text);if(checked)label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            row.setOnClickListener(v->{dialog.dismiss();commit.accept(index);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(a,10);choices.addView(row,lp);
        }
        Button cancel=new Button(a);cancel.setText("Cancelar");cancel.setAllCaps(false);cancel.setTextSize(16);cancel.setTextColor(p.text);cancel.setBackground(card(a,p.tint,0,12));cancel.setMinimumHeight(dp(a,48));cancel.setTag("choice_cancel");cancel.setOnClickListener(v->dialog.dismiss());body.addView(cancel,new LinearLayout.LayoutParams(-1,-2));
        dialog.setContentView(body);dialog.setCanceledOnTouchOutside(true);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0.6f);}
        dialog.show();if(window!=null)window.setLayout(Math.min(dp(a,560),a.getResources().getDisplayMetrics().widthPixels-dp(a,32)),-2);return dialog;
    }
}
