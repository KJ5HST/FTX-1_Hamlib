/*
 * FTX1-Hamlib - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.discovery;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * UDP discovery client for finding ftx1-hamlib servers on the local network.
 * <p>
 * Broadcasts a discovery request and collects responses from any servers
 * listening on the network.
 * </p>
 */
public class DiscoveryClient {

    private static final int TIMEOUT_MS = 2000;  // Wait 2 seconds for responses

    /**
     * Information about a discovered server.
     */
    public static class ServerInfo {
        private final String ipAddress;
        private final int catPort;
        private final int audioPort;
        private final String rigModel;
        private final String callsign;

        public ServerInfo(String ipAddress, int catPort, int audioPort, String rigModel, String callsign) {
            this.ipAddress = ipAddress;
            this.catPort = catPort;
            this.audioPort = audioPort;
            this.rigModel = rigModel;
            this.callsign = callsign;
        }

        public String getIpAddress() { return ipAddress; }
        public int getCatPort() { return catPort; }
        public int getAudioPort() { return audioPort; }
        public String getRigModel() { return rigModel; }
        public String getCallsign() { return callsign; }

        /**
         * Returns a display string for the server.
         */
        public String getDisplayName() {
            StringBuilder sb = new StringBuilder();
            sb.append(rigModel);
            if (callsign != null && !callsign.isEmpty()) {
                sb.append(" (").append(callsign).append(")");
            }
            sb.append(" - ").append(ipAddress);
            return sb.toString();
        }

        @Override
        public String toString() {
            return getDisplayName();
        }

        /**
         * Parses a server response string.
         *
         * @param response the response string (FTX1-SERVER|ip|catPort|audioPort|rigModel|callsign)
         * @return the parsed ServerInfo, or null if invalid
         */
        public static ServerInfo parse(String response) {
            if (response == null || !response.startsWith(DiscoveryServer.SERVER_RESPONSE_PREFIX)) {
                return null;
            }

            String[] parts = response.split("\\|");
            if (parts.length < 5) {
                return null;
            }

            try {
                String ip = parts[1];
                int catPort = Integer.parseInt(parts[2]);
                int audioPort = Integer.parseInt(parts[3]);
                String rigModel = parts[4];
                String callsign = parts.length > 5 ? parts[5] : "";

                return new ServerInfo(ip, catPort, audioPort, rigModel, callsign);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /**
     * Discovers ftx1-hamlib servers on the local network.
     *
     * @return list of discovered servers
     * @throws IOException if network error occurs
     */
    public static List<ServerInfo> discover() throws IOException {
        return discover(TIMEOUT_MS);
    }

    /**
     * Discovers ftx1-hamlib servers on the local network.
     *
     * @param timeoutMs how long to wait for responses
     * @return list of discovered servers
     * @throws IOException if network error occurs
     */
    public static List<ServerInfo> discover(int timeoutMs) throws IOException {
        List<ServerInfo> servers = new ArrayList<>();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(500);  // Short timeout for receive loops

            // Send discovery request as broadcast
            byte[] requestData = DiscoveryServer.DISCOVER_REQUEST.getBytes(StandardCharsets.UTF_8);
            DatagramPacket request = new DatagramPacket(
                requestData,
                requestData.length,
                InetAddress.getByName("255.255.255.255"),
                DiscoveryServer.DISCOVERY_PORT
            );
            socket.send(request);

            // Also try subnet broadcast for each interface
            sendSubnetBroadcasts(socket, requestData);

            // Collect responses
            byte[] buffer = new byte[512];
            long deadline = System.currentTimeMillis() + timeoutMs;

            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);

                    String message = new String(response.getData(), 0, response.getLength(), StandardCharsets.UTF_8);
                    ServerInfo info = ServerInfo.parse(message);

                    if (info != null && !containsServer(servers, info)) {
                        servers.add(info);
                    }
                } catch (SocketTimeoutException e) {
                    // Continue waiting until deadline
                }
            }
        }

        return servers;
    }

    /**
     * Sends discovery broadcasts on each network interface's subnet.
     */
    private static void sendSubnetBroadcasts(DatagramSocket socket, byte[] requestData) {
        try {
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                for (InterfaceAddress ifaceAddr : iface.getInterfaceAddresses()) {
                    InetAddress broadcast = ifaceAddr.getBroadcast();
                    if (broadcast != null) {
                        try {
                            DatagramPacket packet = new DatagramPacket(
                                requestData,
                                requestData.length,
                                broadcast,
                                DiscoveryServer.DISCOVERY_PORT
                            );
                            socket.send(packet);
                        } catch (IOException e) {
                            // Ignore broadcast failures on individual interfaces
                        }
                    }
                }
            }
        } catch (SocketException e) {
            // Ignore
        }
    }

    /**
     * Checks if a server with the same IP is already in the list.
     */
    private static boolean containsServer(List<ServerInfo> servers, ServerInfo info) {
        for (ServerInfo existing : servers) {
            if (existing.getIpAddress().equals(info.getIpAddress()) &&
                existing.getCatPort() == info.getCatPort()) {
                return true;
            }
        }
        return false;
    }
}
