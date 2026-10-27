package com.grapefruitmusic.tags;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/** Replaces part of a file: in place when the size stays the same, else through a new copy. */
final class FileEdit {
    private FileEdit() {
    }

    /** Reads len bytes at offset. */
    static byte[] read(File f, long offset, int len) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        try {
            byte[] b = new byte[len];
            raf.seek(offset);
            raf.readFully(b);
            return b;
        } finally {
            raf.close();
        }
    }

    /** Puts data in place of the bytes from start to end. */
    static void replace(File f, long start, long end, byte[] data) throws IOException {
        if (data.length == end - start) {
            RandomAccessFile raf = new RandomAccessFile(f, "rw");
            try {
                raf.seek(start);
                raf.write(data);
            } finally {
                raf.close();
            }
            return;
        }
        // A new copy next to it, swapped in at the end, so a failure leaves the song as it was.
        File tmp = new File(f.getParentFile(), f.getName() + ".gfnew");
        File old = new File(f.getParentFile(), f.getName() + ".gfold");
        InputStream in = new FileInputStream(f);
        try {
            OutputStream out = new FileOutputStream(tmp);
            try {
                copy(in, out, start);
                out.write(data);
                skip(in, end - start);
                copy(in, out, Long.MAX_VALUE);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            tmp.delete();
            throw e;
        } finally {
            in.close();
        }
        old.delete();
        if (!f.renameTo(old)) {
            tmp.delete();
            throw new IOException("Couldn't save the changes to " + f.getName());
        }
        if (!tmp.renameTo(f)) {
            old.renameTo(f);
            tmp.delete();
            throw new IOException("Couldn't save the changes to " + f.getName());
        }
        old.delete();
    }

    private static void copy(InputStream in, OutputStream out, long count) throws IOException {
        byte[] buf = new byte[65536];
        while (count > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, count));
            if (n < 0) {
                if (count == Long.MAX_VALUE) return;
                throw new IOException("The file ended early");
            }
            out.write(buf, 0, n);
            if (count != Long.MAX_VALUE) count -= n;
        }
    }

    private static void skip(InputStream in, long count) throws IOException {
        while (count > 0) {
            long n = in.skip(count);
            if (n <= 0) {
                if (in.read() < 0) throw new IOException("The file ended early");
                n = 1;
            }
            count -= n;
        }
    }
}
