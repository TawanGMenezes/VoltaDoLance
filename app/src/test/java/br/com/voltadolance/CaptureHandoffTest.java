package br.com.voltadolance;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;
public class CaptureHandoffTest {
 private Object handoff()throws Exception{Class<?> type=null;try{type=Class.forName("br.com.voltadolance.CaptureHandoff");}catch(ClassNotFoundException ignored){}assertNotNull("Preparation ownership handshake missing",type);java.lang.reflect.Constructor<?> c=type.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
 private Object call(Object owner,String name,Class<?>[] types,Object...args)throws Exception{java.lang.reflect.Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(owner,args);}
 @Test public void rejectedPreparationIsCleanedBeforeNextSerialAllocation()throws Exception{
  Object h=handoff();AtomicBoolean connected=new AtomicBoolean(true);ExecutorService io=Executors.newSingleThreadExecutor();
  try{call(h,"reject",new Class<?>[0]);Future<?> first=io.submit(()->{try{if(!(Boolean)call(h,"await",new Class<?>[]{long.class},1000L))connected.set(false);}catch(Exception e){throw new RuntimeException(e);}});Future<Boolean> second=io.submit(()->!connected.get());first.get(2,TimeUnit.SECONDS);assertTrue(second.get(2,TimeUnit.SECONDS));}finally{io.shutdownNow();}
 }
 @Test public void timedOutWorkerCannotBeInstalledByLateMainCallback()throws Exception{Object h=handoff();assertFalse((Boolean)call(h,"await",new Class<?>[]{long.class},0L));assertFalse((Boolean)call(h,"claim",new Class<?>[0]));}
 @Test public void installedResourcesAreNotDiscardedByWorker()throws Exception{Object h=handoff();assertTrue((Boolean)call(h,"claim",new Class<?>[0]));call(h,"complete",new Class<?>[0]);assertTrue((Boolean)call(h,"await",new Class<?>[]{long.class},1000L));call(h,"reject",new Class<?>[0]);assertTrue((Boolean)call(h,"await",new Class<?>[]{long.class},0L));}
}
