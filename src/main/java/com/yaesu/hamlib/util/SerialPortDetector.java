/*
 * FTX1-Hamlib - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.util;

import com.yaesu.ftx1.FTX1;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Utility for auto-detecting FTX-1 radios on serial ports.
 * <p>
 * Probes candidate ports by attempting to connect and send an ID command,
 * checking for a valid FTX-1 response.
 * </p>
 */
public class SerialPortDetector {

    private static final int PROBE_TIMEOUT_MS = 2000;
    private static final int[] BAUD_RATES = {38400, 19200, 9600, 4800};

    /**
     * Result of probing a serial port.
     */
    public static class DetectionResult {
        private final String port;
        private final int baudRate;
        private final String rigId;
        private final String headType;

        public DetectionResult(String port, int baudRate, String rigId, String headType) {
            this.port = port;
            this.baudRate = baudRate;
            this.rigId = rigId;
            this.headType = headType;
        }

        public String getPort() { return port; }
        public int getBaudRate() { return baudRate; }
        public String getRigId() { return rigId; }
        public String getHeadType() { return headType; }

        @Override
        public String toString() {
            return port + " @ " + baudRate + " baud (" + headType + ")";
        }
    }

    /**
     * Listener for detection progress updates.
     */
    public interface DetectionListener {
        void onProgress(String port, String status);
        void onDetected(DetectionResult result);
    }

    /**
     * Detects FTX-1 radios on the given ports.
     *
     * @param ports list of port names to probe
     * @param listener optional progress listener
     * @return list of detected radios
     */
    public static List<DetectionResult> detect(List<String> ports, DetectionListener listener) {
        List<DetectionResult> results = new ArrayList<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        for (String port : ports) {
            if (listener != null) {
                listener.onProgress(port, "Probing...");
            }

            DetectionResult result = probePort(port, executor);
            if (result != null) {
                results.add(result);
                if (listener != null) {
                    listener.onDetected(result);
                }
            }
        }

        executor.shutdownNow();
        return results;
    }

    /**
     * Probes a single port to check for FTX-1.
     */
    private static DetectionResult probePort(String port, ExecutorService executor) {
        // Try each baud rate
        for (int baud : BAUD_RATES) {
            Future<DetectionResult> future = executor.submit(() -> tryConnect(port, baud));

            try {
                DetectionResult result = future.get(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (result != null) {
                    return result;
                }
            } catch (TimeoutException e) {
                future.cancel(true);
                // Try next baud rate
            } catch (Exception e) {
                // Try next baud rate
            }
        }

        return null;
    }

    /**
     * Attempts to connect to the port and verify it's an FTX-1.
     */
    private static DetectionResult tryConnect(String port, int baud) {
        FTX1 rig = null;
        try {
            rig = FTX1.connect(port, baud);

            // If we get here, connection succeeded - get head type
            String headType = rig.getHeadType() != null ?
                rig.getHeadType().toString() : "Unknown";

            return new DetectionResult(port, baud, "FTX-1", headType);

        } catch (Exception e) {
            // Not an FTX-1 or connection failed
            return null;
        } finally {
            if (rig != null) {
                try {
                    rig.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Scans system for serial ports that might be FTX-1.
     *
     * @return list of candidate port names
     */
    public static List<String> scanCandidatePorts() {
        List<String> ports = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("mac") || os.contains("nix") || os.contains("nux")) {
            scanUnixPorts(ports);
        } else if (os.contains("win")) {
            scanWindowsPorts(ports);
        }

        return ports;
    }

    private static void scanUnixPorts(List<String> ports) {
        java.io.File devDir = new java.io.File("/dev");
        if (devDir.exists() && devDir.isDirectory()) {
            String[] files = devDir.list();
            if (files != null) {
                for (String file : files) {
                    // macOS: cu.* ports (SLAB_USBtoUART, usbserial, etc.)
                    // Silicon Labs CP210x creates two ports - we want both to probe
                    if (file.startsWith("cu.SLAB") ||
                        file.startsWith("cu.usbserial") ||
                        file.startsWith("cu.wchusbserial") ||
                        file.startsWith("cu.USB")) {
                        ports.add("/dev/" + file);
                    }
                    // Linux: ttyUSB*, ttyACM*
                    if (file.startsWith("ttyUSB") || file.startsWith("ttyACM")) {
                        ports.add("/dev/" + file);
                    }
                }
            }
        }
    }

    private static void scanWindowsPorts(List<String> ports) {
        // Method 1: Try to use Windows .NET SerialPort class via PowerShell
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-Command",
                "[System.IO.Ports.SerialPort]::GetPortNames()"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.toUpperCase().startsWith("COM")) {
                        ports.add(line.toUpperCase());
                    }
                }
            } finally {
                p.waitFor();
                p.destroyForcibly();
            }
        } catch (Exception e) {
            // PowerShell method failed
        }

        // Method 2: Probe common COM ports if PowerShell didn't find any
        if (ports.isEmpty()) {
            for (int i = 1; i <= 20; i++) {
                String port = "COM" + i;
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                        "cmd", "/c", "mode " + port + " 2>nul"
                    );
                    Process p = pb.start();
                    try (var is = p.getInputStream(); var es = p.getErrorStream()) {
                        is.readAllBytes();
                        es.readAllBytes();
                    }
                    int exitCode = p.waitFor();
                    if (exitCode == 0) {
                        ports.add(port);
                    }
                } catch (Exception e) {
                    // Port doesn't exist or can't be checked
                }
            }
        }
    }
}
