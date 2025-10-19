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

    // FEC fields
    private int fecGroupId = -1;
    private FECPacketManager.FECType fecType = FECPacketManager.FECType.DATA;
    private boolean fecEnabled = false;

    // FEC methods
    public void setFECGroupId(int groupId) { this.fecGroupId = groupId; }
    public int getFECGroupId() { return fecGroupId; }

    public void setFECType(FECPacketManager.FECType type) { this.fecType = type; }
    public FECPacketManager.FECType getFECType() { return fecType; }

    public void setFECEnabled(boolean enabled) { this.fecEnabled = enabled; }
    public boolean isFECEnabled() { return fecEnabled; }

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

    class PacketRingBuffer {
        private final OptimizedVideoPacket[] buffer;
        private final int capacity;
        private volatile int writePos = 0;
        private volatile int readPos = 0;
        private volatile int size = 0;

        public PacketRingBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new OptimizedVideoPacket[capacity];
        }

        /**
         * Add packet to buffer (non-blocking)
         */
        public boolean offer(OptimizedVideoPacket packet) {
            if (size >= capacity) {
                return false; // Buffer full
            }

            buffer[writePos] = packet;
            writePos = (writePos + 1) % capacity;
            size++;
            return true;
        }

        /**
         * Remove packet from buffer (non-blocking)
         */
        public OptimizedVideoPacket poll() {
            if (size == 0) {
                return null; // Buffer empty
            }

            OptimizedVideoPacket packet = buffer[readPos];
            buffer[readPos] = null; // Help GC
            readPos = (readPos + 1) % capacity;
            size--;
            return packet;
        }

        public int size() { return size; }
        public boolean isEmpty() { return size == 0; }
        public boolean isFull() { return size >= capacity; }

        /**
         * Get fill percentage
         */
        public double getFillPercentage() {
            return (size * 100.0) / capacity;
        }
    }

    /**
     * Adaptive buffer manager
     */
    class AdaptiveBufferManager {
        private PacketRingBuffer primaryBuffer;
        private PacketRingBuffer overflowBuffer;
        private volatile int bufferSize;
        private volatile double targetLatency = 100; // ms

        // Statistics
        private long packetsBuffered = 0;
        private long packetsDropped = 0;
        private double avgLatency = 0;

        public AdaptiveBufferManager(int initialSize) {
            this.bufferSize = initialSize;
            this.primaryBuffer = new PacketRingBuffer(initialSize);
            this.overflowBuffer = new PacketRingBuffer(initialSize / 2);
        }

        /**
         * Add packet with adaptive buffering
         */
        public boolean addPacket(OptimizedVideoPacket packet) {
            packetsBuffered++;

            // Try primary buffer first
            if (primaryBuffer.offer(packet)) {
                updateLatencyEstimate(packet);
                return true;
            }

            // Try overflow buffer
            if (overflowBuffer.offer(packet)) {
                System.out.println("⚠️ [BUFFER] Using overflow buffer");
                return true;
            }

            // Both buffers full - drop packet
            packetsDropped++;
            System.err.println("❌ [BUFFER] Dropped packet #" + packet.getSequenceNumber());

            // Adapt buffer size if dropping too many
            if (packetsDropped > packetsBuffered * 0.01) { // >1% drop rate
                expandBuffer();
            }

            return false;
        }

        /**
         * Get next packet
         */
        public OptimizedVideoPacket getPacket() {
            OptimizedVideoPacket packet = primaryBuffer.poll();

            // Move from overflow to primary if needed
            if (packet == null && !overflowBuffer.isEmpty()) {
                packet = overflowBuffer.poll();
            }

            // Shrink buffer if consistently low usage
            if (primaryBuffer.getFillPercentage() < 25 && bufferSize > 100) {
                shrinkBuffer();
            }

            return packet;
        }

        /**
         * Update latency estimate
         */
        private void updateLatencyEstimate(OptimizedVideoPacket packet) {
            long now = System.nanoTime();
            double latency = (now - packet.getTimestamp()) / 1_000_000.0; // Convert to ms
            avgLatency = avgLatency * 0.9 + latency * 0.1; // Exponential moving average
        }

        /**
         * Dynamically expand buffer
         */
        private void expandBuffer() {
            int newSize = Math.min(bufferSize * 2, 10000); // Max 10k packets
            if (newSize > bufferSize) {
                System.out.println("📈 [BUFFER] Expanding buffer: " + bufferSize + " -> " + newSize);

                // Create new buffers
                PacketRingBuffer newPrimary = new PacketRingBuffer(newSize);
                PacketRingBuffer newOverflow = new PacketRingBuffer(newSize / 2);

                // Transfer existing packets
                while (!primaryBuffer.isEmpty()) {
                    newPrimary.offer(primaryBuffer.poll());
                }
                while (!overflowBuffer.isEmpty()) {
                    newOverflow.offer(overflowBuffer.poll());
                }

                primaryBuffer = newPrimary;
                overflowBuffer = newOverflow;
                bufferSize = newSize;
            }
        }

        /**
         * Dynamically shrink buffer
         */
        private void shrinkBuffer() {
            int newSize = Math.max(bufferSize / 2, 100); // Min 100 packets
            if (newSize < bufferSize) {
                System.out.println("📉 [BUFFER] Shrinking buffer: " + bufferSize + " -> " + newSize);
                bufferSize = newSize;
                // Note: Don't actually resize until next expand to avoid data loss
            }
        }

        /**
         * Get statistics
         */
        public String getStatistics() {
            double dropRate = packetsBuffered > 0 ?
                    (packetsDropped * 100.0 / packetsBuffered) : 0;

            return String.format("Buffer Stats: Size=%d, Fill=%.1f%%, Dropped=%.2f%%, Latency=%.1fms",
                    bufferSize, primaryBuffer.getFillPercentage(), dropRate, avgLatency);
        }
    }
}