package br.com.voltadolance;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

abstract class ThemedActivity extends Activity {
    protected AppAppearance.Palette palette;
    protected boolean appearanceRecreation;
    private boolean appearancePending;
    @Override protected void onCreate(Bundle state) {
        palette=AppAppearance.palette(this);
        setTheme(palette.dark?R.style.AppThemeDark:R.style.AppTheme);
        super.onCreate(state);
        getWindow().setStatusBarColor(palette.surface);
        getWindow().setNavigationBarColor(palette.surface);
        getWindow().getDecorView().setSystemUiVisibility(palette.dark?0:
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    protected boolean canRefreshAppearance() { return true; }
    protected final void refreshAppearanceIfNeeded() {
        if(appearanceRecreation || isFinishing() || isDestroyed()) return;
        boolean changed=AppAppearance.palette(this).dark!=palette.dark;
        if((changed||appearancePending)&&canRefreshAppearance()) {
            appearancePending=false;
            if(changed) { appearanceRecreation=true; recreate(); }
        } else if(changed) appearancePending=true;
    }
    @Override protected void onResume() { super.onResume(); refreshAppearanceIfNeeded(); }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config); refreshAppearanceIfNeeded();
    }
    @Override public void setContentView(View view) {
        final int left=view.getPaddingLeft(),top=view.getPaddingTop(),right=view.getPaddingRight(),bottom=view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(left+insets.getSystemWindowInsetLeft(),top+insets.getSystemWindowInsetTop(),
                    right+insets.getSystemWindowInsetRight(),bottom+insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        super.setContentView(view); view.requestApplyInsets();
    }
}
