package br.com.voltadolance;
/** Pure layout math. Rectangles are GL bottom-origin; UV rotation precedes SurfaceTexture's matrix. */
final class DualFrameLayout {
 private DualFrameLayout(){}
 static float[] inset(int w,int h){return new float[]{w*.68f,h*.75f,w*.28f,h*.22f};}
 static float[] uv(int sw,int sh,float dw,float dh,int rotation,boolean mirror){
  if(rotation%90!=0)throw new IllegalArgumentException("Sensor orientation must be a right angle");
  int r=((rotation%360)+360)%360;
  float aspect=(r%180==0?(float)sw/sh:(float)sh/sw),target=dw/dh;
  float sx=Math.min(1,target/aspect),sy=Math.min(1,aspect/target);
  float[] result=new float[8];
  for(int i=0;i<4;i++){
   float x=(i%2==0?0:1),y=(i<2?0:1);
   if(mirror)x=1-x;
   x=.5f+(x-.5f)*sx;y=.5f+(y-.5f)*sy;
   float u=x,v=y;
   if(r==90){u=1-y;v=x;}else if(r==180){u=1-x;v=1-y;}else if(r==270){u=y;v=1-x;}
   result[i*2]=u;result[i*2+1]=v;
  }return result;
 }
}
