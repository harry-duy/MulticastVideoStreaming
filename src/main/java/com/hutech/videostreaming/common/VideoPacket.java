package com.hutech.videostreaming.common;

import java.io.*;

public class VideoPacket implements Serializable {
    private static final long serialVersionUID = 1L;

    private byte command;
    private int sequenceNumber;
    private long timestamp;
    private int dataLength;
    private byte[] data;

    // Constructor
    public VideoPacket(byte command, int sequenceNumber, byte[] data) {
        this.command = command;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = System.currentTimeMillis();
        this.data = data;
        this.dataLength = (data != null) ? data.length : 0;
    }

    /**
     * Chuyển packet thành mảng byte để gửi qua mạng
     * Cấu trúc: [command(1)][seqNum(4)][timestamp(8)][dataLen(4)][data(?)]
     */
    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Ghi header (17 bytes)
        dos.writeByte(command);
        dos.writeInt(sequenceNumber);
        dos.writeLong(timestamp);
        dos.writeInt(dataLength);

        // Ghi data
        if (data != null && dataLength > 0) {
            dos.write(data, 0, dataLength);
        }

        dos.flush();
        return baos.toByteArray();
    }

    /**
     * Đọc packet từ mảng byte nhận được
     */
    public static VideoPacket fromByteArray(byte[] buffer, int length) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(buffer, 0, length);
        DataInputStream dis = new DataInputStream(bais);

        // Đọc header
        byte command = dis.readByte();
        int sequenceNumber = dis.readInt();
        long timestamp = dis.readLong();
        int dataLength = dis.readInt();

        // Đọc data
        byte[] data = null;
        if (dataLength > 0 && dataLength <= length - Constants.HEADER_SIZE) {
            data = new byte[dataLength];
            dis.readFully(data);
        }

        VideoPacket packet = new VideoPacket(command, sequenceNumber, data);
        packet.timestamp = timestamp;
        return packet;
    }

    // Getters
    public byte getCommand() { return command; }
    public int getSequenceNumber() { return sequenceNumber; }
    public long getTimestamp() { return timestamp; }
    public byte[] getData() { return data; }
    public int getDataLength() { return dataLength; }

    @Override
    public String toString() {
        return String.format("VideoPacket[cmd=%d, seq=%d, len=%d]",
                command, sequenceNumber, dataLength);
    }
}