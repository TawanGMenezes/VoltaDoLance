package br.com.voltadolance;

import android.app.Activity;
import android.media.*;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One uninterrupted camera encoder and one microphone shared with offline speech recognition. */
public final class ContinuousRecorder {
    public interface Listener {
        /** Main thread. Both formats and the first independently decodable video frame exist. */
        void onReady();
        /** Serial background dispatcher; immutable PCM16 little-endian, mono, 16 kHz. */
        void onPcm(byte[] pcm,int length);
        /** Main thread. The owner must stop the camera session and discard this recorder. */
        void onError(String message);
    }
    private static final int SAMPLE_RATE=16000, PCM_BYTES=3200;
    private static boolean cacheCleaned;
    private static synchronized void cleanOrphans(File root) throws IOException {
        if(cacheCleaned) return;
        File[] sessions=root.listFiles();
        if(sessions!=null) for(File session:sessions) {
            if(!session.isDirectory()) continue;
            File[] files=session.listFiles();
            if(files!=null) for(File f:files) if(f.isFile() && !f.delete() && f.exists())
                throw new IOException("Cannot clean abandoned video history");
            if(!session.delete() && session.exists()) throw new IOException("Cannot clean abandoned capture");
        }
        cacheCleaned=true;
    }
    private static final long STOP_TIMEOUT_MS=7000;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor pcmDispatch=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), runnable -> {
                Thread thread=new Thread(runnable,"shared-voice-pcm"); thread.setDaemon(true); return thread;
            },new ThreadPoolExecutor.DiscardOldestPolicy());
    private final Object state=new Object();
    private final AtomicBoolean readySent=new AtomicBoolean();
    private final AtomicBoolean errorSent=new AtomicBoolean();
    private MediaCodec video, audio;
    private AudioRecord microphone;
    private Surface surface;
    private EncodedPacketStore packets;
    private volatile MediaFormat videoFormat,audioFormat;
    private volatile boolean stopping, aborting, closed;
    private volatile Exception failure;
    private Thread videoDrain,audioDrain,audioInput;
    private boolean started,terminated,exported;
    private volatile boolean videoEos,audioEos;
    private final int orientation;

    public ContinuousRecorder(Activity activity,CameraModes.Mode mode,int orientation,int pastSeconds,
            Listener listener) throws Exception {
        this.listener=Objects.requireNonNull(listener); this.orientation=orientation;
        try {
            File root=new File(activity.getCacheDir(),"continuous-packets");
            if(!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create capture cache");
            cleanOrphans(root);
            File directory=new File(root,UUID.randomUUID().toString());
            // Keep room for the final MP4 and MediaStore copy, with an additional fixed reserve.
            long usable=root.getUsableSpace();
            long cap=(usable-128L*1024*1024)/3;
            if(cap<8L*1024*1024) throw new IOException("Espaço insuficiente para iniciar o histórico");
            packets=new EncodedPacketStore(directory,pastSeconds,cap);
            MediaFormat vf=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,mode.width,mode.height);
            vf.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            vf.setInteger(MediaFormat.KEY_BIT_RATE,mode.bitrate());
            vf.setInteger(MediaFormat.KEY_FRAME_RATE,mode.fps);
            vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            vf.setInteger(MediaFormat.KEY_PROFILE,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            vf.setInteger(MediaFormat.KEY_MAX_B_FRAMES,0);
            video=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            video.configure(vf,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface=video.createInputSurface(); video.start();
            MediaFormat af=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,SAMPLE_RATE,1);
            af.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            af.setInteger(MediaFormat.KEY_BIT_RATE,64000);
            af.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,PCM_BYTES);
            audio=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            audio.configure(af,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE); audio.start();
            int min=AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0) throw new IOException("Formato do microfone não disponível");
            microphone=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(Math.max(min*2,PCM_BYTES*8)).build();
            if(microphone.getState()!=AudioRecord.STATE_INITIALIZED) throw new IOException("Microfone não iniciou");
        } catch(Exception e) {
            aborting=true; stopping=true; closed=true;
            releaseResources(); pcmDispatch.shutdownNow();
            if(packets!=null) try {packets.close();} catch(IOException cleanup) {e.addSuppressed(cleanup);}
            throw e;
        }
    }
    public Surface inputSurface() {return surface;}
    public void start() {
        synchronized(state) {
            if(started || closed || stopping) return;
            started=true;
            videoDrain=new Thread(()->drain(video,true),"continuous-avc-output");
            audioDrain=new Thread(()->drain(audio,false),"continuous-aac-output");
            audioInput=new Thread(this::captureAudio,"continuous-pcm-input");
            videoDrain.start(); audioDrain.start(); audioInput.start();
        }
    }
    public boolean mark(int pastSeconds) {
        synchronized(state) {
            if(!started || stopping || closed || !readySent.get()) return false;
            return packets.mark(pastSeconds);
        }
    }
    public void setPastSeconds(int seconds) {
        synchronized(state) {
            if(closed || stopping) return;
            try {packets.setPastSeconds(seconds);} catch(IOException e) {fail(e);}
        }
    }
    public long bytesOnDisk() {return packets==null?0:packets.bytesOnDisk();}

    private void checkReady() {
        if(!stopping && videoFormat!=null && audioFormat!=null && packets.hasKeyframe()
                && readySent.compareAndSet(false,true)) {
            main.post(()->{if(!closed && !stopping) listener.onReady();});
        }
    }
    private void fail(Exception e) {
        if(aborting || closed) return;
        if(failure==null) failure=e;
        stopping=true;
        if(errorSent.compareAndSet(false,true)) {
            main.post(()->{if(!closed) listener.onError("Falha na captura contínua: "+e.getMessage());});
        }
    }
    private void drain(MediaCodec codec,boolean isVideo) {
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        try {
            while(!aborting) {
                int index=codec.dequeueOutputBuffer(info,10000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if(isVideo) videoFormat=codec.getOutputFormat(); else audioFormat=codec.getOutputFormat();
                    checkReady();
                } else if(index>=0) {
                    try {
                        if(info.size>0 && (info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0) {
                            ByteBuffer data=codec.getOutputBuffer(index);
                            if(data==null) throw new IOException("Encoder output unavailable");
                            data.position(info.offset); data.limit(info.offset+info.size);
                            packets.append(isVideo,info.presentationTimeUs,info.flags,data.slice());
                            checkReady();
                        }
                    } finally {codec.releaseOutputBuffer(index,false);}
                    if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0) {
                        if(isVideo) videoEos=true; else audioEos=true;
                        return;
                    }
                }
            }
        } catch(Exception e) {fail(e);}
    }
    private void dispatchPcm(byte[] bytes) {
        if(pcmDispatch.isShutdown()) return;
        try {pcmDispatch.execute(()->{
            if(!closed && !stopping) {
                try {listener.onPcm(bytes,bytes.length);} catch(RuntimeException ignored) { }
            }
        });} catch(RejectedExecutionException ignored) { }
    }
    private void captureAudio() {
        long framesCaptured=0, framesQueued=0, originNs=Long.MIN_VALUE;
        ArrayDeque<byte[]> pending=new ArrayDeque<>();
        AudioTimestamp stamp=new AudioTimestamp();
        try {
            microphone.startRecording();
            if(microphone.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)
                throw new IOException("Microfone não está gravando");
            while(!stopping && !aborting) {
                byte[] chunk=new byte[PCM_BYTES];
                int count=microphone.read(chunk,0,chunk.length,AudioRecord.READ_BLOCKING);
                if(count<0) {
                    if(stopping || aborting) break;
                    throw new IOException("Falha de leitura do microfone: "+count);
                }
                if(count==0) continue;
                if((count&1)!=0) throw new IOException("Amostra PCM incompleta");
                if(count!=chunk.length) chunk=Arrays.copyOf(chunk,count);
                framesCaptured+=count/2;
                dispatchPcm(chunk); pending.add(chunk);
                if(originNs==Long.MIN_VALUE) {
                    // Camera2 direct-to-encoder compensates REALTIME to the audio MONOTONIC clock.
                    // Anchor from hardware frame position, then count frames without read-time jitter.
                    if(microphone.getTimestamp(stamp,AudioTimestamp.TIMEBASE_MONOTONIC)==AudioRecord.SUCCESS) {
                        originNs=stamp.nanoTime-stamp.framePosition*1_000_000_000L/SAMPLE_RATE;
                    } else if(framesCaptured>=SAMPLE_RATE/2) {
                        originNs=System.nanoTime()-framesCaptured*1_000_000_000L/SAMPLE_RATE;
                    } else continue;
                }
                while(!pending.isEmpty()) framesQueued=encodePcm(pending.removeFirst(),framesQueued,originNs);
            }
            // A very short recording may end before the hardware timestamp becomes available.
            if(originNs==Long.MIN_VALUE) originNs=System.nanoTime()-framesCaptured*1_000_000_000L/SAMPLE_RATE;
            while(!pending.isEmpty() && !aborting) framesQueued=encodePcm(pending.removeFirst(),framesQueued,originNs);
        } catch(Exception e) {if(!aborting) fail(e);}
        finally {
            try {microphone.stop();} catch(Exception ignored) { }
            if(!aborting) {
                try {queueAudioEos(originNs==Long.MIN_VALUE?System.nanoTime()/1000:
                        originNs/1000+framesQueued*1_000_000L/SAMPLE_RATE);}
                catch(Exception e) {fail(e);}
            }
        }
    }
    private long encodePcm(byte[] pcm,long frame,long originNs) throws Exception {
        int offset=0;
        long deadline=System.nanoTime()+2_000_000_000L;
        while(offset<pcm.length && !aborting) {
            int index=audio.dequeueInputBuffer(10000);
            if(index<0) {
                if(System.nanoTime()>deadline) throw new IOException("AAC input timed out");
                continue;
            }
            ByteBuffer buffer=audio.getInputBuffer(index);
            if(buffer==null) throw new IOException("AAC input unavailable");
            buffer.clear();
            int count=Math.min(pcm.length-offset,buffer.remaining())&~1;
            if(count==0) throw new IOException("AAC input too small");
            buffer.put(pcm,offset,count);
            audio.queueInputBuffer(index,0,count,originNs/1000+frame*1_000_000L/SAMPLE_RATE,0);
            offset+=count; frame+=count/2;
            deadline=System.nanoTime()+2_000_000_000L;
        }
        return frame;
    }
    private void queueAudioEos(long pts) throws Exception {
        long deadline=System.nanoTime()+2_000_000_000L;
        while(!aborting) {
            int index=audio.dequeueInputBuffer(10000);
            if(index>=0) {audio.queueInputBuffer(index,0,0,pts,MediaCodec.BUFFER_FLAG_END_OF_STREAM); return;}
            if(System.nanoTime()>deadline) throw new IOException("AAC EOS timed out");
        }
    }

    /** Blocking worker operation. CameraLoop stops its repeating request/session before calling this. */
    public void finish(File output) throws Exception {
        synchronized(state) {
            if(exported) return;
            if(closed) throw new IOException("Captura já encerrada");
            Exception problem=null;
            try {
                if(!packets.isMarked()) throw new IOException("Nenhum lance marcado");
                try {terminate(false);} catch(Exception stopError) {
                    // Drain failure must not erase already encoded, independently decodable packets.
                    terminate(true);
                }

                export(output); exported=true;
            } catch(Exception e) {problem=e;}
            finally {
                if(!terminated) try {terminate(true);} catch(Exception e) {
                    if(problem==null) problem=e; else problem.addSuppressed(e);
                }
                closed=true; pcmDispatch.shutdownNow(); releaseResources();
                try {packets.close();} catch(IOException e) {if(problem==null) problem=e; else problem.addSuppressed(e);}
                if(problem!=null && output.exists() && !output.delete()) problem.addSuppressed(new IOException("Cannot delete incomplete export"));
            }
            if(problem!=null) throw problem;
        }
    }
    public void discard() {
        synchronized(state) {
            if(closed) return;
            try {terminate(true);} catch(Exception ignored) { }
            closed=true; pcmDispatch.shutdownNow(); releaseResources();
            try {packets.close();} catch(IOException ignored) { }
        }
    }
    private void terminate(boolean discard) throws Exception {
        if(terminated) return;
        stopping=true; aborting=discard;
        try {if(microphone!=null) microphone.stop();} catch(Exception ignored) { }
        if(!discard && started) video.signalEndOfInputStream();
        long deadline=System.nanoTime()+STOP_TIMEOUT_MS*1_000_000L;
        join(audioInput,deadline); join(videoDrain,deadline); join(audioDrain,deadline);
        if(alive(audioInput) || alive(videoDrain) || alive(audioDrain)) {
            aborting=true;
            for(Thread t:new Thread[]{audioInput,videoDrain,audioDrain}) if(t!=null) t.interrupt();
            long abortDeadline=System.nanoTime()+1_000_000_000L;
            join(audioInput,abortDeadline); join(videoDrain,abortDeadline); join(audioDrain,abortDeadline);
            if(alive(audioInput) || alive(videoDrain) || alive(audioDrain))
                throw new IOException("Capture thread teardown timed out");
            if(!discard) throw new IOException("Encoder EOS timed out");
        }
        terminated=true;
    }
    private static boolean alive(Thread thread) {return thread!=null && thread.isAlive();}
    private static void join(Thread thread,long deadline) throws InterruptedException {
        if(thread==null || thread==Thread.currentThread()) return;
        long remaining=deadline-System.nanoTime();
        if(remaining>0) thread.join(Math.max(1,remaining/1_000_000L));
    }
    private void export(File output) throws Exception {
        if(videoFormat==null || audioFormat==null) throw new IOException("Formatos de mídia não disponíveis");
        List<EncodedPacketStore.Packet> snapshot=packets.snapshot();
        if(snapshot.isEmpty() || !snapshot.get(0).video || (snapshot.get(0).flags&1)==0)
            throw new IOException("Histórico sem quadro-chave inicial");
        MediaMuxer muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        boolean running=false;
        try {
            muxer.setOrientationHint(orientation);
            int v=muxer.addTrack(videoFormat),a=muxer.addTrack(audioFormat);
            muxer.start(); running=true;
            long commonBaseUs=snapshot.get(0).ptsUs;
            long lastVideo=-1,lastAudio=-1;
            int audioCount=0,videoCount=0;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            for(EncodedPacketStore.Packet p:snapshot) {
                long last=p.video?lastVideo:lastAudio;
                if(p.ptsUs<=last) throw new IOException("Encoder retornou timestamps fora de ordem");
                info.set(0,p.size,p.ptsUs-commonBaseUs,p.flags&~MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                muxer.writeSampleData(p.video?v:a,p.read(),info);
                if(p.video) {lastVideo=p.ptsUs; videoCount++;} else {lastAudio=p.ptsUs; audioCount++;}
            }
            if(videoCount==0 || audioCount==0) throw new IOException("Gravação sem áudio ou vídeo");
            muxer.stop(); running=false;
        } finally {
            if(running) try {muxer.stop();} catch(Exception ignored) { }
            muxer.release();
        }
    }
    private void releaseResources() {
        if(microphone!=null) {try {microphone.release();} catch(Exception ignored) { } microphone=null;}
        for(MediaCodec codec:new MediaCodec[]{video,audio}) if(codec!=null) {
            try {codec.stop();} catch(Exception ignored) { }
            try {codec.release();} catch(Exception ignored) { }
        }
        video=null; audio=null;
        if(surface!=null) {surface.release(); surface=null;}
    }
}
