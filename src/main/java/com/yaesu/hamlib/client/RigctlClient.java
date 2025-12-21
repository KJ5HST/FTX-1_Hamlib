/*
 * FTX1-Hamlib - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.client;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.*;

/**
 * Client for connecting to a remote rigctld-compatible server.
 * <p>
 * Implements the rigctl protocol to send commands and receive responses
 * from a remote Hamlib server (like ftx1-hamlib running in daemon mode).
 * Also handles async AI (Auto-Information) updates pushed by the server.
 * </p>
 */
public class RigctlClient {

    private static final int DEFAULT_TIMEOUT = 5000;  // 5 second timeout
    private static final int READ_TIMEOUT = 100;  // Short timeout for async reads

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean connected = false;

    // AI update listeners
    private final List<AIUpdateListener> aiListeners = new CopyOnWriteArrayList<>();

    // Response queue for command/response synchronization
    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();

    // Reader thread for handling async data
    private Thread readerThread;
    private volatile boolean readerRunning = false;

    /**
     * Listener interface for AI (Auto-Information) updates.
     */
    public interface AIUpdateListener {
        /**
         * Called when an AI update is received from the server.
         *
         * @param aiData the raw AI data (e.g., "FA00014074000;")
         */
        void onAIUpdate(String aiData);
    }

    /**
     * Creates a new RigctlClient.
     *
     * @param host the remote server hostname or IP
     * @param port the remote server port (typically 4532)
     */
    public RigctlClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Adds an AI update listener.
     */
    public void addAIListener(AIUpdateListener listener) {
        if (listener != null && !aiListeners.contains(listener)) {
            aiListeners.add(listener);
        }
    }

    /**
     * Removes an AI update listener.
     */
    public void removeAIListener(AIUpdateListener listener) {
        aiListeners.remove(listener);
    }

    /**
     * Connects to the remote server and starts the reader thread.
     *
     * @throws IOException if connection fails
     */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(READ_TIMEOUT);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        connected = true;

        // Start reader thread
        readerRunning = true;
        readerThread = new Thread(this::readerLoop, "RigctlClient-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Background reader loop that handles both command responses and AI updates.
     */
    private void readerLoop() {
        StringBuilder lineBuffer = new StringBuilder();

        while (readerRunning && connected) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    // Connection closed
                    break;
                }

                // Check if this is an AI update
                if (line.startsWith("AI:")) {
                    String aiData = line.substring(3);  // Remove "AI:" prefix
                    notifyAIListeners(aiData);
                } else {
                    // Regular response - put in queue for sendCommand()
                    responseQueue.offer(line);
                }
            } catch (SocketTimeoutException e) {
                // Normal timeout - continue loop
            } catch (IOException e) {
                if (readerRunning) {
                    // Unexpected error
                    break;
                }
            }
        }

        // Connection lost
        if (connected) {
            connected = false;
        }
    }

    private void notifyAIListeners(String aiData) {
        for (AIUpdateListener listener : aiListeners) {
            try {
                listener.onAIUpdate(aiData);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }

    /**
     * Disconnects from the remote server.
     */
    public void disconnect() {
        readerRunning = false;
        connected = false;

        if (readerThread != null) {
            readerThread.interrupt();
            try {
                readerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // Ignore close errors
        }
        reader = null;
        writer = null;
        socket = null;
        responseQueue.clear();
    }

    /**
     * Checks if connected to the server.
     */
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Sends a command and returns the response.
     *
     * @param command the rigctl command (e.g., "f", "F 14074000", "m")
     * @return the response from the server
     * @throws IOException if communication fails
     */
    public synchronized String sendCommand(String command) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected");
        }

        // Clear any stale responses
        responseQueue.clear();

        // Send command
        writer.println(command);

        // Wait for response (may be multiple lines until we get RPRT or single value)
        StringBuilder response = new StringBuilder();

        try {
            long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT;

            while (System.currentTimeMillis() < deadline) {
                String line = responseQueue.poll(100, TimeUnit.MILLISECONDS);
                if (line == null) continue;

                if (line.startsWith("RPRT")) {
                    // End of response - RPRT 0 means success
                    if (!line.equals("RPRT 0")) {
                        throw new IOException("Command failed: " + line);
                    }
                    break;
                }

                if (response.length() > 0) {
                    response.append("\n");
                }
                response.append(line);

                // For simple single-line responses, check if we're done
                if (!command.equals("1") && !command.equals("_") && !command.startsWith("\\")) {
                    // Simple command - one line response
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for response");
        }

        return response.toString().trim();
    }

    /**
     * Gets the current frequency in Hz.
     */
    public long getFrequency() throws IOException {
        String response = sendCommand("f");
        try {
            return Long.parseLong(response.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Sets the frequency in Hz.
     */
    public void setFrequency(long frequency) throws IOException {
        sendCommand("F " + frequency);
    }

    /**
     * Gets the current mode (e.g., "USB", "LSB", "CW").
     */
    public String getMode() throws IOException {
        String response = sendCommand("m");
        // Response is "MODE\nPASSBAND" - we just want mode
        String[] parts = response.split("\n");
        return parts.length > 0 ? parts[0].trim() : "UNKNOWN";
    }

    /**
     * Sets the mode.
     */
    public void setMode(String mode) throws IOException {
        sendCommand("M " + mode + " 0");
    }

    /**
     * Gets the current VFO (e.g., "VFOA", "VFOB").
     */
    public String getVFO() throws IOException {
        String response = sendCommand("v");
        return response.trim();
    }

    /**
     * Sets the active VFO.
     */
    public void setVFO(String vfo) throws IOException {
        sendCommand("V " + vfo);
    }

    /**
     * Gets the PTT state.
     *
     * @return true if transmitting, false if receiving
     */
    public boolean getPTT() throws IOException {
        String response = sendCommand("t");
        return "1".equals(response.trim());
    }

    /**
     * Sets the PTT state.
     *
     * @param ptt true to transmit, false to receive
     */
    public void setPTT(boolean ptt) throws IOException {
        sendCommand("T " + (ptt ? "1" : "0"));
    }

    /**
     * Gets a level value.
     *
     * @param level the level name (e.g., "RFPOWER", "AF", "SQL")
     * @return the level value (0.0 to 1.0)
     */
    public double getLevel(String level) throws IOException {
        String response = sendCommand("l " + level);
        try {
            return Double.parseDouble(response.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Sets a level value.
     *
     * @param level the level name
     * @param value the value (0.0 to 1.0)
     */
    public void setLevel(String level, double value) throws IOException {
        sendCommand("L " + level + " " + value);
    }

    /**
     * Gets the split state.
     *
     * @return true if split is enabled
     */
    public boolean getSplit() throws IOException {
        String response = sendCommand("s");
        // Response is "SPLIT\nVFO" - we just want split state
        String[] parts = response.split("\n");
        return parts.length > 0 && "1".equals(parts[0].trim());
    }

    /**
     * Sets the split state.
     */
    public void setSplit(boolean split, String txVfo) throws IOException {
        sendCommand("S " + (split ? "1" : "0") + " " + txVfo);
    }

    /**
     * Sends a raw CAT command.
     *
     * @param catCommand the raw CAT command (e.g., "FA;")
     * @return the raw response
     */
    public String sendRawCommand(String catCommand) throws IOException {
        return sendCommand("w " + catCommand);
    }

    /**
     * Gets the server host.
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the server port.
     */
    public int getPort() {
        return port;
    }
}
