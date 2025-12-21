/*
 * FTX1-Hamlib - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License.
 *
 * ACKNOWLEDGMENTS:
 *   Jeremy Miller (KO4SSD) - RIT/XIT using RC/TC commands, EX0306 tuning steps
 *   See: https://github.com/Hamlib/Hamlib/pull/1826
 */
package com.yaesu.hamlib;

import com.yaesu.ftx1.FTX1;
import com.yaesu.ftx1.exception.CatConnectionException;
import com.yaesu.hamlib.audio.client.AudioClientGUI;
import com.yaesu.hamlib.gui.HamlibGUI;
import com.yaesu.hamlib.i18n.Messages;
import com.yaesu.hamlib.server.RigctldServer;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * FTX1-Hamlib - A Hamlib-compatible daemon for FTX-1.
 * <p>
 * Provides three modes of operation:
 * </p>
 * <ul>
 *   <li>GUI mode (--gui or no args): Graphical interface</li>
 *   <li>Daemon mode (-t): TCP server compatible with rigctld protocol</li>
 *   <li>Interactive mode: Command-line interface like rigctl</li>
 * </ul>
 *
 * @author Terrell Deppe (KJ5HST)
 */
public class HamlibFTX1 {

    public static final String VERSION = "1.2.0";
    public static final int DEFAULT_PORT = 4532;
    public static final int DEFAULT_BAUD = 38400;

    private String serialPort;
    private int baudRate = DEFAULT_BAUD;
    private int tcpPort = DEFAULT_PORT;
    private boolean daemonMode = false;
    private boolean guiMode = false;
    private boolean verbose = false;

    private FTX1 rig;
    private RigctldServer server;

    public static void main(String[] args) {
        // If no args, launch GUI
        if (args.length == 0) {
            launchGUI();
            return;
        }

        // Check for --gui or --audio-client flags first
        for (String arg : args) {
            if (arg.equals("--gui") || arg.equals("-g")) {
                launchGUI();
                return;
            }
            if (arg.equals("--audio-client") || arg.equals("-a")) {
                launchAudioClient();
                return;
            }
        }

        HamlibFTX1 hamlib = new HamlibFTX1();

        if (!hamlib.parseArgs(args)) {
            System.exit(1);
        }

        if (hamlib.guiMode) {
            launchGUI();
            return;
        }

        try {
            hamlib.run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (hamlib.verbose) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    /**
     * Launch the GUI application.
     */
    private static void launchGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use default
        }

        SwingUtilities.invokeLater(() -> {
            HamlibGUI gui = new HamlibGUI();
            gui.setVisible(true);
        });
    }

    /**
     * Launch the audio client GUI application.
     */
    private static void launchAudioClient() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use default
        }

        SwingUtilities.invokeLater(() -> {
            AudioClientGUI gui = new AudioClientGUI();
            gui.setVisible(true);
        });
    }

    /**
     * Parse command-line arguments.
     */
    private boolean parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "-r":
                case "--rig-file":
                    if (i + 1 >= args.length) {
                        System.err.println("Error: -r requires serial port argument");
                        return false;
                    }
                    serialPort = args[++i];
                    break;

                case "-s":
                case "--serial-speed":
                    if (i + 1 >= args.length) {
                        System.err.println("Error: -s requires baud rate argument");
                        return false;
                    }
                    try {
                        baudRate = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        System.err.println("Error: Invalid baud rate");
                        return false;
                    }
                    break;

                case "-t":
                case "--port":
                    if (i + 1 >= args.length) {
                        System.err.println("Error: -t requires TCP port argument");
                        return false;
                    }
                    try {
                        tcpPort = Integer.parseInt(args[++i]);
                        daemonMode = true;
                    } catch (NumberFormatException e) {
                        System.err.println("Error: Invalid TCP port");
                        return false;
                    }
                    break;

                case "-T":
                case "--listen-addr":
                    // Accept but ignore for now (always binds to all interfaces)
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        i++;
                    }
                    daemonMode = true;
                    break;

                case "-g":
                case "--gui":
                    guiMode = true;
                    break;

                case "-v":
                case "--verbose":
                    verbose = true;
                    break;

                case "-h":
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;

                case "-V":
                case "--version":
                    printVersion();
                    System.exit(0);
                    break;

                default:
                    if (arg.startsWith("-")) {
                        System.err.println("Unknown option: " + arg);
                        return false;
                    }
                    // Assume it's a command for interactive mode
                    break;
            }
        }

        // GUI mode doesn't require serial port
        if (!guiMode && serialPort == null) {
            System.err.println("Error: Serial port required (-r /dev/ttyUSB0)");
            printUsage();
            return false;
        }

        return true;
    }

    /**
     * Run the daemon or interactive mode.
     */
    private void run() throws Exception {
        // Connect to the rig
        if (verbose) {
            System.out.println("Connecting to FTX-1 on " + serialPort + " at " + baudRate + " baud...");
        }

        try {
            rig = FTX1.connect(serialPort, baudRate);
        } catch (CatConnectionException e) {
            throw new Exception("Failed to connect: " + e.getMessage(), e);
        }

        if (verbose) {
            System.out.println("Connected. Head type: " + rig.getHeadType());
        }

        // Add shutdown hook for clean disconnect
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (verbose) {
                System.out.println("\nShutting down...");
            }
            shutdown();
        }));

        if (daemonMode) {
            runDaemon();
        } else {
            runInteractive();
        }
    }

    /**
     * Run as a TCP daemon (like rigctld).
     */
    private void runDaemon() throws IOException {
        server = new RigctldServer(rig, tcpPort, verbose);

        System.out.println("FTX1-Hamlib " + VERSION + " listening on port " + tcpPort);
        System.out.println("Press Ctrl+C to stop");

        server.start();

        // Block until interrupted
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            // Shutdown initiated
        }
    }

    /**
     * Run in interactive CLI mode (like rigctl).
     */
    private void runInteractive() throws IOException {
        RigctlCommandHandler handler = new RigctlCommandHandler(rig, verbose);

        System.out.println("FTX1-Hamlib " + VERSION + " - Interactive Mode");
        System.out.println("Type 'help' for commands, 'quit' to exit");
        System.out.println();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while (true) {
                System.out.print("Rig command: ");
                System.out.flush();

                line = reader.readLine();
                if (line == null) {
                    break;  // EOF
                }

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("q") ||
                    line.equalsIgnoreCase("exit")) {
                    break;
                }

                String response = handler.handleCommand(line);
                System.out.println(response);
            }
        }
    }

    /**
     * Clean shutdown.
     */
    private void shutdown() {
        if (server != null) {
            server.stop();
        }
        if (rig != null) {
            rig.close();
        }
    }

    private void printUsage() {
        System.out.println("Usage: ftx1-hamlib [OPTIONS]");
        System.out.println();
        System.out.println("Hamlib-compatible daemon for FTX-1 using Java ftx1-cat library");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -g, --gui               Launch graphical interface");
        System.out.println("  -a, --audio-client      Launch audio client GUI (for remote operation)");
        System.out.println("  -r, --rig-file PORT     Serial port (required for CLI)");
        System.out.println("  -s, --serial-speed BAUD Baud rate (default: 38400)");
        System.out.println("  -t, --port PORT         TCP port for daemon mode (default: 4532)");
        System.out.println("  -T, --listen-addr ADDR  Listen address (default: 0.0.0.0)");
        System.out.println("  -v, --verbose           Verbose output");
        System.out.println("  -h, --help              Show this help");
        System.out.println("  -V, --version           Show version");
        System.out.println();
        System.out.println("Modes:");
        System.out.println("  (no args)               Launch GUI");
        System.out.println("  -r PORT                 Interactive CLI mode");
        System.out.println("  -r PORT -t TCP_PORT     Daemon mode (rigctld compatible)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # GUI mode");
        System.out.println("  ftx1-hamlib");
        System.out.println("  ftx1-hamlib --gui");
        System.out.println();
        System.out.println("  # Interactive mode");
        System.out.println("  ftx1-hamlib -r /dev/cu.SLAB_USBtoUART");
        System.out.println();
        System.out.println("  # Daemon mode on port 4532");
        System.out.println("  ftx1-hamlib -r /dev/cu.SLAB_USBtoUART -t 4532");
    }

    private void printVersion() {
        System.out.println("FTX1-Hamlib " + VERSION);
        System.out.println("Copyright (c) 2025 Terrell Deppe (KJ5HST)");
        System.out.println("Hamlib-compatible daemon for FTX-1");
    }
}
