package br.com.voltadolance;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Encoded bytes live in private GOP files; only packet indexes live in memory. */
final class EncodedPacketStore implements Closeable {
    static final class Packet {
        final boolean video;
        final long ptsUs;
        final int flags, size;
        final File file;
        final long offset;
        Packet(boolean video,long ptsUs,int flags,int size,File file,long offset) {
            this.video=video; this.ptsUs=ptsUs; this.flags=flags;
            this.size=size; this.file=file; this.offset=offset;
        }
        ByteBuffer read() throws IOException {
            byte[] bytes=new byte[size];
            try(RandomAccessFile input=new RandomAccessFile(file,"r")) {
                input.seek(offset); input.readFully(bytes);
            }
            return ByteBuffer.wrap(bytes);
        }
    }
    private static final class Gop {
        final File file;
        final long start;
        final List<Packet> packets=new ArrayList<>();
        long size;
        Gop(File file,long start) {this.file=file; this.start=start;}
    }
    private final File directory;
    private final long maxBytes;
    private final List<Gop> gops=new ArrayList<>();
    private RandomAccessFile writer;
    private long bytes, newestVideo=Long.MIN_VALUE, pinnedStart=Long.MIN_VALUE;
    private int pastSeconds, sequence;
    private boolean closed;

    EncodedPacketStore(File directory,int pastSeconds,long maxBytes) throws IOException {
        if(maxBytes<=0) throw new IllegalArgumentException("Invalid byte limit");
        this.directory=directory; this.maxBytes=maxBytes;
        this.pastSeconds=clampHistory(pastSeconds);
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create packet directory");
    }
    private static int clampHistory(int seconds) {return Math.max(0,Math.min(60,seconds));}
    File directory() {return directory;}
    synchronized long bytesOnDisk() {return bytes;}
    synchronized boolean hasKeyframe() {return !gops.isEmpty();}
    synchronized void setPastSeconds(int seconds) throws IOException {
        pastSeconds=clampHistory(seconds); evict();
    }
    synchronized void append(boolean video,long ptsUs,int flags,ByteBuffer source) throws IOException {
        if(closed) throw new IOException("Packet store closed");
        if(ptsUs<0 || !source.hasRemaining()) return;
        boolean key=video && (flags&1)!=0;
        if(gops.isEmpty() && !key) return; // Never retain undecodable startup frames.
        if(pinnedStart!=Long.MIN_VALUE && ptsUs<pinnedStart) return;
        if(video) newestVideo=Math.max(newestVideo,ptsUs);
        // Evict before allocating the next GOP, including at the disk cap boundary.
        evict();
        int size=source.remaining();
        if(size>maxBytes-bytes) throw new IOException("Limite de armazenamento da gravação atingido");
        if(key) {
            if(writer!=null) writer.close();
            File file=new File(directory,String.format(Locale.ROOT,"gop-%08d.bin",sequence++));
            writer=new RandomAccessFile(file,"rw");
            Gop next=new Gop(file,ptsUs);
            Gop previous=gops.isEmpty()?null:gops.get(gops.size()-1);
            gops.add(next);
            // AAC and AVC drain independently. Audio may already have arrived for this GOP.
            // Move its index and bytes before the previous file becomes eligible for eviction.
            if(previous!=null && pinnedStart==Long.MIN_VALUE) {
                Iterator<Packet> iterator=previous.packets.iterator();
                while(iterator.hasNext()) {
                    Packet packet=iterator.next();
                    if(!packet.video && packet.ptsUs>=ptsUs) {
                        if(packet.size>maxBytes-bytes-size) throw new IOException("Limite de armazenamento da gravação atingido");
                        ByteBuffer data=packet.read();
                        byte[] copy=new byte[packet.size]; data.get(copy);
                        long offset=writer.length(); writer.seek(offset); writer.write(copy);
                        next.packets.add(new Packet(false,packet.ptsUs,packet.flags,packet.size,file,offset));
                        next.size+=packet.size; bytes+=packet.size;
                        iterator.remove();
                    }
                }
            }
        }
        Gop current=gops.get(gops.size()-1);
        long offset=writer.length();
        byte[] data=new byte[size]; source.duplicate().get(data);
        try {writer.seek(offset); writer.write(data);}
        catch(IOException e) {writer.setLength(offset); throw e;}
        current.packets.add(new Packet(video,ptsUs,flags,size,current.file,offset));
        current.size+=size; bytes+=size;
        evict();
    }
    synchronized boolean mark(int seconds) {
        if(closed || pinnedStart!=Long.MIN_VALUE || gops.isEmpty()) return false;
        long cutoff=newestVideo-clampHistory(seconds)*1_000_000L;
        Gop first=gops.get(0);
        for(Gop g:gops) {if(g.start<=cutoff) first=g; else break;}
        pinnedStart=first.start;
        // Deletion failure is surfaced on export/next append; never pretend to have pinned twice.
        try {evict();} catch(IOException e) {throw new IllegalStateException(e);}
        return true;
    }
    private void evict() throws IOException {
        if(gops.size()<2) return;
        long cutoff=pinnedStart!=Long.MIN_VALUE?pinnedStart:newestVideo-pastSeconds*1_000_000L;
        while(gops.size()>1 && gops.get(1).start<=cutoff) {
            Gop old=gops.get(0);
            if(!old.file.delete() && old.file.exists()) throw new IOException("Cannot evict old video history");
            gops.remove(0); bytes-=old.size;
        }
    }
    synchronized List<Packet> snapshot() {
        List<Packet> result=new ArrayList<>();
        if(gops.isEmpty()) return result;
        long start=pinnedStart!=Long.MIN_VALUE?pinnedStart:gops.get(0).start;
        for(Gop g:gops) for(Packet p:g.packets) if(p.ptsUs>=start) result.add(p);
        // Cross-track arrival order is arbitrary; preserve original sample times, never stitch GOPs.
        result.sort(Comparator.comparingLong((Packet p)->p.ptsUs).thenComparing(p->!p.video));
        return result;
    }
    synchronized boolean isMarked() {return pinnedStart!=Long.MIN_VALUE;}
    @Override public synchronized void close() throws IOException {
        if(closed) return;
        closed=true;
        IOException error=null;
        try {if(writer!=null) writer.close();} catch(IOException e) {error=e;}
        File[] files=directory.listFiles();
        if(files!=null) for(File f:files) if(!f.delete() && f.exists()) error=new IOException("Cannot remove packet file");
        if(!directory.delete() && directory.exists()) error=new IOException("Cannot remove packet directory");
        gops.clear(); bytes=0;
        if(error!=null) throw error;
    }
}
