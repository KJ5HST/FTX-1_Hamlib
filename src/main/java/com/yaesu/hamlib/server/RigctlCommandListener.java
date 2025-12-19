/*
 * FTX1-Hamlib - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.server;

/**
 * Listener interface for rigctl command traffic.
 * <p>
 * Allows observers to monitor commands received from clients
 * and responses sent back.
 * </p>
 */
public interface RigctlCommandListener {

    /**
     * Called when a command is received from a client.
     *
     * @param clientId identifier for the client connection
     * @param command the command received
     */
    void onCommandReceived(String clientId, String command);

    /**
     * Called when a response is sent to a client.
     *
     * @param clientId identifier for the client connection
     * @param response the response sent
     */
    void onResponseSent(String clientId, String response);

    /**
     * Called when a client connects.
     *
     * @param clientId identifier for the client connection
     * @param address the client's address
     */
    void onClientConnected(String clientId, String address);

    /**
     * Called when a client disconnects.
     *
     * @param clientId identifier for the client connection
     */
    void onClientDisconnected(String clientId);
}
