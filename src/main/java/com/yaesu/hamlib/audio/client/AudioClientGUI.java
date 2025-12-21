/*
 * FTX1-Hamlib - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.audio.client;

import com.yaesu.hamlib.audio.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.List;

/**
 * GUI application for the audio streaming client.
 * <p>
 * Run on the remote machine to connect to an ftx1-hamlib audio server
 * and bridge audio to/from WSJT-X via virtual audio devices.
 * </p>
 */
public class AudioClientGUI extends JFrame implements AudioStreamListener {

    private static final String VERSION = "1.0.0";
    private static final int DEFAULT_PORT = AudioStreamConfig.DEFAULT_PORT;

    // Connection components
    private JTextField hostField;
    private JSpinner portSpinner;
    private JButton connectButton;
    private JLabel connectionStatus;

    // Audio device components
    private JComboBox<AudioDeviceInfo> captureDeviceCombo;
    private JComboBox<AudioDeviceInfo> playbackDeviceCombo;
    private JButton refreshDevicesButton;

    // Status components
    private JProgressBar bufferLevel;
    private JLabel latencyLabel;
    private JLabel rxRateLabel;
    private JLabel txRateLabel;

    // State
    private AudioStreamClient client;
    private AudioDeviceManager deviceManager;
    private VirtualAudioBridge virtualBridge;
    private boolean connected = false;

    public AudioClientGUI() {
        super("FTX-1 Hamlib Audio Client");
        this.deviceManager = new AudioDeviceManager();
        this.virtualBridge = new VirtualAudioBridge(deviceManager);
        initializeUI();
        refreshAudioDevices();
    }

    private void initializeUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        // Main content panel with scroll
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Server connection section
        contentPanel.add(createConnectionPanel());
        contentPanel.add(Box.createVerticalStrut(10));

        // Audio devices section
        contentPanel.add(createDevicesPanel());
        contentPanel.add(Box.createVerticalStrut(10));

        // Status section
        contentPanel.add(createStatusPanel());
        contentPanel.add(Box.createVerticalStrut(10));

        // Instructions section
        contentPanel.add(createInstructionsPanel());

        contentPanel.add(Box.createVerticalGlue());

        // Wrap in scroll pane
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        // Window settings
        setSize(500, 600);
        setMinimumSize(new Dimension(400, 400));
        setLocationRelativeTo(null);

        // Handle window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Server Connection"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Host
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Host:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        hostField = new JTextField("192.168.1.100", 20);
        panel.add(hostField, gbc);

        // Port
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("Port:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        SpinnerNumberModel portModel = new SpinnerNumberModel(DEFAULT_PORT, 1024, 65535, 1);
        portSpinner = new JSpinner(portModel);
        panel.add(portSpinner, gbc);

        // Connect button
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> toggleConnection());
        panel.add(connectButton, gbc);

        // Status
        gbc.gridy = 3;
        connectionStatus = new JLabel("Disconnected");
        connectionStatus.setForeground(Color.GRAY);
        panel.add(connectionStatus, gbc);

        return panel;
    }

    private JPanel createDevicesPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Local Audio Devices (Virtual)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Capture device (from WSJT-X TX)
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("From WSJT-X:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        captureDeviceCombo = new JComboBox<>();
        captureDeviceCombo.setRenderer(new DeviceListCellRenderer());
        panel.add(captureDeviceCombo, gbc);

        // Playback device (to WSJT-X RX)
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("To WSJT-X:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        playbackDeviceCombo = new JComboBox<>();
        playbackDeviceCombo.setRenderer(new DeviceListCellRenderer());
        panel.add(playbackDeviceCombo, gbc);

        // Refresh button
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        refreshDevicesButton = new JButton("Refresh Devices");
        refreshDevicesButton.addActionListener(e -> refreshAudioDevices());
        panel.add(refreshDevicesButton, gbc);

        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Stream Status"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Buffer level
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Buffer:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        bufferLevel = new JProgressBar(0, 100);
        bufferLevel.setStringPainted(true);
        bufferLevel.setValue(0);
        panel.add(bufferLevel, gbc);

        // Latency
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("Latency:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        latencyLabel = new JLabel("-- ms");
        panel.add(latencyLabel, gbc);

        // RX rate
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(new JLabel("RX Rate:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        rxRateLabel = new JLabel("0 kB/s");
        panel.add(rxRateLabel, gbc);

        // TX rate
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        panel.add(new JLabel("TX Rate:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        txRateLabel = new JLabel("0 kB/s");
        panel.add(txRateLabel, gbc);

        return panel;
    }

    private JPanel createInstructionsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Setup Instructions"));

        String instructions = virtualBridge.getWSJTXInstructions();

        JTextArea textArea = new JTextArea(instructions);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        textArea.setBackground(panel.getBackground());

        panel.add(textArea, BorderLayout.CENTER);

        // Check if virtual audio is available
        if (!virtualBridge.isVirtualAudioAvailable()) {
            JLabel warning = new JLabel("⚠ No virtual audio device found - see instructions above");
            warning.setForeground(Color.RED);
            warning.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            panel.add(warning, BorderLayout.NORTH);
        }

        return panel;
    }

    private void refreshAudioDevices() {
        captureDeviceCombo.removeAllItems();
        playbackDeviceCombo.removeAllItems();

        List<AudioDeviceInfo> captureDevices = deviceManager.discoverCaptureDevices();
        List<AudioDeviceInfo> playbackDevices = deviceManager.discoverPlaybackDevices();

        for (AudioDeviceInfo device : captureDevices) {
            captureDeviceCombo.addItem(device);
        }

        for (AudioDeviceInfo device : playbackDevices) {
            playbackDeviceCombo.addItem(device);
        }

        // Try to auto-select virtual devices
        AudioDeviceInfo virtualCapture = virtualBridge.findBestCaptureDevice();
        AudioDeviceInfo virtualPlayback = virtualBridge.findBestPlaybackDevice();

        if (virtualCapture != null) {
            captureDeviceCombo.setSelectedItem(virtualCapture);
        }
        if (virtualPlayback != null) {
            playbackDeviceCombo.setSelectedItem(virtualPlayback);
        }
    }

    private void toggleConnection() {
        if (connected) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        String host = hostField.getText().trim();
        int port = (Integer) portSpinner.getValue();

        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter a server host",
                "Connection Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        AudioDeviceInfo captureDevice = (AudioDeviceInfo) captureDeviceCombo.getSelectedItem();
        AudioDeviceInfo playbackDevice = (AudioDeviceInfo) playbackDeviceCombo.getSelectedItem();

        if (captureDevice == null || playbackDevice == null) {
            JOptionPane.showMessageDialog(this,
                "Please select both audio devices",
                "Connection Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Disable controls during connection
        setControlsEnabled(false);
        connectionStatus.setText("Connecting...");
        connectionStatus.setForeground(Color.ORANGE);

        // Connect in background thread
        new SwingWorker<Void, Void>() {
            private Exception error;

            @Override
            protected Void doInBackground() {
                try {
                    client = new AudioStreamClient(host, port);
                    client.setCaptureDevice(captureDevice);
                    client.setPlaybackDevice(playbackDevice);
                    client.addStreamListener(AudioClientGUI.this);
                    client.connect();
                } catch (Exception e) {
                    error = e;
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(AudioClientGUI.this,
                        "Connection failed: " + error.getMessage(),
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE);
                    setControlsEnabled(true);
                    connectionStatus.setText("Disconnected");
                    connectionStatus.setForeground(Color.GRAY);
                } else {
                    connected = true;
                    updateUIState();
                }
            }
        }.execute();
    }

    private void disconnect() {
        if (client != null) {
            client.disconnect();
            client = null;
        }
        connected = false;
        updateUIState();
    }

    private void setControlsEnabled(boolean enabled) {
        hostField.setEnabled(enabled);
        portSpinner.setEnabled(enabled);
        captureDeviceCombo.setEnabled(enabled);
        playbackDeviceCombo.setEnabled(enabled);
        refreshDevicesButton.setEnabled(enabled);
    }

    private void updateUIState() {
        setControlsEnabled(!connected);

        connectButton.setText(connected ? "Disconnect" : "Connect");

        if (connected) {
            connectionStatus.setText("Connected to " + hostField.getText());
            connectionStatus.setForeground(new Color(0, 128, 0));
        } else {
            connectionStatus.setText("Disconnected");
            connectionStatus.setForeground(Color.GRAY);
            bufferLevel.setValue(0);
            bufferLevel.setString("0%");
            latencyLabel.setText("-- ms");
            rxRateLabel.setText("0 kB/s");
            txRateLabel.setText("0 kB/s");
        }
    }

    // AudioStreamListener implementation

    @Override
    public void onClientConnected(String clientId, String address) {
        // Handled in connect()
    }

    @Override
    public void onClientDisconnected(String clientId) {
        SwingUtilities.invokeLater(() -> {
            connected = false;
            updateUIState();
        });
    }

    @Override
    public void onStreamStarted(String clientId, AudioStreamConfig config) {
        SwingUtilities.invokeLater(() -> {
            connectionStatus.setText("Streaming: " + config.getSampleRate() + " Hz");
        });
    }

    @Override
    public void onStreamStopped(String clientId) {
        SwingUtilities.invokeLater(() -> {
            connectionStatus.setText("Connected (not streaming)");
        });
    }

    @Override
    public void onStatisticsUpdate(String clientId, AudioStreamStats stats) {
        SwingUtilities.invokeLater(() -> {
            bufferLevel.setValue(stats.getBufferFillPercent());
            bufferLevel.setString(stats.getBufferFillPercent() + "% (" + stats.getBufferLevelMs() + " ms)");
            latencyLabel.setText(stats.getLatencyMs() + " ms");
            rxRateLabel.setText(String.format("%.1f kB/s", stats.getRxKBps()));
            txRateLabel.setText(String.format("%.1f kB/s", stats.getTxKBps()));
        });
    }

    @Override
    public void onError(String clientId, String error) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                error,
                "Audio Error",
                JOptionPane.ERROR_MESSAGE);
            disconnect();
        });
    }

    /**
     * Custom renderer for device combo boxes.
     */
    private static class DeviceListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof AudioDeviceInfo) {
                AudioDeviceInfo device = (AudioDeviceInfo) value;
                String text = device.getName();
                if (device.isVirtual()) {
                    text += " [VIRTUAL]";
                }
                setText(text);
            }

            return this;
        }
    }

    /**
     * Main entry point for the audio client GUI.
     */
    public static void main(String[] args) {
        // Set look and feel
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
}
