package br.com.voltadolance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Atomic model installation with a separate budget for extraction and free-space reserve. */
final class VoiceModelFiles {
    interface Copier { void copyTo(File staging) throws IOException; }
    private static final long REQUIRED_FREE_BYTES = (96L + 128L) * 1024 * 1024;
    private VoiceModelFiles() { }
    static synchronized File prepare(File root, Copier copier) throws IOException {
        File destination = new File(root, "vosk-small-pt-0.3");
        if (new File(destination, ".complete").isFile()
                && (new File(destination, "final.mdl").isFile()
                || new File(destination, "am/final.mdl").isFile())) return destination;
        File staging = new File(root, "vosk-small-pt-0.3.partial");
        deleteTree(staging);
        if (root.getUsableSpace() < REQUIRED_FREE_BYTES)
            throw new IOException("Espaço insuficiente para preparar a voz local. Libere pelo menos 224 MiB");
        try {
            copier.copyTo(staging);
            try (FileOutputStream marker = new FileOutputStream(new File(staging, ".complete"))) {
                marker.write(1);
            }
            deleteTree(destination);
            if (!staging.renameTo(destination)) throw new IOException("Não foi possível preparar o modelo de voz");
            return destination;
        } catch (IOException | RuntimeException | Error failure) {
            try { deleteTree(staging); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    private static void deleteTree(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (file.exists() && !file.delete()) throw new IOException("Não foi possível limpar " + file);
    }
}
