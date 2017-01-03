package com.megvii.livenesslib.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import static android.R.attr.height;
import static android.R.attr.width;
import static android.graphics.ImageFormat.NV21;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;

@SuppressLint("NewApi")
public class IVideoEncoder {
	private final static String TAG = "ceshi";

	private int TIMEOUT_USEC = 12000;
    boolean  isFormat=false;
	private MediaCodec mediaCodec;
	int color;
	int m_width;
	int m_height;
	int m_framerate = 30;
	int m_interval = 30;
	float m_bpp = 0.25f;
	byte[] m_info = null;

	public byte[] configbyte;
	public  int colorFormat;

	@SuppressLint("NewApi")
	public IVideoEncoder(Context context, int width, int height) {
		Log.w("ceshi", "width===" + width + ", height===" + height);
		if (!SupportAvcCodec())
			return;
//		MediaCodecInfo mediaCodecInfo = selectCodec("video/avc");
//		colorFormat = printColorFormat(mediaCodecInfo, "video/avc");
		m_width = width;
		m_height = height;

	 	MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
//		if(colorFormat ==19) {
//			mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
//					COLOR_FormatYUV420Planar);
//		}else if(colorFormat ==21) {
			mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
					COLOR_FormatYUV420SemiPlanar);
	//	}
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate());
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, m_framerate);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, m_interval);
		try {
			mediaCodec = MediaCodec.createEncoderByType("video/avc");
			//mediaCodec = MediaCodec.createEncoderByType("video/3gpp");
		} catch (Exception e) {
			Log.e("TAG", "BUG");
			e.printStackTrace();
		}
		mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mediaCodec.start();
		createfile(context);
	}
	private static int printColorFormat(MediaCodecInfo codecInfo, String mimeType) {
		MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
		for (int i = 0; i < capabilities.colorFormats.length; i++) {
			int colorFormat = capabilities.colorFormats[i];
			if (colorFormat == 19) {
				return colorFormat;
			} else if (colorFormat == 21) {
				return colorFormat;
			}
		}
	   return  0;
	}

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
					mediaStorageDir.getAbsolutePath() + "/" + System.currentTimeMillis() + ".h264"));
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
//						if(colorFormat==21) {
							byte[] yuv420sp = new byte[m_width * m_height * 3 / 2];
							NV21ToNV12(input, yuv420sp, m_width, m_height);
							input = yuv420sp;
//						}

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
	byte[] i420bytes = null;
	private byte[] swapYV12toI420(byte[] yv12bytes, int width, int height) {
		if (i420bytes == null)
			i420bytes = new byte[yv12bytes.length];
		for (int i = 0; i < width*height; i++)
			i420bytes[i] = yv12bytes[i];
		for (int i = width*height; i < width*height + (width/2*height/2); i++)
			i420bytes[i] = yv12bytes[i + (width/2*height/2)];
		for (int i = width*height + (width/2*height/2); i < width*height + 2*(width/2*height/2); i++)
			i420bytes[i] = yv12bytes[i - (width/2*height/2)];
		return i420bytes;
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
					if (types[i].equalsIgnoreCase("video/avc")) {
						return true;
					}
				}
			}
		}
		return false;
	}
	private static MediaCodecInfo selectCodec(String mimeType) {
		int numCodecs = MediaCodecList.getCodecCount();
		for (int i = 0; i < numCodecs; i++) {
			MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

			if (!codecInfo.isEncoder()) {
				continue;
			}

			String[] types = codecInfo.getSupportedTypes();
			for (int j = 0; j < types.length; j++) {
				if (types[j].equalsIgnoreCase(mimeType)) {
					return codecInfo;
				}
			}
		}
		return null;
	}
}
