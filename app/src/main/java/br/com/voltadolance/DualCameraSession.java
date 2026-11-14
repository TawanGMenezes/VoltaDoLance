package br.com.voltadolance;

import android.annotation.SuppressLint;
import android.hardware.camera2.*;
import android.os.Build;
import android.os.Handler;
import android.view.Surface;
import java.util.Arrays;
import java.util.Collections;

/** Main-thread owner of two devices. Both open before either session is configured. */
final class DualCameraSession implements AutoCloseable {
    interface Listener {void onConfigured();void onError(String reason);}
    private final CameraManager manager;
    private final DualCaptureConfig config;
    private final Surface[] outputs;
    private final Handler handler;
    private final Listener listener;
    private final CameraDevice[] devices=new CameraDevice[2];
    private final CameraCaptureSession[] sessions=new CameraCaptureSession[2];
    private boolean closed,configuring,started;
    DualCameraSession(CameraManager manager,DualCaptureConfig config,Surface rear,Surface front,Handler handler,Listener listener){
        this.manager=manager;this.config=config;outputs=new Surface[]{rear,front};this.handler=handler;this.listener=listener;
    }
    @SuppressLint("MissingPermission") void start(){
        try{open(0,config.rearId);if(!closed)open(1,config.frontId);}catch(Exception e){fail("Não foi possível abrir as duas câmeras: "+e.getMessage());}
    }
    @SuppressLint("MissingPermission") private void open(final int index,String id)throws CameraAccessException{
        manager.openCamera(id,new CameraDevice.StateCallback(){
            @Override public void onOpened(CameraDevice device){
                if(closed){device.close();return;}devices[index]=device;
                if(devices[0]!=null&&devices[1]!=null&&!configuring){configuring=true;configure(0);if(!closed)configure(1);}
            }
            @Override public void onDisconnected(CameraDevice device){device.close();if(!closed)fail("Uma câmera do vídeo duplo foi desconectada");}
            @Override public void onError(CameraDevice device,int error){device.close();if(!closed)fail("O aparelho rejeitou o vídeo duplo (câmera "+error+")");}
        },handler);
    }
    private void configure(final int index){
        try{devices[index].createCaptureSession(Collections.singletonList(outputs[index]),new CameraCaptureSession.StateCallback(){
            @Override public void onConfigured(CameraCaptureSession session){
                if(closed){session.close();return;}sessions[index]=session;
                if(sessions[0]!=null&&sessions[1]!=null&&!started){started=true;begin();}
            }
            @Override public void onConfigureFailed(CameraCaptureSession session){session.close();if(!closed)fail("As câmeras não aceitaram a captura simultânea");}
        },handler);}catch(Exception e){fail("Não foi possível configurar o vídeo duplo: "+e.getMessage());}
    }
    private void begin(){
        try{
            CaptureRequest[] requests=new CaptureRequest[2];
            for(int i=0;i<2;i++){
                CaptureRequest.Builder request=devices[i].createCaptureRequest(CameraDevice.TEMPLATE_RECORD);request.addTarget(outputs[i]);
                request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,(i==0?config.rearMode:config.frontMode).aeRange);
                if(Build.VERSION.SDK_INT>=31){
                    int[] rotations=manager.getCameraCharacteristics(i==0?config.rearId:config.frontId).get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES);
                    if(rotations!=null&&Arrays.stream(rotations).anyMatch(x->x==CameraMetadata.SCALER_ROTATE_AND_CROP_NONE))request.set(CaptureRequest.SCALER_ROTATE_AND_CROP,CameraMetadata.SCALER_ROTATE_AND_CROP_NONE);
                }
                requests[i]=request.build();
            }
            listener.onConfigured();if(closed)return;
            sessions[0].setRepeatingRequest(requests[0],null,handler);sessions[1].setRepeatingRequest(requests[1],null,handler);
        }catch(Exception e){fail("Não foi possível iniciar o vídeo duplo: "+e.getMessage());}
    }
    private void fail(String reason){if(closed)return;close();listener.onError(reason);}
    @Override public void close(){
        if(closed)return;closed=true;
        for(int i=0;i<2;i++){
            if(sessions[i]!=null){try{sessions[i].stopRepeating();}catch(Exception ignored){}sessions[i].close();sessions[i]=null;}
            if(devices[i]!=null){devices[i].close();devices[i]=null;}
        }
    }
}
