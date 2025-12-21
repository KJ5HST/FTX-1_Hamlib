/*
 * FTX1-Hamlib - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.gui;

import com.yaesu.hamlib.audio.*;
import com.yaesu.hamlib.i18n.Messages;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.util.List;

/**
 * Panel for audio streaming controls in HamlibGUI.
 * <p>
 * Provides controls for configuring and starting the audio streaming server
 * that enables remote WSJT-X operation.
 * </p>
 */
public class AudioControlPanel extends JPanel implements AudioStreamListener {

    private static final int DEFAULT_AUDIO_PORT = AudioStreamConfig.DEFAULT_PORT;

    // Components
    private JComboBox<AudioDeviceInfo> captureDeviceCombo;
    private JComboBox<AudioDeviceInfo> playbackDeviceCombo;
    private JButton refreshDevicesButton;
    private JSpinner audioPortSpinner;
    private JButton streamButton;
    private JLabel streamStatus;
    private JProgressBar bufferLevel;
    private JLabel latencyLabel;
    private JLabel clientsLabel;
    private JLabel rxRateLabel;
    private JLabel txRateLabel;

    // State
    private AudioStreamServer audioServer;
    private AudioDeviceManager deviceManager;
    private boolean serverRunning = false;

    /**
     * Creates a new AudioControlPanel.
     */
    public AudioControlPanel() {
        this.deviceManager = new AudioDeviceManager();
        initializeUI();
        refreshAudioDevices();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(5, 5));

        // Main content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Audio devices section
        contentPanel.add(createDevicesPanel());
        contentPanel.add(Box.createVerticalStrut(10));

        // Server section
        contentPanel.add(createServerPanel());
        contentPanel.add(Box.createVerticalStrut(10));

        // Status section
        contentPanel.add(createStatusPanel());
        contentPanel.add(Box.createVerticalStrut(10));

        // Instructions panel
        contentPanel.add(createInstructionsPanel());

        // Add filler
        contentPanel.add(Box.createVerticalGlue());

        // Wrap in scroll pane
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createDevicesPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder(Messages.get("audio.devices.title")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Capture device
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel(Messages.get("audio.devices.capture")), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        captureDeviceCombo = new JComboBox<>();
        captureDeviceCombo.setRenderer(new DeviceListCellRenderer());
        panel.add(captureDeviceCombo, gbc);

        // Playback device
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel(Messages.get("audio.devices.playback")), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        playbackDeviceCombo = new JComboBox<>();
        playbackDeviceCombo.setRenderer(new DeviceListCellRenderer());
        panel.add(playbackDeviceCombo, gbc);

        // Refresh button
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        refreshDevicesButton = new JButton(Messages.get("audio.devices.refresh"));
        refreshDevicesButton.addActionListener(e -> refreshAudioDevices());
        panel.add(refreshDevicesButton, gbc);

        return panel;
    }

    private JPanel createServerPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder(Messages.get("audio.server.title")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Port
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel(Messages.get("audio.server.port")), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        SpinnerNumberModel portModel = new SpinnerNumberModel(DEFAULT_AUDIO_PORT, 1024, 65535, 1);
        audioPortSpinner = new JSpinner(portModel);
        panel.add(audioPortSpinner, gbc);

        // Start/Stop button
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        streamButton = new JButton(Messages.get("audio.server.start"));
        streamButton.addActionListener(e -> toggleServer());
        panel.add(streamButton, gbc);

        // Status
        gbc.gridy = 2;
        streamStatus = new JLabel(Messages.get("audio.server.status.stopped"));
        panel.add(streamStatus, gbc);

        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder(Messages.get("audio.stream.title")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Clients
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel(Messages.get("audio.stream.clients")), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        clientsLabel = new JLabel("0");
        panel.add(clientsLabel, gbc);

        // Buffer level
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel(Messages.get("audio.stream.buffer")), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        bufferLevel = new JProgressBar(0, 100);
        bufferLevel.setStringPainted(true);
        bufferLevel.setValue(0);
        panel.add(bufferLevel, gbc);

        // Latency
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(new JLabel(Messages.get("audio.stream.latency")), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        latencyLabel = new JLabel("-- ms");
        panel.add(latencyLabel, gbc);

        // RX rate
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        panel.add(new JLabel(Messages.get("audio.stream.rx")), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        rxRateLabel = new JLabel("0 kB/s");
        panel.add(rxRateLabel, gbc);

        // TX rate
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        panel.add(new JLabel(Messages.get("audio.stream.tx")), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        txRateLabel = new JLabel("0 kB/s");
        panel.add(txRateLabel, gbc);

        return panel;
    }

    private JPanel createInstructionsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder(Messages.get("audio.instructions.title")));

        JTextArea instructions = new JTextArea(Messages.get("audio.instructions.text"));
        instructions.setEditable(false);
        instructions.setLineWrap(true);
        instructions.setWrapStyleWord(true);
        instructions.setBackground(panel.getBackground());
        instructions.setFont(instructions.getFont().deriveFont(Font.PLAIN, 11f));

        panel.add(instructions, BorderLayout.CENTER);

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

        // Try to auto-select FTX-1 devices
        AudioDeviceInfo ftx1Capture = deviceManager.findFTX1CaptureDevice();
        AudioDeviceInfo ftx1Playback = deviceManager.findFTX1PlaybackDevice();

        if (ftx1Capture != null) {
            captureDeviceCombo.setSelectedItem(ftx1Capture);
        }
        if (ftx1Playback != null) {
            playbackDeviceCombo.setSelectedItem(ftx1Playback);
        }
    }

    private void toggleServer() {
        if (serverRunning) {
            stopServer();
        } else {
            startServer();
        }
    }

    private void startServer() {
        AudioDeviceInfo captureDevice = (AudioDeviceInfo) captureDeviceCombo.getSelectedItem();
        AudioDeviceInfo playbackDevice = (AudioDeviceInfo) playbackDeviceCombo.getSelectedItem();

        if (captureDevice == null || playbackDevice == null) {
            JOptionPane.showMessageDialog(this,
                Messages.get("audio.error.nodevice"),
                Messages.get("audio.error.title"),
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        int port = (Integer) audioPortSpinner.getValue();

        try {
            audioServer = new AudioStreamServer(port);
            audioServer.setCaptureDevice(captureDevice);
            audioServer.setPlaybackDevice(playbackDevice);
            audioServer.addStreamListener(this);
            audioServer.start();

            serverRunning = true;
            updateUIState();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                Messages.get("audio.error.start", e.getMessage()),
                Messages.get("audio.error.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServer() {
        if (audioServer != null) {
            audioServer.stop();
            audioServer = null;
        }

        serverRunning = false;
        updateUIState();
    }

    private void updateUIState() {
        boolean running = serverRunning;

        captureDeviceCombo.setEnabled(!running);
        playbackDeviceCombo.setEnabled(!running);
        refreshDevicesButton.setEnabled(!running);
        audioPortSpinner.setEnabled(!running);

        streamButton.setText(running ?
            Messages.get("audio.server.stop") :
            Messages.get("audio.server.start"));

        if (running) {
            int port = (Integer) audioPortSpinner.getValue();
            streamStatus.setText(Messages.get("audio.server.status.running", port));
        } else {
            streamStatus.setText(Messages.get("audio.server.status.stopped"));
            clientsLabel.setText("0");
            bufferLevel.setValue(0);
            latencyLabel.setText("-- ms");
            rxRateLabel.setText("0 kB/s");
            txRateLabel.setText("0 kB/s");
        }
    }

    /**
     * Gets the audio server instance.
     */
    public AudioStreamServer getAudioServer() {
        return audioServer;
    }

    /**
     * Checks if the server is running.
     */
    public boolean isServerRunning() {
        return serverRunning;
    }

    /**
     * Stops the server if running. Called when parent GUI is closing.
     */
    public void shutdown() {
        stopServer();
    }

    // AudioStreamListener implementation

    @Override
    public void onClientConnected(String clientId, String address) {
        SwingUtilities.invokeLater(() -> {
            clientsLabel.setText("1 (" + address + ")");
        });
    }

    @Override
    public void onClientDisconnected(String clientId) {
        SwingUtilities.invokeLater(() -> {
            clientsLabel.setText("0");
            bufferLevel.setValue(0);
            latencyLabel.setText("-- ms");
            rxRateLabel.setText("0 kB/s");
            txRateLabel.setText("0 kB/s");
        });
    }

    @Override
    public void onStreamStarted(String clientId, AudioStreamConfig config) {
        // Already handled by onClientConnected
    }

    @Override
    public void onStreamStopped(String clientId) {
        // Already handled by onClientDisconnected
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
                Messages.get("audio.error.title"),
                JOptionPane.ERROR_MESSAGE);
        });
    }

    @Override
    public void onServerStarted(int port) {
        // Already handled in startServer
    }

    @Override
    public void onServerStopped() {
        // Already handled in stopServer
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
}
