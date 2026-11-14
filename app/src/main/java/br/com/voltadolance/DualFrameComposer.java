package br.com.voltadolance;

import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.*;
import android.view.Surface;
import java.nio.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Two camera OES textures, one upright encoder stream. All EGL operations stay on one thread. */
public final class DualFrameComposer implements AutoCloseable {
 public interface Listener {void onError(String message);}
 private final HandlerThread thread=new HandlerThread("dual-camera-gl");
 private final Handler handler;
 private final AtomicBoolean closed=new AtomicBoolean();
 private final Listener listener;
 private final Surface encoder,preview;
 private final int width,height;
 private final CameraModes.Mode rearMode,frontMode;
 private final FloatBuffer quad=buffer(new float[]{-1,-1,1,-1,-1,1,1,1});
 private final FloatBuffer rearUv,frontUv;
 private final int[] queryScratch=new int[1];
 private final boolean realtime;
 private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
 private EGLContext context=EGL14.EGL_NO_CONTEXT;
 private EGLSurface encoderWindow=EGL14.EGL_NO_SURFACE,previewWindow=EGL14.EGL_NO_SURFACE;
 private SurfaceTexture rearTexture,frontTexture;
 private Surface rearInput,frontInput;
 private final int[] textures=new int[2];
 private int program;
 private int positionLocation,uvLocation,transformLocation,imageLocation,sizeLocation,roundedLocation;
 private boolean rearReady,frontReady;
 private long rearUpdated,frontUpdated,started,lastSource=Long.MIN_VALUE,lastOutput=Long.MIN_VALUE,clockOffset,lastFrontSource=Long.MIN_VALUE;
 private final float[] rearMatrix=new float[16],frontMatrix=new float[16];
 private static final String VERTEX="attribute vec2 position;attribute vec2 uv;uniform mat4 transform;varying vec2 tex;varying vec2 local;void main(){gl_Position=vec4(position,0.,1.);tex=(transform*vec4(uv,0.,1.)).xy;local=(position+1.)*.5;}";
 private static final String FRAGMENT="#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES image;uniform vec2 size;uniform float rounded;varying vec2 tex;varying vec2 local;void main(){vec2 p=abs((local-.5)*size)-(size*.5-vec2(14.));float d=length(max(p,0.))+min(max(p.x,p.y),0.)-14.;if(rounded>.5&&d>0.)discard;gl_FragColor=rounded>.5&&d> -3.?vec4(1.):texture2D(image,tex);}";
 public DualFrameComposer(Surface encoder,Surface preview,int outputWidth,int outputHeight,
   CameraModes.Mode rearMode,CameraModes.Mode frontMode,int rearOrientation,int frontOrientation,
   boolean rearRealtime,Listener listener)throws Exception {
  this.encoder=encoder;this.preview=preview;width=outputWidth;height=outputHeight;
  this.rearMode=rearMode;this.frontMode=frontMode;
  realtime=rearRealtime;this.listener=java.util.Objects.requireNonNull(listener);
  if(encoder==null||width<=0||height<=0||rearMode==null||frontMode==null)throw new IllegalArgumentException("Invalid compositor inputs");
  rearUv=buffer(DualFrameLayout.uv(rearMode.width,rearMode.height,width,height,rearOrientation,false));
  frontUv=buffer(DualFrameLayout.uv(frontMode.width,frontMode.height,width*.28f,height*.22f,frontOrientation,true));
  thread.start();handler=new Handler(thread.getLooper());
  CountDownLatch done=new CountDownLatch(1);Exception[] error=new Exception[1];
  handler.post(()->{try{if(!closed.get())initialize();}catch(Exception e){error[0]=e;closed.set(true);release();thread.quitSafely();}finally{done.countDown();}});
  try{if(!done.await(7,TimeUnit.SECONDS))throw new TimeoutException("Compositor initialization timed out");if(error[0]!=null)throw error[0];}
  catch(Exception e){close();throw e;}
 }
 public Surface rearSurface(){return rearInput;}
 public Surface frontSurface(){return frontInput;}
 private void initialize(){
  display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] versions=new int[2];
  require(EGL14.eglInitialize(display,versions,0,versions,1),"EGL initialize");
  int[] attributes={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,EGL14.EGL_NONE};
  EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
  require(EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0)&&count[0]>0,"EGL configuration");
  context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
  require(context!=EGL14.EGL_NO_CONTEXT,"EGL context");
  encoderWindow=window(configs[0],encoder);if(preview!=null)previewWindow=window(configs[0],preview);
  current(encoderWindow);program=GLES20.glCreateProgram();int vs=0,fs=0;
  try{vs=shader(GLES20.GL_VERTEX_SHADER,VERTEX);fs=shader(GLES20.GL_FRAGMENT_SHADER,FRAGMENT);GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);require(ok[0]!=0,"GL program: "+GLES20.glGetProgramInfoLog(program));}finally{if(vs!=0)GLES20.glDeleteShader(vs);if(fs!=0)GLES20.glDeleteShader(fs);}
  positionLocation=GLES20.glGetAttribLocation(program,"position");uvLocation=GLES20.glGetAttribLocation(program,"uv");
  transformLocation=GLES20.glGetUniformLocation(program,"transform");imageLocation=GLES20.glGetUniformLocation(program,"image");
  sizeLocation=GLES20.glGetUniformLocation(program,"size");roundedLocation=GLES20.glGetUniformLocation(program,"rounded");
  GLES20.glGenTextures(2,textures,0);
  for(int texture:textures){GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);}
  rearTexture=new SurfaceTexture(textures[0]);rearTexture.setDefaultBufferSize(rearMode.width,rearMode.height);rearInput=new Surface(rearTexture);
  frontTexture=new SurfaceTexture(textures[1]);frontTexture.setDefaultBufferSize(frontMode.width,frontMode.height);frontInput=new Surface(frontTexture);
  rearTexture.setOnFrameAvailableListener(t->frame(true),handler);frontTexture.setOnFrameAvailableListener(t->frame(false),handler);
  started=System.nanoTime();clockOffset=System.nanoTime()-SystemClock.elapsedRealtimeNanos();handler.postDelayed(watchdog,500);checkGl();
 }
 private final Runnable watchdog=new Runnable(){public void run(){if(closed.get())return;long now=System.nanoTime();if((!rearReady||!frontReady)?now-started>15_000_000_000L:(now-rearUpdated>3_000_000_000L||now-frontUpdated>3_000_000_000L)){fail(new IllegalStateException("Uma câmera deixou de enviar imagens"));return;}handler.postDelayed(this,500);}};
 private void frame(boolean rear){
  if(closed.get())return;
  try{
   current(encoderWindow);SurfaceTexture texture=rear?rearTexture:frontTexture;texture.updateTexImage();long now=System.nanoTime();long source=texture.getTimestamp();
   if(rear&&source<=lastSource||!rear&&source<=lastFrontSource)return;
   if(rear){rearReady=true;rearUpdated=now;texture.getTransformMatrix(rearMatrix);}else{frontReady=true;frontUpdated=now;lastFrontSource=source;texture.getTransformMatrix(frontMatrix);}
   if(!rear||!rearReady||!frontReady)return;
   lastSource=source;
   if(!realtime&&lastOutput==Long.MIN_VALUE)clockOffset=now-source;
   long pts=source+clockOffset;if(lastOutput!=Long.MIN_VALUE)pts=Math.max(pts,lastOutput+1000);lastOutput=pts;
   render(encoderWindow,width,height);require(EGLExt.eglPresentationTimeANDROID(display,encoderWindow,pts),"Encoder timestamp");require(EGL14.eglSwapBuffers(display,encoderWindow),"Encoder swap");
   if(previewWindow!=EGL14.EGL_NO_SURFACE){require(EGL14.eglQuerySurface(display,previewWindow,EGL14.EGL_WIDTH,queryScratch,0),"Preview width");int previewWidth=queryScratch[0];require(EGL14.eglQuerySurface(display,previewWindow,EGL14.EGL_HEIGHT,queryScratch,0),"Preview height");render(previewWindow,previewWidth,queryScratch[0]);require(EGL14.eglSwapBuffers(display,previewWindow),"Preview swap");}
   checkGl();
  }catch(Exception e){fail(e);}
 }
 private void render(EGLSurface target,int w,int h){
  current(target);GLES20.glViewport(0,0,w,h);GLES20.glClearColor(0,0,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
  float scale=Math.min((float)w/width,(float)h/height);int vw=Math.round(width*scale),vh=Math.round(height*scale),x=(w-vw)/2,y=(h-vh)/2;
  draw(x,y,vw,vh,textures[0],rearMatrix,rearUv,false);
  draw(x+Math.round(vw*.68f),y+Math.round(vh*.75f),Math.round(vw*.28f),Math.round(vh*.22f),textures[1],frontMatrix,frontUv,true);
 }
 private void draw(int x,int y,int w,int h,int texture,float[] matrix,FloatBuffer uv,boolean round){
  GLES20.glViewport(x,y,w,h);GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
  int pos=positionLocation,tex=uvLocation;
  GLES20.glEnableVertexAttribArray(pos);GLES20.glEnableVertexAttribArray(tex);
  quad.position(0);uv.position(0);
  GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,quad);GLES20.glVertexAttribPointer(tex,2,GLES20.GL_FLOAT,false,0,uv);
  GLES20.glUniformMatrix4fv(transformLocation,1,false,matrix,0);GLES20.glUniform1i(imageLocation,0);GLES20.glUniform2f(sizeLocation,w,h);GLES20.glUniform1f(roundedLocation,round?1:0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(pos);GLES20.glDisableVertexAttribArray(tex);
 }
 private static FloatBuffer buffer(float[] v){FloatBuffer b=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(v).position(0);return b;}
 private int shader(int kind,String source){int s=GLES20.glCreateShader(kind);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException(log);}return s;}
 private EGLSurface window(EGLConfig config,Surface surface){EGLSurface result=EGL14.eglCreateWindowSurface(display,config,surface,new int[]{EGL14.EGL_NONE},0);require(result!=EGL14.EGL_NO_SURFACE,"EGL window");return result;}
 private void current(EGLSurface surface){require(EGL14.eglMakeCurrent(display,surface,surface,context),"EGL make current");}
 private static void require(boolean value,String operation){if(!value)throw new IllegalStateException(operation);}
 private static void checkGl(){int error=GLES20.glGetError();if(error!=GLES20.GL_NO_ERROR)throw new IllegalStateException("GL error "+error);}
 private void fail(Exception e){if(!closed.compareAndSet(false,true))return;release();thread.quitSafely();try{listener.onError("Falha no vídeo duplo: "+e.getMessage());}catch(RuntimeException ignored){}}
 @Override public void close(){
  if(closed.compareAndSet(false,true)){if(Looper.myLooper()==thread.getLooper()){release();thread.quitSafely();return;}handler.post(()->{release();thread.quitSafely();});}
  if(Thread.currentThread()!=thread)try{thread.join(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
 }
 private static void safely(Runnable action){try{action.run();}catch(RuntimeException ignored){}}
 private void release(){
  handler.removeCallbacksAndMessages(null);
  if(rearTexture!=null)safely(()->rearTexture.setOnFrameAvailableListener(null));if(frontTexture!=null)safely(()->frontTexture.setOnFrameAvailableListener(null));
  if(rearInput!=null){safely(()->rearInput.release());rearInput=null;}if(frontInput!=null){safely(()->frontInput.release());frontInput=null;}
  if(rearTexture!=null){safely(()->rearTexture.release());rearTexture=null;}if(frontTexture!=null){safely(()->frontTexture.release());frontTexture=null;}
  if(display!=EGL14.EGL_NO_DISPLAY){
   if(context!=EGL14.EGL_NO_CONTEXT&&encoderWindow!=EGL14.EGL_NO_SURFACE&&EGL14.eglMakeCurrent(display,encoderWindow,encoderWindow,context)){if(program!=0)GLES20.glDeleteProgram(program);GLES20.glDeleteTextures(2,textures,0);}
   EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
   if(previewWindow!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,previewWindow);if(encoderWindow!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,encoderWindow);if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);EGL14.eglReleaseThread();EGL14.eglTerminate(display);
  }
  display=EGL14.EGL_NO_DISPLAY;context=EGL14.EGL_NO_CONTEXT;encoderWindow=previewWindow=EGL14.EGL_NO_SURFACE;program=0;
 }
}
