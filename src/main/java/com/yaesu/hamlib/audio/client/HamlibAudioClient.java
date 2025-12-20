/*
 * FTX1-Hamlib - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.audio.client;

import com.yaesu.hamlib.audio.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Command-line audio client for connecting to ftx1-hamlib audio server.
 * <p>
 * Usage: java -jar ftx1-hamlib-audio-client.jar [options]
 * </p>
 */
public class HamlibAudioClient {

    private static final String VERSION = "1.0.0";

    public static void main(String[] args) {
        try {
            new HamlibAudioClient().run(args);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    public void run(String[] args) throws Exception {
        // Parse arguments
        String host = null;
        int port = AudioStreamConfig.DEFAULT_PORT;
        String captureName = null;
        String playbackName = null;
        boolean listDevices = false;
        boolean showHelp = false;
        boolean showInstructions = false;
        boolean diagnostic = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h":
                case "--host":
                    if (i + 1 < args.length) {
                        host = args[++i];
                    }
                    break;
                case "-p":
                case "--port":
                    if (i + 1 < args.length) {
                        port = Integer.parseInt(args[++i]);
                    }
                    break;
                case "-c":
                case "--capture":
                    if (i + 1 < args.length) {
                        captureName = args[++i];
                    }
                    break;
                case "-o":
                case "--playback":
                    if (i + 1 < args.length) {
                        playbackName = args[++i];
                    }
                    break;
                case "-l":
                case "--list":
                    listDevices = true;
                    break;
                case "-i":
                case "--instructions":
                    showInstructions = true;
                    break;
                case "-d":
                case "--diagnostic":
                    diagnostic = true;
                    break;
                case "--help":
                    showHelp = true;
                    break;
                default:
                    if (!args[i].startsWith("-") && host == null) {
                        // Assume first non-option argument is host
                        host = args[i];
                    }
                    break;
            }
        }

        if (showHelp) {
            printHelp();
            return;
        }

        AudioDeviceManager deviceManager = new AudioDeviceManager();
        VirtualAudioBridge bridge = new VirtualAudioBridge(deviceManager);

        if (diagnostic) {
            System.out.println(bridge.generateDiagnosticReport());
            return;
        }

        if (showInstructions) {
            System.out.println(bridge.getInstallInstructions());
            System.out.println();
            System.out.println(bridge.getWSJTXInstructions());
            return;
        }

        if (listDevices) {
            listAudioDevices(deviceManager);
            return;
        }

        if (host == null) {
            System.err.println("Error: Server host is required");
            System.err.println("Use --help for usage information");
            System.exit(1);
        }

        // Find audio devices
        AudioDeviceInfo captureDevice = null;
        AudioDeviceInfo playbackDevice = null;

        if (captureName != null) {
            captureDevice = findDeviceByName(deviceManager.discoverCaptureDevices(), captureName);
            if (captureDevice == null) {
                System.err.println("Error: Capture device not found: " + captureName);
                System.exit(1);
            }
        } else {
            captureDevice = bridge.findBestCaptureDevice();
            if (captureDevice == null) {
                System.err.println("Error: No virtual audio capture device found");
                System.err.println("Use --instructions for setup help");
                System.exit(1);
            }
        }

        if (playbackName != null) {
            playbackDevice = findDeviceByName(deviceManager.discoverPlaybackDevices(), playbackName);
            if (playbackDevice == null) {
                System.err.println("Error: Playback device not found: " + playbackName);
                System.exit(1);
            }
        } else {
            playbackDevice = bridge.findBestPlaybackDevice();
            if (playbackDevice == null) {
                System.err.println("Error: No virtual audio playback device found");
                System.err.println("Use --instructions for setup help");
                System.exit(1);
            }
        }

        System.out.println("FTX1-Hamlib Audio Client v" + VERSION);
        System.out.println();
        System.out.println("Capture device:  " + captureDevice.getName());
        System.out.println("Playback device: " + playbackDevice.getName());
        System.out.println();
        System.out.println("Connecting to " + host + ":" + port + "...");

        // Create and configure client
        AudioStreamClient client = new AudioStreamClient(host, port);
        client.setCaptureDevice(captureDevice);
        client.setPlaybackDevice(playbackDevice);

        // Add console listener
        client.addStreamListener(new ConsoleStreamListener());

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nDisconnecting...");
            client.disconnect();
        }));

        // Connect
        try {
            client.connect();
            System.out.println("Connected! Audio streaming active.");
            System.out.println("Press Enter to disconnect, or Ctrl+C to exit.");
            System.out.println();

            // Wait for user input or disconnection
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            while (client.isConnected()) {
                if (System.in.available() > 0) {
                    reader.readLine();
                    break;
                }
                Thread.sleep(100);
            }

            client.disconnect();
            System.out.println("Disconnected.");

        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private void printHelp() {
        System.out.println("FTX1-Hamlib Audio Client v" + VERSION);
        System.out.println();
        System.out.println("Usage: java -jar ftx1-hamlib-audio-client.jar [options] <host>");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --host <host>       Server hostname or IP");
        System.out.println("  -p, --port <port>       Server port (default: 4533)");
        System.out.println("  -c, --capture <name>    Capture device name (from WSJT-X TX)");
        System.out.println("  -o, --playback <name>   Playback device name (to WSJT-X RX)");
        System.out.println("  -l, --list              List available audio devices");
        System.out.println("  -i, --instructions      Show setup instructions");
        System.out.println("  -d, --diagnostic        Run diagnostic report");
        System.out.println("      --help              Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar ftx1-hamlib-audio-client.jar 192.168.1.100");
        System.out.println("  java -jar ftx1-hamlib-audio-client.jar -h 192.168.1.100 -p 4533");
        System.out.println("  java -jar ftx1-hamlib-audio-client.jar --list");
        System.out.println("  java -jar ftx1-hamlib-audio-client.jar --instructions");
    }

    private void listAudioDevices(AudioDeviceManager deviceManager) {
        System.out.println("Available Audio Devices");
        System.out.println("=======================");
        System.out.println();

        List<AudioDeviceInfo> captureDevices = deviceManager.discoverCaptureDevices();
        System.out.println("Capture Devices (" + captureDevices.size() + "):");
        for (AudioDeviceInfo device : captureDevices) {
            System.out.print("  ");
            if (device.isVirtual()) {
                System.out.print("[VIRTUAL] ");
            }
            System.out.println(device.getName());
        }

        System.out.println();

        List<AudioDeviceInfo> playbackDevices = deviceManager.discoverPlaybackDevices();
        System.out.println("Playback Devices (" + playbackDevices.size() + "):");
        for (AudioDeviceInfo device : playbackDevices) {
            System.out.print("  ");
            if (device.isVirtual()) {
                System.out.print("[VIRTUAL] ");
            }
            System.out.println(device.getName());
        }
    }

    private AudioDeviceInfo findDeviceByName(List<AudioDeviceInfo> devices, String name) {
        String lowerName = name.toLowerCase();
        for (AudioDeviceInfo device : devices) {
            if (device.getName().toLowerCase().contains(lowerName)) {
                return device;
            }
        }
        return null;
    }

    /**
     * Console listener for stream events.
     */
    private static class ConsoleStreamListener implements AudioStreamListener {
        private long lastStatsTime = 0;

        @Override
        public void onClientConnected(String clientId, String address) {
            // Already printed by main
        }

        @Override
        public void onClientDisconnected(String clientId) {
            System.out.println("Disconnected from server");
        }

        @Override
        public void onStreamStarted(String clientId, AudioStreamConfig config) {
            System.out.println("Stream started: " + config);
        }

        @Override
        public void onStreamStopped(String clientId) {
            System.out.println("Stream stopped");
        }

        @Override
        public void onStatisticsUpdate(String clientId, AudioStreamStats stats) {
            // Print stats every 10 seconds
            long now = System.currentTimeMillis();
            if (now - lastStatsTime > 10000) {
                System.out.printf("Stats: RX=%.1f kB/s, TX=%.1f kB/s, Buffer=%d%%, Latency=%dms%n",
                    stats.getRxKBps(), stats.getTxKBps(),
                    stats.getBufferFillPercent(), stats.getLatencyMs());
                lastStatsTime = now;
            }
        }

        @Override
        public void onError(String clientId, String error) {
            System.err.println("Error: " + error);
        }
    }
}
