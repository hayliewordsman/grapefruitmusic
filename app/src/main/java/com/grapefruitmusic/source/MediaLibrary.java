package com.grapefruitmusic.source;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.os.RemoteException;

import java.io.IOException;

/**
 * The phone's media library (MediaStore), reached so that it can fail without taking Grapefruit
 * with it.
 *
 * A ContentResolver call holds the library "stably": while a call runs, or a cursor it returned is
 * open, Android closes the calling app as well if the library's process (android.process.media)
 * dies, without an error or a crash report. On a phone short of memory, or with a fault in its
 * firmware's library, that process can die while a playlist is written. Here every call goes
 * through an "unstable" client instead (Android 4.1+): if the library dies, the call fails with
 * an error Grapefruit can show, and Grapefruit carries on.
 */
final class MediaLibrary {
    private final ContentResolver resolver;

    MediaLibrary(ContentResolver resolver) {
        this.resolver = resolver;
    }

    private ContentProviderClient client(Uri uri) throws IOException {
        ContentProviderClient client = resolver.acquireUnstableContentProviderClient(uri);
        if (client == null) throw new IOException("Media library unavailable (is the SD card mounted?)");
        return client;
    }

    private static IOException stopped() {
        return new IOException("This phone's media library stopped. Try again in a moment.");
    }

    /** A cursor that lets go of the library when it's closed. Null results become IOException. */
    Cursor query(Uri uri, String[] columns, String selection, String[] args, String order) throws IOException {
        final ContentProviderClient client = client(uri);
        Cursor c;
        try {
            c = client.query(uri, columns, selection, args, order);
        } catch (RemoteException e) {
            client.release();
            throw stopped();
        } catch (RuntimeException e) {
            client.release();
            throw e;
        }
        if (c == null) {
            client.release();
            throw new IOException("Media library unavailable (is the SD card mounted?)");
        }
        return new CursorWrapper(c) {
            private boolean released;

            @Override
            public void close() {
                try {
                    super.close();
                } finally {
                    if (!released) {
                        released = true;
                        client.release();
                    }
                }
            }
        };
    }

    Uri insert(Uri uri, ContentValues values) throws IOException {
        ContentProviderClient client = client(uri);
        try {
            return client.insert(uri, values);
        } catch (RemoteException e) {
            throw stopped();
        } finally {
            client.release();
        }
    }

    int bulkInsert(Uri uri, ContentValues[] values) throws IOException {
        ContentProviderClient client = client(uri);
        try {
            return client.bulkInsert(uri, values);
        } catch (RemoteException e) {
            throw stopped();
        } finally {
            client.release();
        }
    }

    int update(Uri uri, ContentValues values, String selection, String[] args) throws IOException {
        ContentProviderClient client = client(uri);
        try {
            return client.update(uri, values, selection, args);
        } catch (RemoteException e) {
            throw stopped();
        } finally {
            client.release();
        }
    }

    int delete(Uri uri, String selection, String[] args) throws IOException {
        ContentProviderClient client = client(uri);
        try {
            return client.delete(uri, selection, args);
        } catch (RemoteException e) {
            throw stopped();
        } finally {
            client.release();
        }
    }
}
