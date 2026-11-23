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
    private final CameraTuning[] tuning=new CameraTuning[2];
    private final CaptureRequest.Builder[] requests=new CaptureRequest.Builder[2];
    private float zoom=1f;
    float[] zoomChoices(){return tuning[0]==null?new float[]{1f}:tuning[0].choices();}
    float zoomRatio(){return zoom;}
    String stabilizationLabel(){return tuning[0]==null?"Estabilização • verificando suporte":tuning[0].stabilizationLabel();}
    boolean setZoom(float value){
        if(closed||sessions[0]==null||tuning[0]==null)return false;
        float previous=zoom;
        try{zoom=tuning[0].apply(requests[0],value);submit(0);return true;}
        catch(Exception e){try{zoom=tuning[0].apply(requests[0],previous);submit(0);}catch(Exception ignored){}return false;}
    }
    private void submit(final int index)throws CameraAccessException{
        CameraCaptureSession.CaptureCallback feedback=new CameraCaptureSession.CaptureCallback(){
            @Override public void onCaptureCompleted(CameraCaptureSession session,CaptureRequest request,TotalCaptureResult result){if(!closed&&session==sessions[index])tuning[index].observe(result);}
        };
        try{sessions[index].setRepeatingRequest(requests[index].build(),feedback,handler);}
        catch(IllegalArgumentException e){if(!tuning[index].disableVideoStabilization())throw e;tuning[index].apply(requests[index],index==0?zoom:1f);sessions[index].setRepeatingRequest(requests[index].build(),feedback,handler);}
    }
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

            for(int i=0;i<2;i++){
                CaptureRequest.Builder request=devices[i].createCaptureRequest(CameraDevice.TEMPLATE_RECORD);request.addTarget(outputs[i]);
                request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,(i==0?config.rearMode:config.frontMode).aeRange);
                if(Build.VERSION.SDK_INT>=31){
                    int[] rotations=manager.getCameraCharacteristics(i==0?config.rearId:config.frontId).get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES);
                    if(rotations!=null&&Arrays.stream(rotations).anyMatch(x->x==CameraMetadata.SCALER_ROTATE_AND_CROP_NONE))request.set(CaptureRequest.SCALER_ROTATE_AND_CROP,CameraMetadata.SCALER_ROTATE_AND_CROP_NONE);
                }
                CameraModes.Mode mode=i==0?config.rearMode:config.frontMode;
                tuning[i]=CameraTuning.from(manager.getCameraCharacteristics(i==0?config.rearId:config.frontId),mode.width,mode.height,0);
                float effective=tuning[i].apply(request,i==0?zoom:1f);if(i==0)zoom=effective;
                requests[i]=request;
            }
            listener.onConfigured();if(closed)return;
            submit(0);submit(1);
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
