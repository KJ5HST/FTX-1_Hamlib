/*
 * FTX1-Hamlib - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.server;

import com.yaesu.ftx1.FTX1;
import com.yaesu.hamlib.RigctlCommandHandler;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP server implementing the rigctld protocol.
 * <p>
 * Listens on a TCP port and handles rigctl commands from clients.
 * Supports multiple simultaneous connections.
 * </p>
 */
public class RigctldServer {

    private final FTX1 rig;
    private final int port;
    private final boolean verbose;
    private final List<RigctlCommandListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger clientIdCounter = new AtomicInteger(1);

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean running = false;

    public RigctldServer(FTX1 rig, int port, boolean verbose) {
        this.rig = rig;
        this.port = port;
        this.verbose = verbose;
    }

    /**
     * Start the server.
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        executor = Executors.newCachedThreadPool();
        running = true;

        // Accept connections in a thread
        executor.submit(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    if (verbose) {
                        System.out.println("Client connected: " +
                            clientSocket.getRemoteSocketAddress());
                    }
                    executor.submit(new ClientHandler(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Accept error: " + e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Adds a listener for command traffic.
     *
     * @param listener the listener to add
     */
    public void addCommandListener(RigctlCommandListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a command listener.
     *
     * @param listener the listener to remove
     */
    public void removeCommandListener(RigctlCommandListener listener) {
        listeners.remove(listener);
    }

    private void notifyCommandReceived(String clientId, String command) {
        for (RigctlCommandListener listener : listeners) {
            try {
                listener.onCommandReceived(clientId, command);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }

    private void notifyResponseSent(String clientId, String response) {
        for (RigctlCommandListener listener : listeners) {
            try {
                listener.onResponseSent(clientId, response);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }

    private void notifyClientConnected(String clientId, String address) {
        for (RigctlCommandListener listener : listeners) {
            try {
                listener.onClientConnected(clientId, address);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }

    private void notifyClientDisconnected(String clientId) {
        for (RigctlCommandListener listener : listeners) {
            try {
                listener.onClientDisconnected(clientId);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }

    /**
     * Stop the server.
     * Closes the server socket first to unblock accept(), then sets running=false.
     */
    public void stop() {
        // Close socket first to unblock the accept() call in the listener thread
        ServerSocket socketToClose = serverSocket;
        serverSocket = null;

        if (socketToClose != null) {
            try {
                socketToClose.close();
            } catch (IOException e) {
                // Ignore
            }
        }

        // Now safe to set running=false after socket is closed
        running = false;

        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Handler for a single client connection.
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final String clientId;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientId = "client-" + clientIdCounter.getAndIncrement();
        }

        @Override
        public void run() {
            RigctlCommandHandler handler = new RigctlCommandHandler(rig, verbose);
            String clientAddr = socket.getRemoteSocketAddress().toString();
            notifyClientConnected(clientId, clientAddr);

            try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(
                     socket.getOutputStream(), true)) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (verbose) {
                        System.out.println("< " + line);
                    }

                    String command = line.trim();
                    notifyCommandReceived(clientId, command);

                    String response = handler.handleCommand(command);

                    if (verbose) {
                        System.out.println("> " + response.replace("\n", "\\n"));
                    }

                    notifyResponseSent(clientId, response);
                    writer.print(response);
                    writer.flush();
                }
            } catch (IOException e) {
                if (verbose) {
                    System.out.println("Client disconnected: " + e.getMessage());
                }
            } finally {
                notifyClientDisconnected(clientId);
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }

            if (verbose) {
                System.out.println("Client handler finished");
            }
        }
    }
}
