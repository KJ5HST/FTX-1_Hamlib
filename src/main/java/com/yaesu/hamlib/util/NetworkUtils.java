/*
 * FTX1-Hamlib - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.util;

import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Network utility methods.
 */
public class NetworkUtils {

    /**
     * Gets the primary local IP address (non-loopback, non-link-local).
     * Prefers IPv4 addresses.
     *
     * @return the primary local IP address, or "127.0.0.1" if none found
     */
    public static String getLocalIPAddress() {
        List<String> addresses = getLocalIPAddresses();
        return addresses.isEmpty() ? "127.0.0.1" : addresses.get(0);
    }

    /**
     * Gets all local IP addresses (non-loopback, non-link-local).
     * IPv4 addresses are listed first.
     *
     * @return list of local IP addresses
     */
    public static List<String> getLocalIPAddresses() {
        List<String> ipv4 = new ArrayList<>();
        List<String> ipv6 = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // Skip loopback and down interfaces
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // Skip loopback and link-local
                    if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
                        continue;
                    }

                    if (addr instanceof Inet4Address) {
                        ipv4.add(addr.getHostAddress());
                    } else if (addr instanceof Inet6Address) {
                        // Remove scope ID from IPv6 addresses
                        String ip6 = addr.getHostAddress();
                        int scopeIdx = ip6.indexOf('%');
                        if (scopeIdx > 0) {
                            ip6 = ip6.substring(0, scopeIdx);
                        }
                        ipv6.add(ip6);
                    }
                }
            }
        } catch (SocketException e) {
            // Ignore
        }

        // IPv4 first, then IPv6
        List<String> result = new ArrayList<>();
        result.addAll(ipv4);
        result.addAll(ipv6);
        return result;
    }

    /**
     * Formats server addresses for display.
     *
     * @param port the server port
     * @return formatted string like "192.168.1.100:4532"
     */
    public static String formatServerAddresses(int port) {
        List<String> addresses = getLocalIPAddresses();
        if (addresses.isEmpty()) {
            return "localhost:" + port;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < addresses.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(addresses.get(i)).append(":").append(port);
        }
        return sb.toString();
    }
}
