package br.com.voltadolance;

import android.content.Context;
import android.content.res.Configuration;

/** Local appearance preference, independent of capture and billing. */
final class AppAppearance {
    static final String[] VALUES={"system","light","dark"};
    static final String[] LABELS={"Seguir o celular","Claro","Escuro"};
    static String choice(Context context) {
        String value=context.getSharedPreferences("local_settings",0).getString("appearance","system");
        return "light".equals(value)||"dark".equals(value)?value:"system";
    }
    static boolean isDark(String choice,Configuration config) {
        return "dark".equals(choice)||(!"light".equals(choice)&&
                (config.uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
    }
    static Palette palette(Context context) {
        return new Palette(isDark(choice(context),context.getResources().getConfiguration()));
    }
    static final class Palette {
        final boolean dark;
        final int background,surface,text,secondary,line,tint,primary,accent=0xFFF37D68;
        Palette(boolean dark) {
            this.dark=dark;
            background=dark?0xFF0E1728:0xFFEAF1F6;
            surface=dark?0xFF17263E:0xFFFFFFFF;
            text=dark?0xFFF3F6FC:0xFF172B4D;
            secondary=dark?0xFFBCC9DC:0xFF4D6078;
            line=dark?0xFF35445A:0xFFCED9E4;
            tint=dark?0xFF243651:0xFFE4EDF5;
            primary=dark?0xFFB9CEFF:0xFF172B4D;
        }
    }
}
