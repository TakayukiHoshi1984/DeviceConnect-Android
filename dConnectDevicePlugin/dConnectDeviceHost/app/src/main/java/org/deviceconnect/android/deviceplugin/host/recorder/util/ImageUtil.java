package org.deviceconnect.android.deviceplugin.host.recorder.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/**
 * Image の処理を行うユーティリティクラス.
 */
public final class ImageUtil {

    private ImageUtil() {
    }

    /**
     * 指定された JPEG 画像を回転して返却します.
     *
     * @param jpeg 元の JPEG 画像データ
     * @param quality JPEG の品質
     * @param degrees 回転
     * @return 回転された JPEG データ
     */
    public static byte[] rotateJPEG(final byte[] jpeg, int quality, int degrees) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        Bitmap rotated;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        rotated.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        byte[] result = baos.toByteArray();
        bitmap.recycle();
        rotated.recycle();
        return result;
    }

    /**
     * {@link Image} を JPEG のバイナリデータに変換します.
     *
     * @param image 元の画像
     * @return JPEG のバイナリデータ
     */
    public static byte[] convertToJPEG(Image image) {
        byte[] jpeg;
        if (image.getFormat() == ImageFormat.JPEG || image.getFormat() == ImageFormat.DEPTH_JPEG) {
            jpeg = readJPEG(image);
        } else if (image.getFormat() == ImageFormat.YUV_420_888) {
            jpeg = NV21toJPEG(YUV420toNV21(image), image.getWidth(), image.getHeight(), 100);
        } else if (image.getFormat() == ImageFormat.DEPTH16) {
            jpeg = writeDepth16Image(image);
        } else {
            throw new RuntimeException("Unsupported format: " + image.getFormat());
        }
        return jpeg;
    }
    private static byte[] writeDepth16Image(final Image img) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        int w = img.getWidth();
        int h = img.getHeight();
        int rowStride = img.getPlanes()[0].getRowStride() / 2; // in shorts
        int[] rgbData = new int[w * h];
        short[] yRow = new short[w];
        ShortBuffer y16Data = img.getPlanes()[0].getBuffer().asShortBuffer();
        int rgbIndex = 0;
        for (int y = 0; y < h; y++) {
            y16Data.position(y * rowStride);
            y16Data.get(yRow, 0, w);
            for (int x = 0; x < w; x++) {
                short y16 = yRow[x];
                rgbData[rgbIndex++] =
                        Color.rgb(y16 & 0x00FF, (y16 >> 8) & 0x00FF, 0);
            }
        }
        Bitmap rgbImage = Bitmap.createBitmap(rgbData, w, h, Bitmap.Config.ARGB_8888);
        rgbImage.compress(Bitmap.CompressFormat.PNG, 100, bout);
        return bout.toByteArray();
    }

    private static byte[] readJPEG(final Image jpegImage) {
        ByteBuffer buffer = jpegImage.getPlanes()[0].getBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data, 0, data.length);
        return data;
    }

    private static byte[] NV21toJPEG(byte[] nv21, int width, int height, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        yuv.compressToJpeg(new Rect(0, 0, width, height), quality, out);
        return out.toByteArray();
    }

    private static byte[] YUV420toNV21(Image image) {
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    channelOffset = width * height + 1;
                    outputStride = 2;
                    break;
                case 2:
                    channelOffset = width * height;
                    outputStride = 2;
                    break;
            }

            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();

            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return data;
    }
}
