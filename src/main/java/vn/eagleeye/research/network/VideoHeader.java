package vn.eagleeye.research.network;

import io.humble.video.PixelFormat;

import java.io.Serializable;

/**
 * Author: Tuan Anh Nguyen (HCM) on 11/10/15.
 */
public class VideoHeader implements Serializable{
    String codec;
    int snapsPerSecond;
    int width;
    int height;
    String pixelFormat;

    public VideoHeader() {
    }

    public VideoHeader(String codec, int snapsPerSecond, int width, int height, String pixelFormat) {
        this.codec = codec;
        this.snapsPerSecond = snapsPerSecond;
        this.width = width;
        this.height = height;
        this.pixelFormat = pixelFormat;
    }

    public String getPixelFormat() {
        return pixelFormat;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getCodec() {
        return codec;
    }

    public int getSnapsPerSecond() {
        return snapsPerSecond;
    }
}
