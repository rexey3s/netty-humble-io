package vn.eagleeye.research.network;

import java.io.Serializable;

/**
 * Author: Tuan Anh Nguyen (HCM) on 11/10/15.
 */
public class VideoPacket implements Serializable{
    int length;
    byte[] data;

    public VideoPacket() {
    }

    public VideoPacket(int length, byte[] data) {

        this.length = length;
        this.data = data;
    }

    public int getLength() {
        return length;
    }

    public byte[] getData() {
        return data;
    }

}
