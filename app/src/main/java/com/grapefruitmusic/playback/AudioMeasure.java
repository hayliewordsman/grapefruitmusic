package com.grapefruitmusic.playback;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Measures the loudness of an audio file on the phone by decoding it with MediaCodec (Android
 * 4.1+). Songs longer than a minute are measured from eight 4-second slices spread through the
 * song, which is close to measuring all of it at a fraction of the work.
 */
final class AudioMeasure {
    private static final int SLICES = 8;
    private static final long SLICE_US = 4000000L;
    private static final long WHOLE_SONG_UNDER_US = 60000000L;
    /** Gives up on files that take longer than this to decode. */
    private static final long BUDGET_MS = 30000;
    private static final long TIMEOUT_US = 10000;

    private AudioMeasure() {
    }

    /** Integrated loudness in LUFS, or NaN when the file can't be measured. Blocking. */
    @SuppressWarnings("deprecation") // get{Input,Output}Buffers: the only way on Android 4.x
    static double measure(String path) {
        MediaExtractor extractor = null;
        MediaCodec codec = null;
        FileInputStream in = null;
        try {
            extractor = new MediaExtractor();
            in = new FileInputStream(path);
            extractor.setDataSource(in.getFD());
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    format = f;
                    break;
                }
            }
            if (track < 0) return Double.NaN;
            extractor.selectTrack(track);
            String mime = format.getString(MediaFormat.KEY_MIME);
            Loudness meter = new Loudness();
            meter.setFormat(format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            long duration = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION) : -1;

            long[] starts;
            long length;
            if (duration <= 0 || duration < WHOLE_SONG_UNDER_US) {
                starts = new long[]{0};
                length = Long.MAX_VALUE / 2;
            } else {
                starts = new long[SLICES];
                for (int i = 0; i < SLICES; i++) {
                    // Spread from 5% to 95% of the song.
                    starts[i] = duration / 20 + (duration * 9 / 10 - SLICE_US) * i / (SLICES - 1);
                }
                length = SLICE_US;
            }
            long deadline = SystemClock.uptimeMillis() + BUDGET_MS;

            if ("audio/raw".equals(mime)) {
                // Older Android hands WAV and FLAC over already decoded.
                ByteBuffer buf = ByteBuffer.allocateDirect(256 * 1024).order(ByteOrder.LITTLE_ENDIAN);
                short[] pcm = new short[0];
                for (long start : starts) {
                    extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    meter.discontinuity();
                    while (SystemClock.uptimeMillis() < deadline) {
                        buf.clear();
                        int size = extractor.readSampleData(buf, 0);
                        long t = extractor.getSampleTime();
                        if (size <= 0 || t > start + length) break;
                        pcm = toShorts(buf, 0, size, pcm);
                        if (t >= start) meter.add(pcm, size / 2);
                        extractor.advance();
                    }
                }
                return meter.integrated();
            }

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();
            ByteBuffer[] inputs = codec.getInputBuffers();
            ByteBuffer[] outputs = codec.getOutputBuffers();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            short[] pcm = new short[0];
            for (long start : starts) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                codec.flush();
                meter.discontinuity();
                long end = start + length;
                boolean inputDone = false;
                int idle = 0;
                while (SystemClock.uptimeMillis() < deadline) {
                    if (!inputDone) {
                        int i = codec.dequeueInputBuffer(TIMEOUT_US);
                        if (i >= 0) {
                            ByteBuffer b = inputs[i];
                            b.clear();
                            int size = extractor.readSampleData(b, 0);
                            long t = extractor.getSampleTime();
                            if (size < 0 || t > end) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                codec.queueInputBuffer(i, 0, size, t, 0);
                                extractor.advance();
                            }
                        }
                    }
                    int o = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                    if (o >= 0) {
                        idle = 0;
                        if (info.size > 0 && info.presentationTimeUs >= start) {
                            pcm = toShorts(outputs[o], info.offset, info.size, pcm);
                            meter.add(pcm, info.size / 2);
                        }
                        codec.releaseOutputBuffer(o, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                    } else if (o == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // Some old decoders never pass the end-of-stream flag back.
                        if (inputDone && ++idle > 50) break;
                    } else if (o == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outputs = codec.getOutputBuffers();
                    } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // AAC with SBR, for one, decodes at a different rate than it says.
                        MediaFormat out = codec.getOutputFormat();
                        meter.setFormat(out.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                out.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                    }
                }
            }
            return SystemClock.uptimeMillis() < deadline ? meter.integrated() : Double.NaN;
        } catch (Throwable e) {
            // Unsupported format, a broken file, or no decoder on this phone.
            return Double.NaN;
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
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // Nothing to do.
                }
            }
        }
    }

    /** Copies 16-bit little-endian PCM from a buffer, reusing reuse when it's big enough. */
    private static short[] toShorts(ByteBuffer buffer, int offset, int size, short[] reuse) {
        int count = size / 2;
        short[] out = reuse.length >= count ? reuse : new short[count];
        ByteBuffer b = buffer.duplicate();
        b.position(offset);
        b.limit(offset + size);
        ShortBuffer s = b.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        s.get(out, 0, count);
        return out;
    }
}
