package br.com.voltadolance;

import android.content.SharedPreferences;

final class CaptureSettings {
    final int past,postMinutes;
    final String mode,start,stop;
    final boolean voice,sounds;
    CaptureSettings(SharedPreferences p) {
        int history=p.getInt("past",15);past=history==30||history==60?history:15;
        int after=p.getInt("post_minutes",10);
        postMinutes=after==0||after==3||after==20||after==30||after==60?after:10;
        mode=p.getString("video_mode",p.getInt("video_height",720)==1080?"1920x1080@30":"1280x720@30");
        String a=p.getString("start_phrase","Marcar momento"),b=p.getString("stop_phrase","Encerrar vídeo");
        try { new VoiceCommands(a,b); } catch(IllegalArgumentException e) { a="Marcar momento";b="Encerrar vídeo"; }
        start=a;stop=b;voice=p.getBoolean("voice",false);sounds=p.getBoolean("sounds",true);
    }
    static void savePhrases(SharedPreferences p,String start,String stop) {
        new VoiceCommands(start,stop);
        p.edit().putString("start_phrase",start.trim()).putString("stop_phrase",stop.trim()).apply();
    }
}
