package com.grapefruitmusic.playback;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Grapefruit's own player, used when Volume Boost, the Equalizer or Mono Audio is on: decodes with MediaCodec,
 * runs the sound through {@link Dsp}, and plays it with AudioTrack (Android 4.1+; the same
 * approach as ExoPlayer on old Android). One audio thread does all the work.
 */
@SuppressWarnings("deprecation") // MediaCodec buffer arrays: the only way on Android 4.x
final class DspEngine implements Engine {
    private static final String TAG = "DspEngine";
    private static final long TIMEOUT_US = 5000;

    /** Karaoke Mode's model, read from the app once. */
    private static VocalRemover.Model karaokeModel;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Context app;
    private final Dsp dsp = new Dsp();
    private final PowerManager.WakeLock wakeLock;
    private final Object lock = new Object();
    private Callback callback;
    private String source;

    // Guarded by lock.
    private boolean playing;
    private boolean released;
    private boolean completed;
    private long seekToUs = -1;
    private AudioTrack track;

    // Written by the audio thread, read for the position.
    private volatile int rate;
    /** Song position of the first frame written since the track was last flushed. */
    private volatile long baseUs;
    private volatile long durationUs = -1;

    // Audio thread only.
    private int outChannels;
    private int bufferFrames;
    private long framesWritten;
    private short[] pending = new short[0];
    private int pendingOff;
    private int pendingLen;
    private short[] scratch = new short[0];

    DspEngine(Context context) {
        app = context.getApplicationContext();
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "grapefruit:effects");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void setDataSource(String source) {
        this.source = source;
    }

    @Override
    public void prepareAsync() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                runAudio();
            }
        }, "gf-audio");
        t.start();
    }

    @Override
    public void start() {
        synchronized (lock) {
            if (released) return;
            // Like MediaPlayer: start after the end plays the song again.
            if (completed && seekToUs < 0) seekToUs = 0;
            completed = false;
            playing = true;
            if (track != null && seekToUs < 0) track.play();
            lock.notifyAll();
        }
        wakeLock.acquire();
    }

    @Override
    public void pause() {
        synchronized (lock) {
            playing = false;
            if (track != null) track.pause();
            lock.notifyAll();
        }
        if (wakeLock.isHeld()) wakeLock.release();
    }

    @Override
    public boolean isPlaying() {
        synchronized (lock) {
            return playing && !completed;
        }
    }

    @Override
    public void seekTo(int ms) {
        synchronized (lock) {
            seekToUs = Math.max(0, ms) * 1000L;
            completed = false;
            lock.notifyAll();
        }
    }

    @Override
    public int getCurrentPosition() {
        AudioTrack t;
        synchronized (lock) {
            if (seekToUs >= 0) return (int) (seekToUs / 1000);
            t = track;
        }
        long us = baseUs;
        int r = rate;
        if (t != null && r > 0) {
            try {
                // What's heard now went in latencyFrames() earlier (Karaoke Mode's 70 ms, mostly).
                long heard = (t.getPlaybackHeadPosition() & 0xffffffffL) - dsp.latencyFrames();
                us += Math.max(0, heard) * 1000000L / r;
            } catch (IllegalStateException e) {
                // The audio thread just let go of the track (stopping, or an error on its way).
            }
        }
        if (durationUs > 0 && us > durationUs) us = durationUs;
        return (int) (us / 1000);
    }

    @Override
    public int getDuration() {
        return durationUs > 0 ? (int) (durationUs / 1000) : -1;
    }

    @Override
    public void setVolume(float volume) {
        dsp.setVolume(volume);
    }

    @Override
    public void setEffects(final Effects e) {
        dsp.setBoost(e.boost);
        dsp.setEq(e.eq);
        dsp.setMono(e.mono);
        VocalRemover.Model model = loadedKaraokeModel();
        if (e.karaoke && model == null) {
            // Read the model off the main thread; Karaoke Mode starts once it's in.
            com.grapefruitmusic.util.Bg.run(new com.grapefruitmusic.util.Bg.Work<VocalRemover.Model>() {
                @Override
                public VocalRemover.Model run() throws Exception {
                    return karaokeModel(app);
                }
            }, new com.grapefruitmusic.util.Bg.Done<VocalRemover.Model>() {
                @Override
                public void done(VocalRemover.Model m, Exception error) {
                    if (m != null) dsp.setKaraoke(e.karaoke, e.karaokeAmount, m);
                    else Log.w(TAG, "karaoke model", error);
                }
            });
        }
        dsp.setKaraoke(e.karaoke, e.karaokeAmount, model);
    }

    private static synchronized VocalRemover.Model loadedKaraokeModel() {
        return karaokeModel;
    }

    /** Karaoke Mode's model (assets/karaoke.bin), read once, warmed up and kept. */
    static synchronized VocalRemover.Model karaokeModel(Context c) throws IOException {
        if (karaokeModel == null) {
            java.io.InputStream in = c.getAssets().open("karaoke.bin");
            VocalRemover.Model model;
            try {
                model = VocalRemover.Model.read(in);
            } finally {
                in.close();
            }
            warmUp(model);
            karaokeModel = model;
        }
        return karaokeModel;
    }

    /**
     * Runs the model over a second of quiet noise. Android 4's JIT only compiles code once it has
     * run a while, and until then Karaoke Mode is several times slower: warmed up here, off the
     * audio thread, it's fast from the first note.
     */
    static void warmUp(VocalRemover.Model model) {
        VocalRemover r = new VocalRemover(model, 44100);
        java.util.Random random = new java.util.Random(1);
        for (int i = 0; i < 44100; i++) {
            r.process(random.nextFloat() * 0.02f - 0.01f, random.nextFloat() * 0.02f - 0.01f);
        }
    }

    @Override
    public boolean hasEffects() {
        return true;
    }

    @Override
    public void release() {
        synchronized (lock) {
            released = true;
            playing = false;
            lock.notifyAll();
        }
        if (wakeLock.isHeld()) wakeLock.release();
        // The audio thread lets go of the decoder and track.
    }

    private void post(final Runnable r) {
        main.post(new Runnable() {
            @Override
            public void run() {
                synchronized (lock) {
                    if (released) return;
                }
                r.run();
            }
        });
    }

    private void runAudio() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        MediaExtractor extractor = null;
        MediaCodec codec = null;
        FileInputStream in = null;
        try {
            extractor = new MediaExtractor();
            if (source.startsWith("/")) {
                in = new FileInputStream(source);
                extractor.setDataSource(in.getFD());
            } else {
                extractor.setDataSource(source);
            }
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    extractor.selectTrack(i);
                    format = f;
                    break;
                }
            }
            if (format == null) throw new IOException("No audio in this file");
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (format.containsKey(MediaFormat.KEY_DURATION)) durationUs = format.getLong(MediaFormat.KEY_DURATION);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            boolean raw = "audio/raw".equals(mime); // WAV and (on older Android) FLAC come decoded
            if (!raw) {
                codec = MediaCodec.createDecoderByType(mime);
                codec.configure(format, null, null, 0);
                codec.start();
            }
            openTrack(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), channels, 0);
            post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) callback.onPrepared(DspEngine.this);
                }
            });
            play(extractor, codec, channels);
        } catch (final Throwable e) {
            Log.w(TAG, "can't play " + source, e);
            post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) callback.onError(DspEngine.this, String.valueOf(e));
                }
            });
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Throwable ignored) {
                    // Not started.
                }
                codec.release();
            }
            if (extractor != null) extractor.release();
            synchronized (lock) {
                if (track != null) {
                    track.release();
                    track = null;
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // Nothing to do.
                }
            }
        }
    }

    /** Opens (or reopens, after the decoder changed format) the AudioTrack. */
    private void openTrack(int sampleRate, int channels, long atUs) throws IOException {
        outChannels = channels == 1 ? 1 : 2;
        int config = outChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int min = AudioTrack.getMinBufferSize(sampleRate, config, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IOException("Can't play " + sampleRate + " Hz audio");
        // At least a quarter second, so a busy moment doesn't make the sound stutter.
        int bytes = Math.max(min * 2, sampleRate * outChannels * 2 / 4);
        AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, config, AudioFormat.ENCODING_PCM_16BIT,
                bytes, AudioTrack.MODE_STREAM);
        if (t.getState() != AudioTrack.STATE_INITIALIZED) {
            t.release();
            throw new IOException("AudioTrack didn't start");
        }
        bufferFrames = bytes / (2 * outChannels);
        dsp.setFormat(sampleRate, outChannels);
        synchronized (lock) {
            if (track != null) track.release();
            track = t;
            rate = sampleRate;
            baseUs = atUs;
            framesWritten = 0;
            pendingLen = 0;
            if (playing && !completed) t.play();
        }
    }

    private long head(AudioTrack t) {
        return t.getPlaybackHeadPosition() & 0xffffffffL;
    }

    private void play(MediaExtractor ex, MediaCodec codec, int channels) throws Exception {
        ByteBuffer[] inputs = codec != null ? codec.getInputBuffers() : null;
        ByteBuffer[] outputs = codec != null ? codec.getOutputBuffers() : null;
        ByteBuffer rawBuf = codec == null ? ByteBuffer.allocateDirect(256 * 1024).order(ByteOrder.LITTLE_ENDIAN) : null;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inChannels = channels;
        boolean inputDone = false;
        boolean outputDone = false;
        boolean tailFlushed = false;
        boolean stopped = false;
        long drainDeadline = 0;
        long skipUntilUs = 0;
        while (true) {
            AudioTrack t;
            long seek;
            synchronized (lock) {
                if (released) return;
                seek = seekToUs;
                if (seek < 0 && (!playing || completed)) {
                    lock.wait();
                    continue;
                }
                t = track;
            }
            if (seek >= 0) {
                // Slow (a stream may need to fetch), so done outside the lock.
                ex.seekTo(seek, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                if (codec != null) codec.flush();
                synchronized (lock) {
                    track.pause();
                    track.flush();
                    baseUs = seek;
                    framesWritten = 0;
                    pendingLen = 0;
                    if (seekToUs == seek) seekToUs = -1;
                    if (playing && seekToUs < 0) track.play();
                }
                skipUntilUs = seek;
                dsp.reset();
                inputDone = false;
                outputDone = false;
                tailFlushed = false;
                stopped = false;
                continue;
            }

            // 1. Hand processed sound to the AudioTrack: only what fits, so the write never blocks.
            if (pendingLen > 0) {
                int free = (int) (bufferFrames - (framesWritten - head(t)));
                if (free <= 0) {
                    Thread.sleep(10);
                    continue;
                }
                int n = Math.min(free * outChannels, pendingLen);
                int w = t.write(pending, pendingOff, n);
                if (w < 0) throw new IOException("AudioTrack write failed (" + w + ")");
                pendingOff += w;
                pendingLen -= w;
                framesWritten += w / outChannels;
                continue;
            }

            // 2. End of the song: send out the limiter's last few ms, then let it all play.
            if (outputDone) {
                if (!tailFlushed) {
                    tailFlushed = true;
                    int n = dsp.latencyFrames() * outChannels;
                    ensurePending(n);
                    java.util.Arrays.fill(pending, 0, n, (short) 0);
                    dsp.process(pending, 0, n);
                    pendingOff = 0;
                    pendingLen = n;
                    continue;
                }
                if (!stopped) {
                    stopped = true;
                    t.stop(); // plays out what's written
                    long left = Math.max(0, framesWritten - head(t));
                    drainDeadline = SystemClock.uptimeMillis() + left * 1000 / rate + 1500;
                }
                // Some phones reset the head to 0 once drained; the deadline covers those.
                if (head(t) >= framesWritten || SystemClock.uptimeMillis() > drainDeadline) {
                    synchronized (lock) {
                        if (seekToUs >= 0) continue;
                        completed = true;
                        playing = false;
                    }
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (wakeLock.isHeld()) wakeLock.release();
                            if (callback != null) callback.onCompletion(DspEngine.this);
                        }
                    });
                    continue;
                }
                Thread.sleep(10);
                continue;
            }

            // 3. Decode some more.
            if (codec == null) {
                rawBuf.clear();
                int size = ex.readSampleData(rawBuf, 0);
                if (size < 0) {
                    outputDone = true;
                    continue;
                }
                long ptsUs = ex.getSampleTime();
                ex.advance();
                toPending(rawBuf, 0, size, ptsUs, inChannels, skipUntilUs);
                continue;
            }
            if (!inputDone) {
                int i = codec.dequeueInputBuffer(TIMEOUT_US);
                if (i >= 0) {
                    ByteBuffer b = inputs[i];
                    b.clear();
                    int size = ex.readSampleData(b, 0);
                    if (size < 0) {
                        codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        codec.queueInputBuffer(i, 0, size, ex.getSampleTime(), 0);
                        ex.advance();
                    }
                }
            }
            int o = codec.dequeueOutputBuffer(info, TIMEOUT_US);
            if (o >= 0) {
                if (info.size > 0) toPending(outputs[o], info.offset, info.size, info.presentationTimeUs, inChannels, skipUntilUs);
                codec.releaseOutputBuffer(o, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
            } else if (o == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputs = codec.getOutputBuffers();
            } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // AAC with SBR, for one, decodes at a different rate than the file says.
                MediaFormat f = codec.getOutputFormat();
                int r = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                inChannels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                int out = inChannels == 1 ? 1 : 2;
                if (r != rate || out != outChannels) openTrack(r, inChannels, getCurrentPosition() * 1000L);
            }
        }
    }

    private void ensurePending(int samples) {
        if (pending.length < samples) pending = new short[samples];
    }

    /** Decoded 16-bit PCM in: skip what's before a seek target, mix down to stereo, process. */
    private void toPending(ByteBuffer buffer, int offset, int size, long ptsUs, int inChannels, long skipUntilUs) {
        int samples = size / 2;
        if (scratch.length < samples) scratch = new short[samples];
        ByteBuffer b = buffer.duplicate();
        b.position(offset);
        b.limit(offset + size);
        b.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(scratch, 0, samples);
        int frames = samples / inChannels;
        int skip = 0;
        if (ptsUs < skipUntilUs) {
            skip = (int) Math.min(frames, (skipUntilUs - ptsUs) * rate / 1000000L);
        }
        int keep = frames - skip;
        ensurePending(keep * outChannels);
        int o = 0;
        for (int f = skip; f < frames; f++) {
            int base = f * inChannels;
            if (inChannels <= 2) {
                for (int c = 0; c < outChannels; c++) pending[o++] = scratch[base + c];
            } else {
                // Surround: even channels left, odd channels right.
                int l = 0;
                int r = 0;
                for (int c = 0; c < inChannels; c++) {
                    if (c % 2 == 0) l += scratch[base + c];
                    else r += scratch[base + c];
                }
                pending[o++] = (short) (l / ((inChannels + 1) / 2));
                pending[o++] = (short) (r / (inChannels / 2));
            }
        }
        dsp.process(pending, 0, o);
        pendingOff = 0;
        pendingLen = o;
    }
}
