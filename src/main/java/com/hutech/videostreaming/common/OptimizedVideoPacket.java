package com.hutech.videostreaming.common;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.*;

/**
 * Optimized Video Packet with compression and buffering
 * Immediate improvements for better performance
 */
public class OptimizedVideoPacket implements Serializable {
    private static final long serialVersionUID = 2L;

    // Packet fields
    private byte command;
    private int sequenceNumber;
    private long timestamp;
    private int originalLength;
    private int compressedLength;
    private byte[] data;
    private boolean isCompressed;

    // Compression settings
    private static final boolean ENABLE_COMPRESSION = true;
    private static final int COMPRESSION_THRESHOLD = 1000; // Compress if > 1KB
    private static final int COMPRESSION_LEVEL = Deflater.BEST_SPEED; // Fast compression

    // Object pools for performance
    private static final ObjectPool<ByteArrayOutputStream> BAOS_POOL =
            new ObjectPool<>(ByteArrayOutputStream::new, ByteArrayOutputStream::reset);
    private static final ObjectPool<Deflater> DEFLATER_POOL =
            new ObjectPool<>(Deflater::new, Deflater::reset);
    private static final ObjectPool<Inflater> INFLATER_POOL =
            new ObjectPool<>(Inflater::new, Inflater::reset);

    /**
     * Constructor with automatic compression
     */
    public OptimizedVideoPacket(byte command, int sequenceNumber, byte[] data) {
        this.command = command;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = System.nanoTime(); // More precise timing

        if (data != null && data.length > 0) {
            this.originalLength = data.length;

            // Compress if enabled and data is large enough
            if (ENABLE_COMPRESSION && data.length > COMPRESSION_THRESHOLD) {
                this.data = compress(data);
                this.compressedLength = this.data.length;
                this.isCompressed = true;

                // Only use compression if it actually reduced size
                if (this.compressedLength >= this.originalLength) {
                    this.data = data;
                    this.compressedLength = this.originalLength;
                    this.isCompressed = false;
                }
            } else {
                this.data = data;
                this.compressedLength = data.length;
                this.isCompressed = false;
            }
        } else {
            this.originalLength = 0;
            this.compressedLength = 0;
            this.isCompressed = false;
        }
    }

    /**
     * Compress data using Deflater (faster than GZIP)
     */
    private byte[] compress(byte[] data) {
        Deflater deflater = DEFLATER_POOL.borrow();
        ByteArrayOutputStream baos = BAOS_POOL.borrow();

        try {
            deflater.setLevel(COMPRESSION_LEVEL);
            deflater.setInput(data);
            deflater.finish();

            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                baos.write(buffer, 0, count);
            }

            byte[] compressed = baos.toByteArray();

            // Log compression ratio
            double ratio = (1.0 - (compressed.length / (double) data.length)) * 100;
            if (ratio > 10) { // Only log significant compression
                System.out.println(String.format("🗜️ [COMPRESS] Packet #%d: %d -> %d bytes (%.1f%% saved)",
                        sequenceNumber, data.length, compressed.length, ratio));
            }

            return compressed;

        } finally {
            DEFLATER_POOL.returnObject(deflater);
            BAOS_POOL.returnObject(baos);
        }
    }

    /**
     * Decompress data
     */
    private byte[] decompress(byte[] compressed, int originalSize) {
        Inflater inflater = INFLATER_POOL.borrow();

        try {
            inflater.setInput(compressed);
            byte[] decompressed = new byte[originalSize];
            inflater.inflate(decompressed);
            return decompressed;

        } catch (DataFormatException e) {
            System.err.println("❌ [DECOMPRESS] Failed: " + e.getMessage());
            return compressed; // Return as-is if decompression fails

        } finally {
            INFLATER_POOL.returnObject(inflater);
        }
    }

    /**
     * Get decompressed data
     */
    public byte[] getData() {
        if (isCompressed && data != null) {
            return decompress(data, originalLength);
        }
        return data;
    }

    /**
     * Get raw (possibly compressed) data for transmission
     */
    public byte[] getRawData() {
        return data;
    }

    /**
     * Optimized serialization using ByteBuffer
     */
    public byte[] toByteArray() {
        // Calculate total size
        int headerSize = 1 + 4 + 8 + 4 + 4 + 1; // command + seq + time + origLen + compLen + compressed flag
        int totalSize = headerSize + (data != null ? data.length : 0);

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);

        // Write header
        buffer.put(command);
        buffer.putInt(sequenceNumber);
        buffer.putLong(timestamp);
        buffer.putInt(originalLength);
        buffer.putInt(compressedLength);
        buffer.put((byte) (isCompressed ? 1 : 0));

        // Write data
        if (data != null && data.length > 0) {
            buffer.put(data);
        }

        return buffer.array();
    }

    /**
     * Optimized deserialization
     */
    public static OptimizedVideoPacket fromByteArray(byte[] bytes, int length) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, length);

        // Read header
        byte command = buffer.get();
        int sequenceNumber = buffer.getInt();
        long timestamp = buffer.getLong();
        int originalLength = buffer.getInt();
        int compressedLength = buffer.getInt();
        boolean isCompressed = buffer.get() == 1;

        // Read data
        byte[] data = null;
        if (compressedLength > 0 && buffer.remaining() >= compressedLength) {
            data = new byte[compressedLength];
            buffer.get(data);
        }

        // Create packet
        OptimizedVideoPacket packet = new OptimizedVideoPacket((byte)0, 0, null);
        packet.command = command;
        packet.sequenceNumber = sequenceNumber;
        packet.timestamp = timestamp;
        packet.originalLength = originalLength;
        packet.compressedLength = compressedLength;
        packet.isCompressed = isCompressed;
        packet.data = data;

        return packet;
    }

    /**
     * Simple object pool for reusing objects
     */
    private static class ObjectPool<T> {
        private final ConcurrentLinkedQueue<T> pool;
        private final Supplier<T> factory;
        private final Consumer<T> reset;
        private final int maxSize = 100;

        public ObjectPool(Supplier<T> factory, Consumer<T> reset) {
            this.pool = new ConcurrentLinkedQueue<>();
            this.factory = factory;
            this.reset = reset;
        }

        public T borrow() {
            T object = pool.poll();
            return object != null ? object : factory.get();
        }

        public void returnObject(T object) {
            if (pool.size() < maxSize) {
                reset.accept(object);
                pool.offer(object);
            }
        }
    }

    // Getters
    public byte getCommand() { return command; }
    public int getSequenceNumber() { return sequenceNumber; }
    public long getTimestamp() { return timestamp; }
    public int getOriginalLength() { return originalLength; }
    public int getCompressedLength() { return compressedLength; }
    public boolean isCompressed() { return isCompressed; }

    /**
     * Get compression ratio
     */
    public double getCompressionRatio() {
        if (originalLength == 0) return 0;
        return (1.0 - (compressedLength / (double) originalLength)) * 100;
    }

    @Override
    public String toString() {
        return String.format("OptimizedPacket[cmd=%d, seq=%d, orig=%d, comp=%d (%.1f%%), compressed=%s]",
                command, sequenceNumber, originalLength, compressedLength,
                getCompressionRatio(), isCompressed);
    }
}