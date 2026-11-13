package br.com.voltadolance;

import android.content.SharedPreferences;

/** Presentation-only sandbox. Never consulted by capture or treated as an entitlement. */
final class PlanDemo {
    private final SharedPreferences p;
    PlanDemo(SharedPreferences preferences) {p=preferences;}
    String state(long now) {
        String value=p.getString("demo_state","none");
        return "trial".equals(value)&&now>=p.getLong("demo_trial_until",0)?"expired":value;
    }
    void startTrial(long now,String plan) {
        p.edit().putString("demo_state","trial").putString("demo_plan",plan)
                .putLong("demo_trial_until",now+30L*86400000).apply();
    }
    void subscribe(String plan) {p.edit().putString("demo_state","subscribed").putString("demo_plan",plan).apply();}
    void expire() {p.edit().putString("demo_state","expired").apply();}
    void reset() {p.edit().remove("demo_state").remove("demo_plan").remove("demo_trial_until").apply();}
}
