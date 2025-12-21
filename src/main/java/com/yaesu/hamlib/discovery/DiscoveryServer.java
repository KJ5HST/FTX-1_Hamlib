/*
 * FTX1-Hamlib - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.discovery;

import com.yaesu.hamlib.util.NetworkUtils;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * UDP discovery server for ftx1-hamlib.
 * <p>
 * Listens on UDP port 4534 for discovery requests and responds with
 * server information so clients can find servers on the local network.
 * </p>
 * <p>
 * Protocol:
 * <ul>
 *   <li>Request: "FTX1-DISCOVER"</li>
 *   <li>Response: "FTX1-SERVER|ip|catPort|audioPort|rigModel|callsign"</li>
 * </ul>
 * </p>
 */
public class DiscoveryServer {

    public static final int DISCOVERY_PORT = 4534;
    public static final String DISCOVER_REQUEST = "FTX1-DISCOVER";
    public static final String SERVER_RESPONSE_PREFIX = "FTX1-SERVER";

    private final int catPort;
    private final int audioPort;
    private final String rigModel;
    private final String callsign;
    private final boolean verbose;

    private DatagramSocket socket;
    private Thread listenerThread;
    private volatile boolean running = false;

    /**
     * Creates a new discovery server.
     *
     * @param catPort   the CAT/rigctld port
     * @param audioPort the audio streaming port (0 if not enabled)
     * @param rigModel  the rig model (e.g., "FTX-1")
     * @param callsign  optional callsign
     * @param verbose   verbose logging
     */
    public DiscoveryServer(int catPort, int audioPort, String rigModel, String callsign, boolean verbose) {
        this.catPort = catPort;
        this.audioPort = audioPort;
        this.rigModel = rigModel != null ? rigModel : "FTX-1";
        this.callsign = callsign != null ? callsign : "";
        this.verbose = verbose;
    }

    /**
     * Starts the discovery server.
     *
     * @throws IOException if unable to bind to the discovery port
     */
    public void start() throws IOException {
        socket = new DatagramSocket(DISCOVERY_PORT);
        socket.setBroadcast(true);
        running = true;

        listenerThread = new Thread(this::listenLoop, "DiscoveryServer");
        listenerThread.setDaemon(true);
        listenerThread.start();

        if (verbose) {
            System.out.println("Discovery server listening on UDP port " + DISCOVERY_PORT);
        }
    }

    /**
     * Stops the discovery server.
     */
    public void stop() {
        running = false;

        if (socket != null) {
            socket.close();
        }

        if (listenerThread != null) {
            listenerThread.interrupt();
            try {
                listenerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Checks if the server is running.
     */
    public boolean isRunning() {
        return running && socket != null && !socket.isClosed();
    }

    private void listenLoop() {
        byte[] buffer = new byte[256];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();

                if (DISCOVER_REQUEST.equals(message)) {
                    if (verbose) {
                        System.out.println("Discovery request from " + packet.getAddress().getHostAddress());
                    }
                    sendResponse(packet.getAddress(), packet.getPort());
                }
            } catch (SocketException e) {
                // Socket closed - normal shutdown
                if (running) {
                    break;
                }
            } catch (IOException e) {
                if (running && verbose) {
                    System.err.println("Discovery error: " + e.getMessage());
                }
            }
        }
    }

    private void sendResponse(InetAddress address, int port) {
        try {
            String localIP = NetworkUtils.getLocalIPAddress();

            // Format: FTX1-SERVER|ip|catPort|audioPort|rigModel|callsign
            String response = String.format("%s|%s|%d|%d|%s|%s",
                SERVER_RESPONSE_PREFIX,
                localIP,
                catPort,
                audioPort,
                rigModel,
                callsign);

            byte[] data = response.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);

            if (verbose) {
                System.out.println("Sent discovery response: " + response);
            }
        } catch (IOException e) {
            if (verbose) {
                System.err.println("Failed to send discovery response: " + e.getMessage());
            }
        }
    }
}
