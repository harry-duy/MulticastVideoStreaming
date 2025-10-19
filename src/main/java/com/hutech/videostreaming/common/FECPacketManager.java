package com.hutech.videostreaming.common;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forward Error Correction (FEC) for reliable multicast
 * Uses Reed-Solomon-like approach with XOR parity
 */
public class FECPacketManager {

    // FEC Configuration
    private static final int FEC_GROUP_SIZE = 10; // 10 data packets
    private static final int FEC_PARITY_COUNT = 2; // 2 parity packets per group
    private static final double FEC_REDUNDANCY = 0.2; // 20% redundancy

    // Packet groups for FEC
    private Map<Integer, FECGroup> fecGroups;
    private int currentGroupId = 0;

    /**
     * FEC Group containing data and parity packets
     */
    private static class FECGroup {
        int groupId;
        List<VideoPacket> dataPackets;
        List<VideoPacket> parityPackets;
        BitSet receivedMask;
        long timestamp;

        FECGroup(int groupId) {
            this.groupId = groupId;
            this.dataPackets = new ArrayList<>(FEC_GROUP_SIZE);
            this.parityPackets = new ArrayList<>(FEC_PARITY_COUNT);
            this.receivedMask = new BitSet(FEC_GROUP_SIZE + FEC_PARITY_COUNT);
            this.timestamp = System.currentTimeMillis();
        }

        boolean isComplete() {
            return receivedMask.cardinality() >= FEC_GROUP_SIZE;
        }

        boolean canRecover() {
            return receivedMask.cardinality() >= FEC_GROUP_SIZE;
        }
    }

    public FECPacketManager() {
        this.fecGroups = new ConcurrentHashMap<>();
    }

    // ==================== SENDER SIDE ====================

    /**
     * Encode data packets with FEC
     * @param packets Original data packets
     * @return Data + Parity packets
     */
    public List<VideoPacket> encodeFEC(List<VideoPacket> packets) {
        List<VideoPacket> result = new ArrayList<>();

        // Process in groups
        for (int i = 0; i < packets.size(); i += FEC_GROUP_SIZE) {
            int groupEnd = Math.min(i + FEC_GROUP_SIZE, packets.size());
            List<VideoPacket> group = packets.subList(i, groupEnd);

            // Add original packets
            result.addAll(group);

            // Generate and add parity packets
            List<VideoPacket> parityPackets = generateParityPackets(group, currentGroupId);
            result.addAll(parityPackets);

            currentGroupId++;
        }

        return result;
    }

    /**
     * Generate parity packets using XOR
     */
    private List<VideoPacket> generateParityPackets(List<VideoPacket> dataPackets, int groupId) {
        List<VideoPacket> parityPackets = new ArrayList<>();

        // Find max packet size
        int maxSize = dataPackets.stream()
                .mapToInt(p -> p.getData() != null ? p.getData().length : 0)
                .max()
                .orElse(0);

        // Simple XOR parity (can be enhanced with Reed-Solomon)
        for (int p = 0; p < FEC_PARITY_COUNT; p++) {
            byte[] parityData = new byte[maxSize];

            // XOR all data packets with different patterns
            for (int i = 0; i < dataPackets.size(); i++) {
                VideoPacket packet = dataPackets.get(i);
                if (packet.getData() != null) {
                    byte[] data = packet.getData();

                    // Apply different XOR pattern for each parity
                    for (int j = 0; j < data.length; j++) {
                        if (p == 0) {
                            // Parity 1: Simple XOR
                            parityData[j] ^= data[j];
                        } else {
                            // Parity 2: XOR with rotation
                            parityData[j] ^= (byte)(data[j] << (i % 8));
                        }
                    }
                }
            }

            // Create parity packet with special marker
            VideoPacket parityPacket = new VideoPacket(
                    (byte)(Constants.CMD_DATA | 0x80), // Mark as parity
                    -1 - p, // Negative sequence for parity
                    parityData
            );

            // Add FEC metadata
            parityPacket.setFECGroupId(groupId);
            parityPacket.setFECType(FECType.PARITY);

            parityPackets.add(parityPacket);
        }

        System.out.println("📊 [FEC] Generated " + parityPackets.size() +
                " parity packets for group " + groupId);

        return parityPackets;
    }

    // ==================== RECEIVER SIDE ====================

    /**
     * Process received packet with FEC
     */
    public VideoPacket processFECPacket(VideoPacket packet) {
        // Check if it's a FEC packet
        if (!packet.isFECEnabled()) {
            return packet; // Regular packet, no FEC
        }

        int groupId = packet.getFECGroupId();
        FECGroup group = fecGroups.computeIfAbsent(groupId, FECGroup::new);

        // Add packet to group
        if (packet.getFECType() == FECType.DATA) {
            int index = packet.getSequenceNumber() % FEC_GROUP_SIZE;
            if (index < group.dataPackets.size()) {
                group.dataPackets.set(index, packet);
            } else {
                group.dataPackets.add(packet);
            }
            group.receivedMask.set(index);
        } else {
            // Parity packet
            group.parityPackets.add(packet);
            group.receivedMask.set(FEC_GROUP_SIZE + group.parityPackets.size() - 1);
        }

        // Check if we can recover missing packets
        if (group.canRecover() && !group.isComplete()) {
            recoverMissingPackets(group);
        }

        // Clean old groups (older than 30 seconds)
        cleanOldGroups();

        return packet;
    }

    /**
     * Recover missing packets using FEC
     */
    private void recoverMissingPackets(FECGroup group) {
        System.out.println("🔧 [FEC] Attempting to recover missing packets for group " +
                group.groupId);

        // Find missing packets
        List<Integer> missingIndices = new ArrayList<>();
        for (int i = 0; i < FEC_GROUP_SIZE; i++) {
            if (!group.receivedMask.get(i)) {
                missingIndices.add(i);
            }
        }

        if (missingIndices.isEmpty()) {
            return; // No missing packets
        }

        System.out.println("🔍 [FEC] Missing packets: " + missingIndices.size());

        // Simple recovery using XOR (simplified version)
        // In production, use Reed-Solomon or more sophisticated FEC
        if (missingIndices.size() <= group.parityPackets.size()) {
            // Can recover
            for (int missingIndex : missingIndices) {
                // Simplified recovery - in real implementation use proper FEC algorithm
                byte[] recoveredData = recoverPacketData(group, missingIndex);

                if (recoveredData != null) {
                    VideoPacket recoveredPacket = new VideoPacket(
                            Constants.CMD_DATA,
                            group.groupId * FEC_GROUP_SIZE + missingIndex,
                            recoveredData
                    );

                    group.dataPackets.set(missingIndex, recoveredPacket);
                    group.receivedMask.set(missingIndex);

                    System.out.println("✅ [FEC] Recovered packet at index " + missingIndex);
                }
            }
        } else {
            System.out.println("❌ [FEC] Too many missing packets to recover");
        }
    }

    /**
     * Recover single packet data (simplified)
     */
    private byte[] recoverPacketData(FECGroup group, int missingIndex) {
        // This is a simplified recovery
        // Real implementation would use Reed-Solomon or similar

        if (group.parityPackets.isEmpty()) {
            return null;
        }

        VideoPacket parityPacket = group.parityPackets.get(0);
        byte[] recovered = Arrays.copyOf(parityPacket.getData(), parityPacket.getData().length);

        // XOR with all received packets except the missing one
        for (int i = 0; i < group.dataPackets.size(); i++) {
            if (i != missingIndex && group.receivedMask.get(i)) {
                VideoPacket packet = group.dataPackets.get(i);
                if (packet != null && packet.getData() != null) {
                    byte[] data = packet.getData();
                    for (int j = 0; j < Math.min(recovered.length, data.length); j++) {
                        recovered[j] ^= data[j];
                    }
                }
            }
        }

        return recovered;
    }

    /**
     * Clean old FEC groups
     */
    private void cleanOldGroups() {
        long now = System.currentTimeMillis();
        long timeout = 30000; // 30 seconds

        fecGroups.entrySet().removeIf(entry ->
                (now - entry.getValue().timestamp) > timeout
        );
    }

    /**
     * Get recovery statistics
     */
    public FECStatistics getStatistics() {
        FECStatistics stats = new FECStatistics();

        for (FECGroup group : fecGroups.values()) {
            stats.totalGroups++;
            if (group.isComplete()) {
                stats.completeGroups++;
            }
            stats.totalPackets += group.receivedMask.cardinality();
            stats.missingPackets += (FEC_GROUP_SIZE - group.receivedMask.cardinality());
        }

        return stats;
    }

    /**
     * FEC Statistics
     */
    public static class FECStatistics {
        public int totalGroups;
        public int completeGroups;
        public int totalPackets;
        public int missingPackets;
        public int recoveredPackets;

        public double getRecoveryRate() {
            if (missingPackets == 0) return 0;
            return (recoveredPackets * 100.0) / missingPackets;
        }

        @Override
        public String toString() {
            return String.format("FEC Stats: Groups=%d/%d, Packets=%d, Missing=%d, Recovered=%d (%.1f%%)",
                    completeGroups, totalGroups, totalPackets, missingPackets,
                    recoveredPackets, getRecoveryRate());
        }
    }

    /**
     * FEC Type enumeration
     */
    public enum FECType {
        DATA,
        PARITY
    }
}