package com.ryanheise.just_waveform;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import java.nio.ShortBuffer;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayDeque;

public class WaveformExtractor {
    private static final int TIMEOUT = 5000;
    private static final int MAX_SAMPLE_SIZE = 256 * 1024;

    private String inPath;
    private String wavePath;
    private Integer samplesPerPixel;
    private Integer pixelsPerSecond;
    private OnProgressListener onProgressListener;
    private MediaExtractor extractor;
    private ProcessThread processThread;
    private MediaCodec decoder;
    private MediaFormat inFormat;
    private String inMime;

    public WaveformExtractor(String inPath, String wavePath, Integer samplesPerPixel, Integer pixelsPerSecond) {
        this.inPath = inPath;
        this.wavePath = wavePath;
        this.samplesPerPixel = samplesPerPixel;
        this.pixelsPerSecond = pixelsPerSecond;
    }

    public void start(OnProgressListener onProgressListener) {
        this.onProgressListener = onProgressListener;
        processThread = new ProcessThread();
        processThread.start();
    }

    private MediaFormat selectAudioTrack(MediaExtractor extractor) throws IOException {
        int trackCount = extractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                extractor.selectTrack(i);
                return format;
            }
        }
        throw new IOException("No audio track found");
    }

    private class ProcessThread extends Thread {
        @Override
        public void run() {
            try {
                extractor = new MediaExtractor();
                extractor.setDataSource(inPath);

                inFormat = selectAudioTrack(extractor);
                int trackCount = extractor.getTrackCount();
                System.out.println("extractor format = " + inFormat);
                inMime = inFormat.getString(MediaFormat.KEY_MIME);
                processAudio();
            } catch (Exception e) {
                e.printStackTrace();
                onProgressListener.onError(e.getMessage());
            } finally {
                extractor.release();
            }
        }
    }

    void processAudio() {
        try {
            int channelCount = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            System.out.println("channel count = " + channelCount);
            int sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            System.out.println("sample rate = " + sampleRate);
            long duration = inFormat.getLong(MediaFormat.KEY_DURATION);
            int durationMs = (int)(duration/1000);
            long expectedSampleCount = duration * sampleRate / 1000000; // If we hear 2 stereo samples at the same time, we count that as 1 sample here.
            System.out.println("expected sample count = " + expectedSampleCount);

            boolean sawInputEOS = false;
            int decoderIdleCount = 0;
            int bufferSize = MAX_SAMPLE_SIZE;
            int frameCount = 0;
            int offset = 100;

            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            BufferInfo bufferInfo = new BufferInfo();

            // For the wave
            int samplesPerPixel;
            if (this.samplesPerPixel != null) {
                samplesPerPixel = this.samplesPerPixel;
            } else {
                samplesPerPixel = sampleRate / pixelsPerSecond;
            }
            System.out.println("samples per pixel: " + samplesPerPixel + " = " + sampleRate + " / " + pixelsPerSecond);
            // Multiply by 2 since 2 bytes are needed for each short, and multiply by 2 again because for each sample we store a pair of (min,max)
            int scaledByteSamplesLength = 2*2*(int)(expectedSampleCount / samplesPerPixel);
            ByteBuffer scaledByteSamples = ByteBuffer.allocate(scaledByteSamplesLength); // alternating min,max,min,max,...
            scaledByteSamples.order(ByteOrder.LITTLE_ENDIAN);
            // Number of min/max pairs
            int scaledSamplesLength = scaledByteSamplesLength / 2;
            System.out.println("scaled samples length = " + scaledSamplesLength);
            ShortBuffer scaledSamples = scaledByteSamples.asShortBuffer();
            int scaledSampleIdx = 0;
            short minSample = Short.MAX_VALUE;
            short maxSample = Short.MIN_VALUE;
            // Current min/max pair index
            int sampleIdx = 0;

            int totalSampleSize = 0;
            int totalDecodedBytes = 0;
            int progress = 0;
            int waitingToDecode = 0;
            int waitingForDecoded = 0;
            long presentationTime = 0L;
            long stopwatchStart = System.currentTimeMillis();
            decoder = MediaCodec.createDecoderByType(inMime);
            decoder.configure(inFormat, null, null, 0);
            decoder.start();
            // Supported media formats:
            // https://developer.android.com/guide/topics/media/media-formats
            MediaFormat outFormat = new MediaFormat();
            String outMime = "audio/mp4a-latm";
            outFormat.setString(MediaFormat.KEY_MIME, outMime);
            // XXX: Do we need to set this or is there a default?
            outFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
            outFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);
            if (inFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                outFormat.setInteger(MediaFormat.KEY_BIT_RATE, inFormat.getInteger(MediaFormat.KEY_BIT_RATE));
            }
            MediaFormat changedOutFormat = null;
            while (!sawInputEOS && decoderIdleCount < 100) {
                decoderIdleCount++;
                // Pump the decoder's input buffers.
                if (!sawInputEOS) {
                    int decoderInputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT);
                    if (decoderInputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(decoderInputBufferIndex);
                        bufferInfo.size = extractor.readSampleData(inputBuffer, 0);
                        if (bufferInfo.size >= 0) {
                            totalSampleSize += bufferInfo.size;
                            presentationTime = extractor.getSampleTime();
                            bufferInfo.presentationTimeUs = presentationTime;
                            bufferInfo.flags = extractor.getSampleFlags();

                            decoder.queueInputBuffer(decoderInputBufferIndex, 0, bufferInfo.size, presentationTime, 0);

                            int presentationTimeMs = (int)(presentationTime/1000);
                            int newProgress = (int)(100 * presentationTime / duration);
                            if (newProgress != progress && newProgress < 100) { // save 100 for after the loop since that signifies to the listener that we're done. Probably better to use a different signal.
                                progress = newProgress;
                                //System.out.println("Progress: " + progress + "% (" + presentationTime/1000000.0 + "sec) - sampleSize = " + bufferInfo.size);
                                onProgressListener.onProgress(progress);
                            }

                            extractor.advance();
                        } else {
                            decoder.queueInputBuffer(decoderInputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                        }
                    } else {
                        waitingToDecode++;
                    }
                }

                // Read the output buffers from the decoder.

                int decoderOutputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT);
                if (decoderOutputBufferIndex >= 0) {
                    if (bufferInfo.size > 0)
                        decoderIdleCount = 0;
                    ByteBuffer buf = decoder.getOutputBuffer(decoderOutputBufferIndex);
                    // analyse wave.
                    ShortBuffer sbuf = buf.asShortBuffer();
                    sbuf.mark();
                    int chunkLength = bufferInfo.size / 2; // a short is twice as long
                    for (int i = 0; i < chunkLength; i += channelCount) {
                        // Take the average of the channels
                        int sample = 0;
                        for (int j = 0; j < channelCount; j++) {
                            sample += sbuf.get();
                        }
                        sample /= channelCount;
                        if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;
                        else if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
                        if (sample < minSample) minSample = (short)sample;
                        if (sample > maxSample) maxSample = (short)sample;
                        sampleIdx++;
                        if (sampleIdx % samplesPerPixel == 0) {
                            if (scaledSampleIdx + 1 < scaledSamplesLength) {
                                //if (scaledSampleIdx < 20)
                                //    System.out.println("pixel[" + scaledSampleIdx + "] " + sampleIdx + ": " + minSample + "\t" + maxSample);
                                scaledSamples.put(scaledSampleIdx++, minSample);
                                scaledSamples.put(scaledSampleIdx++, maxSample);
                                minSample = Short.MAX_VALUE;
                                maxSample = Short.MIN_VALUE;
                            }
                        }
                    }
                    sbuf.reset();

                    decoder.releaseOutputBuffer(decoderOutputBufferIndex, false);

                    totalDecodedBytes += bufferInfo.size;
                } else if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    if (newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) != sampleRate)
                        throw new UnsupportedOperationException("Cannot change sample rate");
                    if (newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != channelCount)
                        throw new UnsupportedOperationException("Cannot change channel count");
                    System.out.println("decoderOutputBufferIndex = " + decoderOutputBufferIndex + " (INFO_OUTPUT_FORMAT_CHANGED)");
                } else {
                    waitingForDecoded++;
                }

                frameCount++; // not really frame count anymore
            }

            System.out.println("End. (" + presentationTime/1000000.0 + "sec) frameCount = " + frameCount + ", totalSampleSize = " + totalSampleSize);
            System.out.println("waitingToDecode:   " + waitingToDecode);
            System.out.println("waitingForDecoded: " + waitingForDecoded);

            // Write the wave file
            System.out.println("Writing the wave file...");
            try (FileOutputStream fout = new FileOutputStream(new File(wavePath))) {
                FileChannel channel = fout.getChannel();
                int waveHeaderLength = 20; // in bytes
                int waveHeaderLengthInShorts = waveHeaderLength / 2; // in shorts 
                ByteBuffer waveHeaderBytes = ByteBuffer.allocate(waveHeaderLength);
                waveHeaderBytes.order(ByteOrder.LITTLE_ENDIAN);
                IntBuffer waveHeader = waveHeaderBytes.asIntBuffer();
                waveHeader.put(0, 1); // version
                waveHeader.put(1, 0); // flags - 16 bit
                waveHeader.put(2, sampleRate);
                waveHeader.put(3, samplesPerPixel);
                waveHeader.put(4, (int)(((long)scaledSampleIdx / 2) & 0xffffffffL));
                System.out.println("waveHeader[0] = 1");
                System.out.println("waveHeader[1] = 0");
                System.out.println("waveHeader[2] = " + sampleRate);
                System.out.println("waveHeader[3] = " + samplesPerPixel);
                System.out.println("waveHeader[4] = " + (int)(((long)scaledSampleIdx / 2) & 0xffffffffL));
                channel.write(waveHeaderBytes);
                channel.write(scaledByteSamples);
                System.out.println("Total scaled samples: " + scaledSampleIdx);
            }
            nProgressListener.onProgress(100);
            onProgressListener.onComplete();
        }
        catch (Exception e) {
            e.printStackTrace();
            onProgressListener.onError(e.getMessage());
        }
        finally {
            if (decoder != null)
                decoder.release();
        }
    }

    public static interface OnProgressListener {
        void onProgress(int progress);
        void onComplete();
        void onError(String message);
    }
}
