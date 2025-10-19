package com.hutech.videostreaming.test;

import java.net.*;
import java.util.*;

/**
 * Multicast Network Testing Tool
 * Tests multicast connectivity between machines
 */
public class MulticastTest {

    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private static final int TEST_DURATION = 10; // seconds

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.println("====================================");
        System.out.println("   MULTICAST NETWORK TESTING TOOL   ");
        System.out.println("====================================");
        System.out.println();
        System.out.println("Select mode:");
        System.out.println("1. Sender (Server mode)");
        System.out.println("2. Receiver (Client mode)");
        System.out.println("3. Network Interface Info");
        System.out.println("4. Full Diagnostic");
        System.out.print("Enter choice (1-4): ");

        int choice = scanner.nextInt();

        switch (choice) {
            case 1:
                runSender();
                break;
            case 2:
                runReceiver();
                break;
            case 3:
                showNetworkInfo();
                break;
            case 4:
                runFullDiagnostic();
                break;
            default:
                System.out.println("Invalid choice!");
        }

        scanner.close();
    }

    /**
     * Run as multicast sender
     */
    private static void runSender() throws Exception {
        System.out.println("\n🎬 Starting SENDER mode...\n");

        MulticastSocket socket = new MulticastSocket();
        InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);

        // Select network interface
        NetworkInterface nif = selectNetworkInterface();
        if (nif != null) {
            socket.setNetworkInterface(nif);
            System.out.println("✅ Using interface: " + nif.getDisplayName());
        }

        socket.setTimeToLive(1);

        System.out.println("📡 Sending to: " + MULTICAST_ADDRESS + ":" + MULTICAST_PORT);
        System.out.println("⏱️ Duration: " + TEST_DURATION + " seconds");
        System.out.println("\n📤 Sending test packets...\n");

        String hostname = InetAddress.getLocalHost().getHostName();
        int packetsSent = 0;
        long startTime = System.currentTimeMillis();

        while ((System.currentTimeMillis() - startTime) < TEST_DURATION * 1000) {
            String message = String.format("TEST_PACKET_%d_FROM_%s", packetsSent, hostname);
            byte[] data = message.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    data, data.length, group, MULTICAST_PORT
            );

            socket.send(packet);
            packetsSent++;

            if (packetsSent % 10 == 0) {
                System.out.println("   Sent " + packetsSent + " packets");
            }

            Thread.sleep(100); // 10 packets per second
        }

        // Send stop signal
        byte[] stopData = "STOP_TEST".getBytes();
        DatagramPacket stopPacket = new DatagramPacket(
                stopData, stopData.length, group, MULTICAST_PORT
        );
        socket.send(stopPacket);

        socket.close();

        System.out.println("\n✅ Test completed!");
        System.out.println("📊 Total packets sent: " + packetsSent);
    }

    /**
     * Run as multicast receiver
     */
    private static void runReceiver() throws Exception {
        System.out.println("\n📺 Starting RECEIVER mode...\n");

        MulticastSocket socket = new MulticastSocket(MULTICAST_PORT);
        InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);

        // Select network interface
        NetworkInterface nif = selectNetworkInterface();
        if (nif != null) {
            socket.setNetworkInterface(nif);
            socket.joinGroup(new InetSocketAddress(group, MULTICAST_PORT), nif);
            System.out.println("✅ Using interface: " + nif.getDisplayName());
        } else {
            socket.joinGroup(group);
        }

        socket.setSoTimeout(1000); // 1 second timeout

        System.out.println("📡 Listening on: " + MULTICAST_ADDRESS + ":" + MULTICAST_PORT);
        System.out.println("⏳ Waiting for packets (press Ctrl+C to stop)...\n");

        byte[] buffer = new byte[1024];
        int packetsReceived = 0;
        long firstPacketTime = 0;
        Map<String, Integer> senders = new HashMap<>();

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());

                if (message.equals("STOP_TEST")) {
                    System.out.println("\n🛑 Stop signal received");
                    break;
                }

                packetsReceived++;
                if (firstPacketTime == 0) {
                    firstPacketTime = System.currentTimeMillis();
                }

                // Track sender
                String senderAddr = packet.getAddress().getHostAddress();
                senders.put(senderAddr, senders.getOrDefault(senderAddr, 0) + 1);

                if (packetsReceived == 1 || packetsReceived % 10 == 0) {
                    System.out.println("📥 Received " + packetsReceived +
                            " packets from " + senderAddr);
                }

            } catch (SocketTimeoutException e) {
                if (packetsReceived > 0) {
                    long elapsed = (System.currentTimeMillis() - firstPacketTime) / 1000;
                    if (elapsed > TEST_DURATION + 2) {
                        System.out.println("\n⏱️ Timeout - no packets for " + TEST_DURATION + " seconds");
                        break;
                    }
                }
            }
        }

        socket.leaveGroup(group);
        socket.close();

        System.out.println("\n✅ Test completed!");
        System.out.println("\n📊 Statistics:");
        System.out.println("   Total packets received: " + packetsReceived);
        System.out.println("   Unique senders: " + senders.size());

        if (!senders.isEmpty()) {
            System.out.println("\n   Packets per sender:");
            for (Map.Entry<String, Integer> entry : senders.entrySet()) {
                System.out.println("      " + entry.getKey() + ": " + entry.getValue());
            }
        }

        if (packetsReceived == 0) {
            System.out.println("\n❌ No packets received! Possible issues:");
            System.out.println("   - Firewall blocking UDP port " + MULTICAST_PORT);
            System.out.println("   - Different network subnet");
            System.out.println("   - IGMP snooping not enabled");
            System.out.println("   - Wrong network interface selected");
        }
    }

    /**
     * Show network interface information
     */
    private static void showNetworkInfo() throws Exception {
        System.out.println("\n🔍 NETWORK INTERFACES INFORMATION\n");

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        int count = 0;

        while (interfaces.hasMoreElements()) {
            NetworkInterface nif = interfaces.nextElement();

            if (!nif.isUp() || nif.isLoopback()) continue;

            count++;
            System.out.println("Interface #" + count + ":");
            System.out.println("  Name: " + nif.getName());
            System.out.println("  Display: " + nif.getDisplayName());
            System.out.println("  Up: " + nif.isUp());
            System.out.println("  Virtual: " + nif.isVirtual());
            System.out.println("  Multicast: " + nif.supportsMulticast());
            System.out.println("  MTU: " + nif.getMTU());

            Enumeration<InetAddress> addresses = nif.getInetAddresses();
            System.out.println("  Addresses:");
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                System.out.println("    - " + addr.getHostAddress() +
                        (addr instanceof Inet4Address ? " (IPv4)" : " (IPv6)"));
            }

            System.out.println();
        }

        // Test multicast route
        System.out.println("📡 MULTICAST ROUTING TEST:");
        try {
            InetAddress multicastAddr = InetAddress.getByName(MULTICAST_ADDRESS);
            System.out.println("  Multicast address " + MULTICAST_ADDRESS + " is reachable");

            if (multicastAddr.isMulticastAddress()) {
                System.out.println("  ✅ Valid multicast address");
            }
        } catch (Exception e) {
            System.out.println("  ❌ Cannot resolve multicast address: " + e.getMessage());
        }
    }

    /**
     * Run full diagnostic
     */
    private static void runFullDiagnostic() throws Exception {
        System.out.println("\n🔬 FULL NETWORK DIAGNOSTIC\n");

        // 1. Check Java version
        System.out.println("1️⃣ Java Version:");
        System.out.println("   " + System.getProperty("java.version"));

        // 2. Check OS
        System.out.println("\n2️⃣ Operating System:");
        System.out.println("   " + System.getProperty("os.name") + " " +
                System.getProperty("os.version"));

        // 3. Check hostname
        System.out.println("\n3️⃣ Hostname:");
        System.out.println("   " + InetAddress.getLocalHost().getHostName());

        // 4. Check network interfaces
        System.out.println("\n4️⃣ Active Network Interfaces:");
        List<NetworkInterface> activeInterfaces = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface nif = interfaces.nextElement();
            if (nif.isUp() && !nif.isLoopback() && nif.supportsMulticast()) {
                activeInterfaces.add(nif);
                System.out.println("   ✅ " + nif.getName() + " - " + nif.getDisplayName());
            }
        }

        if (activeInterfaces.isEmpty()) {
            System.out.println("   ❌ No suitable network interfaces found!");
        }

        // 5. Test multicast socket
        System.out.println("\n5️⃣ Multicast Socket Test:");
        try {
            MulticastSocket testSocket = new MulticastSocket(MULTICAST_PORT);
            testSocket.setReuseAddress(true);
            System.out.println("   ✅ Can create multicast socket on port " + MULTICAST_PORT);

            // Test buffer sizes
            System.out.println("   Send buffer: " + testSocket.getSendBufferSize() + " bytes");
            System.out.println("   Receive buffer: " + testSocket.getReceiveBufferSize() + " bytes");

            testSocket.close();
        } catch (Exception e) {
            System.out.println("   ❌ Failed to create socket: " + e.getMessage());
        }

        // 6. Test multicast group join
        System.out.println("\n6️⃣ Multicast Group Join Test:");
        try {
            MulticastSocket socket = new MulticastSocket(MULTICAST_PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            System.out.println("   ✅ Successfully joined group " + MULTICAST_ADDRESS);
            socket.leaveGroup(group);
            socket.close();
        } catch (Exception e) {
            System.out.println("   ❌ Failed to join group: " + e.getMessage());
        }

        // 7. Firewall hint
        System.out.println("\n7️⃣ Firewall Check:");
        System.out.println("   ⚠️ Please ensure firewall allows UDP port " + MULTICAST_PORT);
        System.out.println("   Run these commands to check:");
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            System.out.println("   netsh advfirewall firewall show rule name=all | findstr " + MULTICAST_PORT);
        } else if (os.contains("nix") || os.contains("nux")) {
            System.out.println("   sudo iptables -L -n | grep " + MULTICAST_PORT);
        }

        System.out.println("\n✅ Diagnostic complete!");
        System.out.println("\n💡 Next steps:");
        System.out.println("   1. Run this tool in SENDER mode on server machine");
        System.out.println("   2. Run this tool in RECEIVER mode on client machine");
        System.out.println("   3. If no packets received, check firewall and network settings");
    }

    /**
     * Select network interface
     */
    private static NetworkInterface selectNetworkInterface() throws Exception {
        List<NetworkInterface> candidates = new ArrayList<>();

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface nif = interfaces.nextElement();

            if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
            if (!nif.supportsMulticast()) continue;

            // Check for IPv4
            Enumeration<InetAddress> addresses = nif.getInetAddresses();
            boolean hasIPv4 = false;
            while (addresses.hasMoreElements()) {
                if (addresses.nextElement() instanceof Inet4Address) {
                    hasIPv4 = true;
                    break;
                }
            }

            if (hasIPv4) {
                candidates.add(nif);
            }
        }

        if (candidates.isEmpty()) {
            System.out.println("⚠️ No suitable network interfaces found");
            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // Let user choose
        System.out.println("\nSelect network interface:");
        for (int i = 0; i < candidates.size(); i++) {
            NetworkInterface nif = candidates.get(i);
            System.out.println((i + 1) + ". " + nif.getName() + " - " + nif.getDisplayName());
        }

        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter choice (1-" + candidates.size() + "): ");
        int choice = scanner.nextInt() - 1;

        if (choice >= 0 && choice < candidates.size()) {
            return candidates.get(choice);
        }

        return candidates.get(0); // Default to first
    }
}