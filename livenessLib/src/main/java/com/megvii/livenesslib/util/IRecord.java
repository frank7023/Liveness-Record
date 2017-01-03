package com.megvii.livenesslib.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by jianghaozhang on 16/12/22.
 */

public class IRecord {

    private static final boolean VERBOSE = false;           // lots of logging
    private static final File OUTPUT_DIR = Environment.getExternalStorageDirectory();
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30;               // 30fps
    private static final int IFRAME_INTERVAL = 5;           // 5 seconds between I-frames
    private static final long DURATION_SEC = 8;
    private final static String TAG = "ceshi";
    private MediaCodec.BufferInfo mBufferInfo;
    private int TIMEOUT_USEC = 12000;
    int bitRate = 6000000;
    private MediaCodec mediaCodec;
    int m_width;
    int m_height;
    int m_framerate = 30;
    int m_interval = 30;
    float m_bpp = 0.25f;
    byte[] m_info = null;

    public byte[] configbyte;
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private int mTrackIndex;
    private boolean mMuxerStarted;

    @SuppressLint("NewApi")
    public IRecord(Context context, int width, int height) {
        if (!SupportAvcCodec())
            return;

        m_width = width;
        m_height = height;
        prepareEncoder(m_width,m_height,bitRate);
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/hevc", width, height);
//		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
//				MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate());
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, m_framerate);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, m_interval);
        try {
            mediaCodec = MediaCodec.createEncoderByType("video/hevc");
        } catch (Exception e) {
            e.printStackTrace();
        }
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
        createfile(context);
    }
//    private void encodeCameraToMpeg() {
//        // arbitrary but popular values
//        int encWidth = 640;
//        int encHeight = 480;
//        int encBitRate = 6000000;      // Mbps
//        Log.d(TAG, MIME_TYPE + " output " + encWidth + "x" + encHeight + " @" + encBitRate);
//
//        try {
//            prepareCamera(encWidth, encHeight);
//            prepareEncoder(encWidth, encHeight, encBitRate);
//            mInputSurface.makeCurrent();
//            prepareSurfaceTexture();
//
//            mCamera.startPreview();
//
//            long startWhen = System.nanoTime();
//            long desiredEnd = startWhen + DURATION_SEC * 1000000000L;
//            SurfaceTexture st = mStManager.getSurfaceTexture();
//            int frameCount = 0;
//
//            while (System.nanoTime() < desiredEnd) {
//                // Feed any pending encoder output into the muxer.
//                drainEncoder(false);
//
//                // Switch up the colors every 15 frames.  Besides demonstrating the use of
//                // fragment shaders for video editing, this provides a visual indication of
//                // the frame rate: if the camera is capturing at 15fps, the colors will change
//                // once per second.
//                if ((frameCount % 15) == 0) {
//                    String fragmentShader = null;
//                    if ((frameCount & 0x01) != 0) {
//                        fragmentShader = SWAPPED_FRAGMENT_SHADER;
//                    }
//                    mStManager.changeFragmentShader(fragmentShader);
//                }
//                frameCount++;
//
//                // Acquire a new frame of input, and render it to the Surface.  If we had a
//                // GLSurfaceView we could switch EGL contexts and call drawImage() a second
//                // time to render it on screen.  The texture can be shared between contexts by
//                // passing the GLSurfaceView's EGLContext as eglCreateContext()'s share_context
//                // argument.
//                mStManager.awaitNewImage();
//                mStManager.drawImage();
//
//                // Set the presentation time stamp from the SurfaceTexture's time stamp.  This
//                // will be used by MediaMuxer to set the PTS in the video.
//                if (VERBOSE) {
//                    Log.d(TAG, "present: " +
//                            ((st.getTimestamp() - startWhen) / 1000000.0) + "ms");
//                }
//                mInputSurface.setPresentationTime(st.getTimestamp());
//
//                // Submit it to the encoder.  The eglSwapBuffers call will block if the input
//                // is full, which would be bad if it stayed full until we dequeued an output
//                // buffer (which we can't do, since we're stuck here).  So long as we fully drain
//                // the encoder before supplying additional input, the system guarantees that we
//                // can supply another frame without blocking.
//                if (VERBOSE) Log.d(TAG, "sending frame to encoder");
//                mInputSurface.swapBuffers();
//            }
//
//            // send end-of-stream to encoder, and drain remaining output
//            drainEncoder(true);
//        } finally {
//            // release everything we grabbed
//            releaseCamera();
//            releaseEncoder();
//            releaseSurfaceTexture();
//        }
//    }

    private int calcBitRate() {
        final int bitrate = (int) (m_bpp * m_framerate * m_width * m_height);
        Log.w(TAG, String.format("bitrate=%5.2f[Mbps]", bitrate / 1024f / 1024f));
        return bitrate;
    }

    private BufferedOutputStream outputStream;

    private void createfile(Context context) {
        File mediaStorageDir = context.getExternalFilesDir(Constant.cacheVideo);

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return;
            }
        }
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(
                    mediaStorageDir.getAbsolutePath() + "/" + System.currentTimeMillis() + ".h265"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isRuning = false;

    public void StopThread() {
        if (mediaCodec == null || outputStream == null)
            return;
        isRuning = false;
        try {
            mediaCodec.stop();
            mediaCodec.release();
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    int count = 0;

    public ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<byte[]>(10);

    public void putYUVData(byte[] buffer, int length) {
        if (YUVQueue.size() >= 10) {
            YUVQueue.poll();
        }
        YUVQueue.add(buffer);
    }

    public void StartEncoderThread() {
        Thread EncoderThread = new Thread(new Runnable() {
            @SuppressLint("NewApi")
            @Override
            public void run() {
                isRuning = true;
                byte[] input = null;
                long pts = 0;
                long generateIndex = 0;

                while (isRuning) {
                    if (YUVQueue.size() > 0) {
                        input = YUVQueue.poll();
//						byte[] yuv420sp = new byte[m_width * m_height * 3 / 2];
//						NV21ToNV12(input, yuv420sp, m_width, m_height);
//						input = yuv420sp;
                    }
                    if (input != null) {
                        try {
                            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
                            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
                            if (inputBufferIndex >= 0) {
                                pts = computePresentationTime(generateIndex);
                                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                                inputBuffer.clear();
                                inputBuffer.put(input);
                                mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
                                generateIndex += 1;
                            }

                            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                            while (outputBufferIndex >= 0) {
                                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                                byte[] outData = new byte[bufferInfo.size];

                                outputBuffer.get(outData);
                                if (bufferInfo.flags == 2) {
                                    configbyte = new byte[bufferInfo.size];
                                    configbyte = outData;
                                } else if (bufferInfo.flags == 1) {
                                    byte[] keyframe = new byte[bufferInfo.size + configbyte.length];
                                    System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                                    System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);

                                    outputStream.write(keyframe, 0, keyframe.length);
                                } else {
                                    outputStream.write(outData, 0, outData.length);
                                }

                                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                            }

                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    } else {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        EncoderThread.start();
    }

    private void NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null)
            return;
        int framesize = width * height;
        int i = 0, j = 0;
        // System.arraycopy(nv21, 0, nv12, 0, (int)(framesize * 1.5f));
        System.arraycopy(nv21, 0, nv12, 0, (int) (framesize));
        // for (i = 0; i < framesize; i++) {
        // nv12[i] = nv21[i];
        // }
        // for (j = 0; j < framesize / 2; j += 2) {
        // nv12[framesize + j - 1] = nv21[j + framesize];
        // }
        // for (j = 0; j < framesize / 2; j += 2) {
        // nv12[framesize + j] = nv21[j + framesize - 1];
        // }
        for (j = framesize; j < framesize + framesize / 2; j += 2) {
            nv12[j] = nv21[j + 1];
            nv12[j + 1] = nv21[j];
        }
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / m_framerate;
    }

    private boolean SupportAvcCodec() {
        if (Build.VERSION.SDK_INT >= 18) {
            for (int j = MediaCodecList.getCodecCount() - 1; j >= 0; j--) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);

                String[] types = codecInfo.getSupportedTypes();
                for (int i = 0; i < types.length; i++) {
                    if (types[i].equalsIgnoreCase("video/hevc")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Configures encoder and muxer state, and prepares the input Surface.  Initializes
     * mEncoder, mMuxer, mInputSurface, mBufferInfo, mTrackIndex, and mMuxerStarted.
     */
    @SuppressLint("NewApi")
    private void prepareEncoder(int width, int height, int bitRate) {
        mBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);


        mEncoder.start();
        String outputPath = new File(OUTPUT_DIR,
                "test." + width + "x" + height + ".mp4").toString();
        Log.i(TAG, "Output file is " + outputPath);

        try {
            mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }

        mTrackIndex = -1;
        mMuxerStarted = false;
    }
}
