package br.com.voltadolance;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** The serial preparation worker retains ownership until the UI accepts its resources. */
final class CaptureHandoff {
    private final AtomicInteger state=new AtomicInteger();
    private final CountDownLatch resolved=new CountDownLatch(1);
    boolean claim(){return state.compareAndSet(0,1);}
    void reject(){if(state.compareAndSet(0,2))resolved.countDown();}
    void complete(){resolved.countDown();}
    boolean await(long millis){
        try{if(!resolved.await(millis,TimeUnit.MILLISECONDS))reject();}
        catch(InterruptedException e){reject();Thread.currentThread().interrupt();}
        return state.get()==1;
    }
}
