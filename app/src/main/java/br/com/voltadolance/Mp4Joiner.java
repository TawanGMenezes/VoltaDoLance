package br.com.voltadolance;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Une fragmentos gravados pelo próprio aplicativo. Sem uploads nem bibliotecas externas. */
public final class Mp4Joiner {
    private Mp4Joiner() { }

    public static void join(List<File> files, File destination, int orientation) throws IOException {
        if (files.isEmpty()) throw new IOException("Nenhum fragmento disponível");
        Map<String, Integer> outTracks = new HashMap<>();
        MediaMuxer muxer = new MediaMuxer(destination.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        boolean started = false;
        try {
            muxer.setOrientationHint(orientation);
            MediaExtractor first = new MediaExtractor();
            try {
                first.setDataSource(files.get(0).getAbsolutePath());
                for (int i = 0; i < first.getTrackCount(); i++) {
                    MediaFormat format = first.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    String kind = mime != null && mime.startsWith("video/") ? "video" :
                            mime != null && mime.startsWith("audio/") ? "audio" : null;
                    if (kind != null && !outTracks.containsKey(kind)) {
                        outTracks.put(kind, muxer.addTrack(format));
                    }
                }
            } finally { first.release(); }
            if (!outTracks.containsKey("video")) throw new IOException("Vídeo sem trilha de imagem");
            muxer.start();
            started = true;
            ByteBuffer buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long baseUs = 0;
            Map<String, Long> lastPts = new HashMap<>();
            for (File file : files) {
                MediaExtractor extractor = new MediaExtractor();
                try {
                    extractor.setDataSource(file.getAbsolutePath());
                    long segmentMaxUs = baseUs;
                    for (int track = 0; track < extractor.getTrackCount(); track++) {
                        MediaFormat format = extractor.getTrackFormat(track);
                        String mime = format.getString(MediaFormat.KEY_MIME);
                        String kind = mime != null && mime.startsWith("video/") ? "video" :
                                mime != null && mime.startsWith("audio/") ? "audio" : null;
                        if (kind == null || !outTracks.containsKey(kind)) continue;
                        extractor.selectTrack(track);
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        while (true) {
                            int actualTrack = extractor.getSampleTrackIndex();
                            if (actualTrack < 0) break;
                            if (actualTrack != track) {
                                if (!extractor.advance()) break;
                                continue;
                            }
                            buffer.clear();
                            int size = extractor.readSampleData(buffer, 0);
                            if (size < 0) break;
                            if (size > buffer.capacity()) throw new IOException("Frame maior que limite do buffer");
                            long pts = baseUs + Math.max(0, extractor.getSampleTime());
                            Long previous = lastPts.get(kind);
                            if (previous != null && pts <= previous) pts = previous + 1;
                            info.set(0, size, pts, extractor.getSampleFlags());
                            buffer.position(0);
                            buffer.limit(size);
                            muxer.writeSampleData(outTracks.get(kind), buffer, info);
                            lastPts.put(kind, pts);
                            segmentMaxUs = Math.max(segmentMaxUs, pts);
                            if (!extractor.advance()) break;
                        }
                        extractor.unselectTrack(track);
                    }
                    baseUs = segmentMaxUs + 33_333;
                } finally { extractor.release(); }
            }
        } finally {
            try { if (started) muxer.stop(); } finally { muxer.release(); }
        }
    }
}
