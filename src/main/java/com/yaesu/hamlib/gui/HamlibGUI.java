/*
 * FTX1-Hamlib GUI - Graphical interface for Hamlib daemon
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.gui;

import com.yaesu.ftx1.FTX1;
import com.yaesu.ftx1.core.CatDataListener;
import com.yaesu.ftx1.exception.CatConnectionException;
import com.yaesu.ftx1.exception.CatException;
import com.yaesu.ftx1.model.HeadType;
import com.yaesu.ftx1.model.OperatingMode;
import com.yaesu.ftx1.model.VFO;
import com.yaesu.hamlib.RigctlCommandHandler;
import com.yaesu.hamlib.audio.AudioStreamServer;
import com.yaesu.hamlib.audio.client.AudioStreamClient;
import com.yaesu.hamlib.client.RigctlClient;
import com.yaesu.hamlib.i18n.Messages;
import com.yaesu.hamlib.server.RigctlCommandListener;
import com.yaesu.hamlib.server.RigctldServer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * GUI for FTX1-Hamlib Hamlib daemon.
 * Provides daemon control, command entry, and radio status display.
 */
public class HamlibGUI extends JFrame {

    private static final String VERSION = "1.2.0";
    private static final int DEFAULT_TCP_PORT = 4532;
    private static final int DEFAULT_BAUD = 38400;

    // Connection state
    private FTX1 rig;
    private RigctldServer server;
    private RigctlCommandHandler commandHandler;
    private boolean connected = false;
    private boolean daemonRunning = false;
    private String connectedPort = null;  // Track connected port for checkmark display

    // UI Components - Connection
    private JComboBox<String> portCombo;
    private JComboBox<Integer> baudCombo;
    private JButton refreshPortsButton;
    private JButton connectButton;
    private JLabel connectionStatus;

    // UI Components - Hamlib
    private JSpinner tcpPortSpinner;
    private JButton daemonButton;
    private JLabel daemonStatus;

    // UI Components - Radio Status
    private JLabel headTypeLabel;
    // VFO-A status
    private JLabel freqLabelA;
    private JLabel modeLabelA;
    // VFO-B status
    private JLabel freqLabelB;
    private JLabel modeLabelB;
    // Common status
    private JLabel activeVfoLabel;
    private JLabel pttLabel;
    private JLabel powerLabel;
    private JLabel smeterLabel;
    private JLabel swrLabel;
    private JLabel alcLabel;
    private JLabel compLabel;

    // UI Components - Command
    private JTextField commandField;
    private JButton sendButton;
    private JTextArea responseArea;
    private JComboBox<String> quickCommandCombo;

    // UI Components - Radio Data Monitor
    private JTextArea commMonitorArea;
    private JCheckBox monitorEnabledCheckbox;
    private JCheckBox showTimestampCheckbox;
    private boolean monitorEnabled = false;
    private boolean showTimestamps = true;

    // Track active VFO for status updates
    private boolean activeVfoIsB = false;

    // Audio streaming panel
    private AudioControlPanel audioControlPanel;

    // Mode selection (Local vs Remote)
    private JRadioButton localModeButton;
    private JRadioButton remoteModeButton;
    private boolean remoteMode = false;
    private CardLayout connectionCardLayout;
    private JPanel connectionCardPanel;

    // Remote mode components
    private JTextField remoteHostField;
    private JSpinner remoteCatPortSpinner;
    private JSpinner remoteAudioPortSpinner;
    private JButton remoteConnectButton;
    private JLabel remoteConnectionStatus;

    // Remote mode client state
    private RigctlClient remoteRigClient;
    private boolean remoteConnected = false;

    public HamlibGUI() {
        super(Messages.get("app.title"));
        initializeUI();
        setupEventHandlers();
        scanSerialPorts();
    }

    private void initializeUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        // Create main panels
        JPanel topPanel = createTopPanel();
        JPanel centerPanel = createCenterPanel();
        JPanel bottomPanel = createBottomPanel();

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Set window properties
        setSize(850, 650);
        setMinimumSize(new Dimension(750, 550));
        setLocationRelativeTo(null);

        // Menu bar
        setJMenuBar(createMenuBar());

        // Set up comm monitor logging interceptor
        setupCommMonitorLogging();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu(Messages.get("menu.file"));
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem exitItem = new JMenuItem(Messages.get("menu.file.exit"), KeyEvent.VK_X);
        exitItem.addActionListener(e -> exitApplication());
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        // Settings menu
        JMenu settingsMenu = new JMenu(Messages.get("menu.settings"));
        settingsMenu.setMnemonic(KeyEvent.VK_S);

        // Language submenu - dynamically built from available locales
        JMenu languageMenu = new JMenu(Messages.get("menu.settings.language"));
        ButtonGroup langGroup = new ButtonGroup();
        Locale currentLocale = Messages.getLocale();
        boolean usingDefault = Messages.isUsingDefault();

        // Add "Default (System)" option at top
        JRadioButtonMenuItem defaultItem = new JRadioButtonMenuItem(Messages.get("lang.default"));
        defaultItem.setSelected(usingDefault);
        defaultItem.addActionListener(e -> resetToDefaultLanguage());
        langGroup.add(defaultItem);
        languageMenu.add(defaultItem);
        languageMenu.addSeparator();

        for (Locale locale : Messages.getAvailableLocales()) {
            String langKey = "lang." + locale.getLanguage();
            JRadioButtonMenuItem langItem = new JRadioButtonMenuItem(Messages.get(langKey));
            langItem.setSelected(!usingDefault && currentLocale.getLanguage().equals(locale.getLanguage()));
            langItem.addActionListener(e -> changeLanguage(locale));
            langGroup.add(langItem);
            languageMenu.add(langItem);
        }

        settingsMenu.add(languageMenu);
        menuBar.add(settingsMenu);

        // Help menu
        JMenu helpMenu = new JMenu(Messages.get("menu.help"));
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem aboutItem = new JMenuItem(Messages.get("menu.help.about"), KeyEvent.VK_A);
        aboutItem.addActionListener(e -> showAbout());
        helpMenu.add(aboutItem);

        menuBar.add(helpMenu);

        return menuBar;
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Mode selector at the very top
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.setBorder(new TitledBorder(Messages.get("mode.title")));
        ButtonGroup modeGroup = new ButtonGroup();
        localModeButton = new JRadioButton(Messages.get("mode.local"), true);
        remoteModeButton = new JRadioButton(Messages.get("mode.remote"), false);
        modeGroup.add(localModeButton);
        modeGroup.add(remoteModeButton);
        modePanel.add(localModeButton);
        modePanel.add(remoteModeButton);

        // Mode change listener
        ActionListener modeListener = e -> {
            remoteMode = remoteModeButton.isSelected();
            connectionCardLayout.show(connectionCardPanel, remoteMode ? "remote" : "local");
            if (audioControlPanel != null) {
                audioControlPanel.setRemoteMode(remoteMode);
            }
        };
        localModeButton.addActionListener(modeListener);
        remoteModeButton.addActionListener(modeListener);

        panel.add(modePanel, BorderLayout.NORTH);

        // Card panel for switching between local and remote connection panels
        connectionCardLayout = new CardLayout();
        connectionCardPanel = new JPanel(connectionCardLayout);

        // LOCAL mode panel (serial + daemon)
        JPanel localPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        localPanel.add(createLocalConnectionPanel());
        localPanel.add(createDaemonPanel());
        connectionCardPanel.add(localPanel, "local");

        // REMOTE mode panel (server connection)
        connectionCardPanel.add(createRemoteConnectionPanel(), "remote");

        panel.add(connectionCardPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createLocalConnectionPanel() {
        JPanel connectionPanel = new JPanel(new GridBagLayout());
        connectionPanel.setBorder(new TitledBorder(Messages.get("connection.title")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Port row
        gbc.gridx = 0; gbc.gridy = 0;
        connectionPanel.add(new JLabel(Messages.get("connection.port")), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        portCombo = new JComboBox<>();
        portCombo.setEditable(true);
        connectionPanel.add(portCombo, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        refreshPortsButton = new JButton("\u21BB");  // Unicode refresh symbol
        refreshPortsButton.setToolTipText(Messages.get("connection.refresh.tooltip"));
        refreshPortsButton.setMargin(new Insets(2, 6, 2, 6));
        refreshPortsButton.addActionListener(e -> scanSerialPorts());
        connectionPanel.add(refreshPortsButton, gbc);

        // Baud row
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        connectionPanel.add(new JLabel(Messages.get("connection.baud")), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        baudCombo = new JComboBox<>(new Integer[]{4800, 9600, 19200, 38400, 57600, 115200});
        baudCombo.setSelectedItem(DEFAULT_BAUD);
        connectionPanel.add(baudCombo, gbc);

        // Connect button and status
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JPanel connectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connectButton = new JButton(Messages.get("connection.connect"));
        connectionStatus = new JLabel(Messages.get("connection.status.disconnected"));
        connectionStatus.setForeground(Color.RED);
        connectPanel.add(connectButton);
        connectPanel.add(connectionStatus);
        connectionPanel.add(connectPanel, gbc);

        return connectionPanel;
    }

    private JPanel createDaemonPanel() {
        JPanel daemonPanel = new JPanel(new GridBagLayout());
        daemonPanel.setBorder(new TitledBorder(Messages.get("hamlib.title")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // TCP Port row
        gbc.gridx = 0; gbc.gridy = 0;
        daemonPanel.add(new JLabel(Messages.get("hamlib.tcpport")), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        tcpPortSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_TCP_PORT, 1024, 65535, 1));
        // Remove comma formatting from port number
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(tcpPortSpinner, "#");
        tcpPortSpinner.setEditor(editor);
        daemonPanel.add(tcpPortSpinner, gbc);

        // Daemon button and status
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        JPanel daemonControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        daemonButton = new JButton(Messages.get("hamlib.start"));
        daemonButton.setEnabled(false);
        daemonStatus = new JLabel(Messages.get("hamlib.status.stopped"));
        daemonStatus.setForeground(Color.GRAY);
        daemonControlPanel.add(daemonButton);
        daemonControlPanel.add(daemonStatus);
        daemonPanel.add(daemonControlPanel, gbc);

        // Clients label
        gbc.gridy = 2;
        JLabel clientsLabel = new JLabel(Messages.get("hamlib.compatible", DEFAULT_TCP_PORT));
        clientsLabel.setFont(clientsLabel.getFont().deriveFont(Font.ITALIC, 10f));
        daemonPanel.add(clientsLabel, gbc);

        return daemonPanel;
    }

    private JPanel createRemoteConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder(Messages.get("remote.title")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Host
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel(Messages.get("remote.host")), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        remoteHostField = new JTextField("192.168.1.100", 20);
        panel.add(remoteHostField, gbc);

        // CAT Port
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.gridwidth = 1;
        panel.add(new JLabel(Messages.get("remote.catport")), gbc);

        gbc.gridx = 1; gbc.weightx = 0.5;
        remoteCatPortSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_TCP_PORT, 1024, 65535, 1));
        JSpinner.NumberEditor catEditor = new JSpinner.NumberEditor(remoteCatPortSpinner, "#");
        remoteCatPortSpinner.setEditor(catEditor);
        panel.add(remoteCatPortSpinner, gbc);

        // Audio Port
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel(Messages.get("remote.audioport")), gbc);

        gbc.gridx = 1; gbc.weightx = 0.5;
        remoteAudioPortSpinner = new JSpinner(new SpinnerNumberModel(4533, 1024, 65535, 1));
        JSpinner.NumberEditor audioEditor = new JSpinner.NumberEditor(remoteAudioPortSpinner, "#");
        remoteAudioPortSpinner.setEditor(audioEditor);
        panel.add(remoteAudioPortSpinner, gbc);

        // Connect button and status
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3;
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        JPanel connectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        remoteConnectButton = new JButton(Messages.get("connection.connect"));
        remoteConnectButton.addActionListener(e -> toggleRemoteConnection());
        remoteConnectionStatus = new JLabel(Messages.get("connection.status.disconnected"));
        remoteConnectionStatus.setForeground(Color.RED);
        connectPanel.add(remoteConnectButton);
        connectPanel.add(remoteConnectionStatus);
        panel.add(connectPanel, gbc);

        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

        // Status panel (left side)
        JPanel statusPanel = createStatusPanel();

        // Right side - tabbed pane with commands and comm monitor
        JTabbedPane tabbedPane = new JTabbedPane();

        // Command panel tab
        JPanel commandPanel = createCommandPanel();
        tabbedPane.addTab(Messages.get("tab.commands"), commandPanel);

        // Radio Data tab
        JPanel commMonitorPanel = createCommMonitorPanel();
        tabbedPane.addTab(Messages.get("tab.radiodata"), commMonitorPanel);

        // Audio Streaming tab
        audioControlPanel = new AudioControlPanel();
        tabbedPane.addTab(Messages.get("tab.audio"), audioControlPanel);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, statusPanel, tabbedPane);
        splitPane.setDividerLocation(320);
        splitPane.setResizeWeight(0.35);

        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createCommMonitorPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Control panel at top - use WrapLayout for better resizing
        JPanel controlPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 5));

        monitorEnabledCheckbox = new JCheckBox(Messages.get("monitor.enable"));
        monitorEnabledCheckbox.addActionListener(e -> {
            monitorEnabled = monitorEnabledCheckbox.isSelected();
        });
        controlPanel.add(monitorEnabledCheckbox);

        showTimestampCheckbox = new JCheckBox(Messages.get("monitor.timestamps"), true);
        showTimestampCheckbox.addActionListener(e -> {
            showTimestamps = showTimestampCheckbox.isSelected();
        });
        controlPanel.add(showTimestampCheckbox);

        JButton clearButton = new JButton(Messages.get("commands.clear"));
        clearButton.addActionListener(e -> commMonitorArea.setText(""));
        controlPanel.add(clearButton);

        panel.add(controlPanel, BorderLayout.NORTH);

        // Monitor text area
        commMonitorArea = new JTextArea();
        commMonitorArea.setEditable(false);
        commMonitorArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        commMonitorArea.setBackground(new Color(20, 20, 30));
        commMonitorArea.setForeground(new Color(0, 255, 0));
        commMonitorArea.setCaretColor(new Color(0, 255, 0));
        DefaultCaret caret = (DefaultCaret) commMonitorArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane scrollPane = new JScrollPane(commMonitorArea);
        scrollPane.setPreferredSize(new Dimension(400, 300));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Legend at bottom
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        legendPanel.add(new JLabel(Messages.get("monitor.legend")));
        panel.add(legendPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder(Messages.get("status.title")));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Radio row - two lines: "Yaesu FTX-1" and head type
        JPanel configPanel = new JPanel(new GridLayout(1, 2, 5, 2));
        configPanel.add(new JLabel(Messages.get("status.radio")));
        headTypeLabel = new JLabel("<html>--<br>&nbsp;</html>");
        headTypeLabel.setFont(headTypeLabel.getFont().deriveFont(Font.BOLD));
        configPanel.add(headTypeLabel);
        mainPanel.add(configPanel);

        mainPanel.add(Box.createVerticalStrut(8));

        // VFO-A section
        JPanel vfoAPanel = new JPanel(new GridLayout(0, 2, 5, 2));
        vfoAPanel.setBorder(new TitledBorder(Messages.get("status.vfoa")));
        vfoAPanel.add(new JLabel(Messages.get("status.frequency")));
        freqLabelA = new JLabel("--");
        freqLabelA.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        freqLabelA.setForeground(new Color(0, 100, 0));
        vfoAPanel.add(freqLabelA);
        vfoAPanel.add(new JLabel(Messages.get("status.mode")));
        modeLabelA = new JLabel(Messages.get("status.placeholder"));
        modeLabelA.setFont(modeLabelA.getFont().deriveFont(Font.BOLD));
        vfoAPanel.add(modeLabelA);
        mainPanel.add(vfoAPanel);

        mainPanel.add(Box.createVerticalStrut(5));

        // VFO-B section
        JPanel vfoBPanel = new JPanel(new GridLayout(0, 2, 5, 2));
        vfoBPanel.setBorder(new TitledBorder(Messages.get("status.vfob")));
        vfoBPanel.add(new JLabel(Messages.get("status.frequency")));
        freqLabelB = new JLabel(Messages.get("status.placeholder"));
        freqLabelB.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        freqLabelB.setForeground(new Color(0, 100, 0));
        vfoBPanel.add(freqLabelB);
        vfoBPanel.add(new JLabel(Messages.get("status.mode")));
        modeLabelB = new JLabel(Messages.get("status.placeholder"));
        modeLabelB.setFont(modeLabelB.getFont().deriveFont(Font.BOLD));
        vfoBPanel.add(modeLabelB);
        mainPanel.add(vfoBPanel);

        mainPanel.add(Box.createVerticalStrut(8));

        // Common status section
        JPanel commonPanel = new JPanel(new GridLayout(0, 2, 5, 2));
        commonPanel.add(new JLabel(Messages.get("status.activevfo")));
        activeVfoLabel = new JLabel(Messages.get("status.placeholder"));
        activeVfoLabel.setFont(activeVfoLabel.getFont().deriveFont(Font.BOLD));
        commonPanel.add(activeVfoLabel);

        commonPanel.add(new JLabel(Messages.get("status.ptt")));
        pttLabel = new JLabel(Messages.get("status.ptt.rx"));
        pttLabel.setForeground(Color.GRAY);
        commonPanel.add(pttLabel);

        commonPanel.add(new JLabel(Messages.get("status.power")));
        powerLabel = new JLabel(Messages.get("status.placeholder"));
        commonPanel.add(powerLabel);

        commonPanel.add(new JLabel(Messages.get("status.smeter")));
        smeterLabel = new JLabel("--");
        commonPanel.add(smeterLabel);

        commonPanel.add(new JLabel(Messages.get("status.swr")));
        swrLabel = new JLabel("--");
        commonPanel.add(swrLabel);

        commonPanel.add(new JLabel(Messages.get("status.alc")));
        alcLabel = new JLabel("--");
        commonPanel.add(alcLabel);

        commonPanel.add(new JLabel(Messages.get("status.comp")));
        compLabel = new JLabel("--");
        commonPanel.add(compLabel);

        mainPanel.add(commonPanel);

        panel.add(mainPanel, BorderLayout.CENTER);

        return panel;
    }

    // CAT command keys - used to build localized command list
    private static final String[] CAT_COMMAND_KEYS = {
            "cat.ab", "cat.ac", "cat.ag", "cat.ai", "cat.ba", "cat.bc", "cat.bd", "cat.bi",
            "cat.bp", "cat.bs", "cat.bu", "cat.cf", "cat.cn", "cat.co", "cat.cs", "cat.ct",
            "cat.da", "cat.dc", "cat.dn", "cat.dt", "cat.fa", "cat.fb", "cat.fn", "cat.fr",
            "cat.ft", "cat.gt", "cat.id", "cat.if", "cat.is", "cat.ks", "cat.ky", "cat.lk",
            "cat.lm", "cat.ma", "cat.mc", "cat.md", "cat.mg", "cat.ml", "cat.mr", "cat.ms",
            "cat.mw", "cat.mx", "cat.nb", "cat.nl", "cat.nr", "cat.oa", "cat.ob", "cat.oi",
            "cat.os", "cat.pa", "cat.pb", "cat.pc", "cat.pl", "cat.pr", "cat.ps", "cat.qr",
            "cat.qs", "cat.ra", "cat.rc", "cat.rd", "cat.rg", "cat.ri", "cat.rl", "cat.rm",
            "cat.rs", "cat.rt", "cat.ru", "cat.sc", "cat.sd", "cat.sh", "cat.sm", "cat.sq",
            "cat.ss", "cat.st", "cat.sv", "cat.ts", "cat.tx", "cat.ty", "cat.ud", "cat.up",
            "cat.vd", "cat.vg", "cat.vm", "cat.vs", "cat.vx", "cat.xt"
    };

    // Hamlib command keys - used to build localized command list
    private static final String[] HAMLIB_COMMAND_KEYS = {
            "hamlib.f", "hamlib.F", "hamlib.m", "hamlib.M", "hamlib.v", "hamlib.V",
            "hamlib.t", "hamlib.T", "hamlib.s", "hamlib.S", "hamlib.l_rfpower", "hamlib.l_af",
            "hamlib.l_sql", "hamlib.l_strength", "hamlib.l_swr", "hamlib.L_rfpower",
            "hamlib.L_af", "hamlib.L_sql", "hamlib.u_lock", "hamlib.U_lock",
            "hamlib.info", "hamlib.caps", "hamlib.raw"
    };

    /**
     * Builds localized CAT commands array.
     */
    private String[] getCatCommands() {
        String[] commands = new String[CAT_COMMAND_KEYS.length + 1];
        commands[0] = Messages.get("commands.cat.select");
        for (int i = 0; i < CAT_COMMAND_KEYS.length; i++) {
            commands[i + 1] = Messages.get(CAT_COMMAND_KEYS[i]);
        }
        return commands;
    }

    /**
     * Builds localized Hamlib commands array.
     */
    private String[] getHamlibCommands() {
        String[] commands = new String[HAMLIB_COMMAND_KEYS.length + 1];
        commands[0] = Messages.get("commands.hamlib.select");
        for (int i = 0; i < HAMLIB_COMMAND_KEYS.length; i++) {
            commands[i + 1] = Messages.get(HAMLIB_COMMAND_KEYS[i]);
        }
        return commands;
    }

    // Track which command mode is active
    private boolean catModeSelected = true;
    private JRadioButton catRadio;
    private JRadioButton hamlibRadio;

    private JPanel createCommandPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder(Messages.get("commands.title")));

        // Top panel with radio buttons and dropdown
        JPanel topPanel = new JPanel(new BorderLayout(5, 0));

        // Radio buttons to switch between CAT and Hamlib
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        catRadio = new JRadioButton(Messages.get("commands.cat"), true);
        hamlibRadio = new JRadioButton(Messages.get("commands.hamlib"), false);
        ButtonGroup commandGroup = new ButtonGroup();
        commandGroup.add(catRadio);
        commandGroup.add(hamlibRadio);

        catRadio.addActionListener(e -> {
            catModeSelected = true;
            updateCommandDropdown();
        });
        hamlibRadio.addActionListener(e -> {
            catModeSelected = false;
            updateCommandDropdown();
        });

        radioPanel.add(catRadio);
        radioPanel.add(hamlibRadio);
        topPanel.add(radioPanel, BorderLayout.WEST);

        // Command dropdown
        quickCommandCombo = new JComboBox<>(getCatCommands());
        quickCommandCombo.addActionListener(e -> handleCommandSelection());
        topPanel.add(quickCommandCombo, BorderLayout.CENTER);

        panel.add(topPanel, BorderLayout.NORTH);

        // Command input
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        inputPanel.add(new JLabel(Messages.get("commands.input")), BorderLayout.WEST);
        commandField = new JTextField();
        commandField.setEnabled(false);
        commandField.addActionListener(e -> sendCurrentCommand());
        inputPanel.add(commandField, BorderLayout.CENTER);

        // Button panel with Send and Clear
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        sendButton = new JButton(Messages.get("commands.send"));
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendCurrentCommand());
        buttonPanel.add(sendButton);
        JButton clearButton = new JButton(Messages.get("commands.clear"));
        clearButton.addActionListener(e -> responseArea.setText(""));
        buttonPanel.add(clearButton);
        inputPanel.add(buttonPanel, BorderLayout.EAST);
        panel.add(inputPanel, BorderLayout.SOUTH);

        // Response area
        responseArea = new JTextArea();
        responseArea.setEditable(false);
        responseArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);
        DefaultCaret caret = (DefaultCaret) responseArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane scrollPane = new JScrollPane(responseArea);
        scrollPane.setPreferredSize(new Dimension(400, 200));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Updates the command dropdown based on CAT/Hamlib selection.
     */
    private void updateCommandDropdown() {
        quickCommandCombo.removeAllItems();
        String[] commands = catModeSelected ? getCatCommands() : getHamlibCommands();
        for (String cmd : commands) {
            quickCommandCombo.addItem(cmd);
        }
    }

    /**
     * Handles command selection from the dropdown.
     */
    private void handleCommandSelection() {
        int idx = quickCommandCombo.getSelectedIndex();
        if (idx > 0) {
            String selected = (String) quickCommandCombo.getSelectedItem();
            // Extract command from parentheses at end: "Description (CMD)" -> "CMD"
            String cmd = extractCommandFromSelection(selected);

            if (catModeSelected) {
                // CAT command - just the command, sendCurrentCommand() will add "w " and ";"
                commandField.setText(cmd);
                showCatCommandHelp(cmd, selected);
            } else {
                // Hamlib command - use as-is
                commandField.setText(cmd);
                showHamlibCommandHelp(cmd, selected);
            }
            quickCommandCombo.setSelectedIndex(0);
        }
    }

    /**
     * Extracts command from selection string format "Description (CMD)".
     */
    private String extractCommandFromSelection(String selected) {
        int start = selected.lastIndexOf('(');
        int end = selected.lastIndexOf(')');
        if (start >= 0 && end > start) {
            return selected.substring(start + 1, end).trim();
        }
        return selected;
    }

    /**
     * Shows help for Hamlib commands.
     */
    private void showHamlibCommandHelp(String cmd, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(description).append(" ===\n");

        switch (cmd.toLowerCase().split(" ")[0]) {
            case "f":
                sb.append("Returns frequency in Hz\n");
                sb.append("Example: f  →  14074000\n");
                break;
            case "m":
                sb.append("Returns mode and passband width\n");
                sb.append("Example: m  →  USB 0\n");
                break;
            case "v":
                sb.append("Returns current VFO\n");
                sb.append("Example: v  →  VFOA\n");
                break;
            case "t":
                sb.append("Returns PTT status (0=RX, 1=TX)\n");
                sb.append("Example: t  →  0\n");
                break;
            case "s":
                sb.append("Returns split status and TX VFO\n");
                sb.append("Example: s  →  0 VFOB\n");
                break;
            case "l":
                sb.append("Get level value\n");
                sb.append("Levels: RFPOWER, AF, SQL, STRENGTH, SWR, ALC, COMP\n");
                sb.append("Example: l STRENGTH  →  -42\n");
                break;
            case "_":
                sb.append("Returns radio information\n");
                sb.append("Shows model, serial, firmware, etc.\n");
                break;
            case "1":
                sb.append("Dumps radio capabilities\n");
                sb.append("Shows supported modes, frequencies, levels, etc.\n");
                break;
            case "w":
                sb.append("Sends raw CAT command to radio\n");
                sb.append("Example: w FA;  →  FA014074000\n");
                break;
            default:
                if (cmd.startsWith("F ")) {
                    sb.append("Set frequency in Hz\n");
                    sb.append("Example: F 14074000\n");
                } else if (cmd.startsWith("M ")) {
                    sb.append("Set mode (USB, LSB, CW, FM, AM, RTTY, PKTUSB)\n");
                    sb.append("Example: M USB 0\n");
                } else if (cmd.startsWith("V ")) {
                    sb.append("Set VFO (VFOA or VFOB)\n");
                    sb.append("Example: V VFOA\n");
                } else if (cmd.startsWith("T ")) {
                    sb.append("Set PTT (0=RX, 1=TX)\n");
                    sb.append("Example: T 1  (transmit)\n");
                } else if (cmd.startsWith("S ")) {
                    sb.append("Set split mode\n");
                    sb.append("Example: S 1 VFOB  (split on, TX on VFO-B)\n");
                } else if (cmd.startsWith("L ")) {
                    sb.append("Set level (0.0 to 1.0)\n");
                    sb.append("Example: L RFPOWER 0.5  (50% power)\n");
                } else if (cmd.startsWith("U ")) {
                    sb.append("Set function on/off\n");
                    sb.append("Example: U LOCK 1  (lock on)\n");
                } else if (cmd.startsWith("u ")) {
                    sb.append("Get function status\n");
                    sb.append("Example: u LOCK  →  0 or 1\n");
                } else {
                    sb.append("See Hamlib rigctl documentation for details\n");
                }
                break;
        }

        responseArea.setText("");
        appendResponse(sb.toString());
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Status bar
        JLabel statusBar = new JLabel(Messages.get("status.ready"));
        statusBar.setBorder(BorderFactory.createLoweredBevelBorder());
        panel.add(statusBar, BorderLayout.CENTER);

        return panel;
    }

    private void setupEventHandlers() {
        connectButton.addActionListener(e -> toggleConnection());
        daemonButton.addActionListener(e -> toggleDaemon());

        // Window closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }
        });

    }

    private void scanSerialPorts() {
        // Save current selection (strip checkmark if present)
        String currentSelection = (String) portCombo.getSelectedItem();
        if (currentSelection != null && currentSelection.startsWith("\u2713 ")) {
            currentSelection = currentSelection.substring(2);
        }

        portCombo.removeAllItems();

        java.util.TreeSet<String> ports = new java.util.TreeSet<>();
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // Windows: scan COM ports
            scanWindowsPorts(ports);
        } else {
            // Unix (macOS/Linux): scan /dev
            scanUnixPorts(ports);
        }

        for (String port : ports) {
            // Add checkmark to connected port
            if (connectedPort != null && port.equals(connectedPort)) {
                portCombo.addItem("\u2713 " + port);
            } else {
                portCombo.addItem(port);
            }
        }

        // If no ports found, add platform-appropriate defaults
        if (portCombo.getItemCount() == 0) {
            if (os.contains("win")) {
                portCombo.addItem("COM1");
                portCombo.addItem("COM3");
            } else {
                portCombo.addItem("/dev/cu.SLAB_USBtoUART");
                portCombo.addItem("/dev/ttyUSB0");
            }
        }

        // Restore previous selection if it still exists
        if (currentSelection != null && !currentSelection.isEmpty()) {
            String checkmarkedSelection = "\u2713 " + currentSelection;
            for (int i = 0; i < portCombo.getItemCount(); i++) {
                String item = portCombo.getItemAt(i);
                if (currentSelection.equals(item) || checkmarkedSelection.equals(item)) {
                    portCombo.setSelectedIndex(i);
                    return;
                }
            }
            // If not found but looks valid, add it back
            if (currentSelection.startsWith("/dev/") || currentSelection.toUpperCase().startsWith("COM")) {
                portCombo.addItem(currentSelection);
                portCombo.setSelectedItem(currentSelection);
            }
        }
    }

    private void scanWindowsPorts(java.util.TreeSet<String> ports) {
        // Method 1: Try to use Windows registry via PowerShell
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-Command",
                "[System.IO.Ports.SerialPort]::GetPortNames()"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (var reader = new java.io.BufferedReader(
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
                p.destroyForcibly();  // Ensure process is cleaned up
            }
        } catch (Exception e) {
            // PowerShell method failed, fall back to probing
        }

        // Method 2: Probe common COM ports if PowerShell didn't find any
        if (ports.isEmpty()) {
            for (int i = 1; i <= 20; i++) {
                String port = "COM" + i;
                Process p = null;
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                        "cmd", "/c", "mode " + port + " 2>nul"
                    );
                    p = pb.start();
                    // Consume streams to prevent blocking
                    try (var is = p.getInputStream(); var es = p.getErrorStream()) {
                        is.readAllBytes();
                        es.readAllBytes();
                    }
                    int exitCode = p.waitFor();
                    if (exitCode == 0) {
                        ports.add(port);
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    if (p != null) {
                        p.destroyForcibly();
                    }
                }
            }
        }

        // Method 3: If still empty, add common defaults
        if (ports.isEmpty()) {
            for (int i = 1; i <= 10; i++) {
                ports.add("COM" + i);
            }
        }
    }

    private void scanUnixPorts(java.util.TreeSet<String> ports) {
        java.io.File devDir = new java.io.File("/dev");
        if (devDir.exists() && devDir.isDirectory()) {
            String[] files = devDir.list();
            if (files != null) {
                for (String file : files) {
                    // macOS: cu.* ports (SLAB_USBtoUART, usbserial, etc.)
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

    private void toggleConnection() {
        if (!connected) {
            connect();
        } else {
            disconnect();
        }
    }

    /**
     * Toggle remote server connection.
     */
    private void toggleRemoteConnection() {
        if (!remoteConnected) {
            connectRemote();
        } else {
            disconnectRemote();
        }
    }

    /**
     * Connect to remote ftx1-hamlib server.
     */
    private void connectRemote() {
        String host = remoteHostField.getText().trim();
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                Messages.get("remote.error.host"),
                Messages.get("connection.error.title"),
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        int catPort = (Integer) remoteCatPortSpinner.getValue();
        int audioPort = (Integer) remoteAudioPortSpinner.getValue();

        // Disable controls during connection
        setRemoteControlsEnabled(false);
        remoteConnectionStatus.setText(Messages.get("remote.connecting"));
        remoteConnectionStatus.setForeground(Color.ORANGE);

        // Connect in background thread
        new SwingWorker<Void, Void>() {
            private Exception error;

            @Override
            protected Void doInBackground() {
                try {
                    // Connect to remote CAT server
                    remoteRigClient = new RigctlClient(host, catPort);
                    remoteRigClient.connect();

                    // Connect audio via AudioControlPanel
                    if (audioControlPanel != null) {
                        audioControlPanel.connectRemoteAudio(host, audioPort);
                    }
                } catch (Exception e) {
                    error = e;
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(HamlibGUI.this,
                        Messages.get("remote.error.connect", error.getMessage()),
                        Messages.get("connection.error.title"),
                        JOptionPane.ERROR_MESSAGE);
                    setRemoteControlsEnabled(true);
                    remoteConnectionStatus.setText(Messages.get("connection.status.disconnected"));
                    remoteConnectionStatus.setForeground(Color.RED);
                    if (remoteRigClient != null) {
                        remoteRigClient.disconnect();
                        remoteRigClient = null;
                    }
                } else {
                    remoteConnected = true;
                    connected = true;  // Set connected flag for status updates
                    // Note: In remote mode, we don't use RigctlCommandHandler - we use RigctlClient directly
                    updateRemoteUIState();
                    appendResponse("Connected to remote server: " + host);

                    // Register AI listener for push-based updates
                    remoteRigClient.addAIListener(aiData -> {
                        SwingUtilities.invokeLater(() -> {
                            if (remoteConnected) {
                                logComm("AI", aiData);
                                updateStatusFromAutoInfo(aiData);
                            }
                        });
                    });

                    // Do initial state query (AI only sends changes)
                    queryInitialRemoteState();
                }
            }
        }.execute();
    }

    /**
     * Disconnect from remote server.
     */
    private void disconnectRemote() {
        if (audioControlPanel != null) {
            audioControlPanel.disconnectRemoteAudio();
        }

        if (remoteRigClient != null) {
            remoteRigClient.disconnect();
            remoteRigClient = null;
        }

        remoteConnected = false;
        connected = false;
        commandHandler = null;
        updateRemoteUIState();
        appendResponse("Disconnected from remote server");
    }

    private void setRemoteControlsEnabled(boolean enabled) {
        remoteHostField.setEnabled(enabled);
        remoteCatPortSpinner.setEnabled(enabled);
        remoteAudioPortSpinner.setEnabled(enabled);
        localModeButton.setEnabled(enabled);
        remoteModeButton.setEnabled(enabled);
    }

    private void updateRemoteUIState() {
        setRemoteControlsEnabled(!remoteConnected);
        remoteConnectButton.setText(remoteConnected ?
            Messages.get("connection.disconnect") :
            Messages.get("connection.connect"));

        if (remoteConnected) {
            remoteConnectionStatus.setText(Messages.get("remote.connected", remoteHostField.getText()));
            remoteConnectionStatus.setForeground(new Color(0, 128, 0));
        } else {
            remoteConnectionStatus.setText(Messages.get("connection.status.disconnected"));
            remoteConnectionStatus.setForeground(Color.RED);
            resetStatusLabels();
        }
    }

    /**
     * Query initial state from remote server.
     * AI updates only push changes, so we need to get the current state once at connect time.
     */
    private void queryInitialRemoteState() {
        if (!remoteConnected || remoteRigClient == null) return;

        new SwingWorker<Void, Void>() {
            private long freqA, freqB;
            private String modeA, modeB;
            private String vfo;
            private boolean ptt;

            @Override
            protected Void doInBackground() {
                try {
                    freqA = remoteRigClient.getFrequency();
                    modeA = remoteRigClient.getMode();
                    vfo = remoteRigClient.getVFO();
                    ptt = remoteRigClient.getPTT();

                    // Try to get VFO-B info
                    try {
                        String currentVfo = vfo;
                        if ("VFOA".equals(currentVfo)) {
                            remoteRigClient.setVFO("VFOB");
                            freqB = remoteRigClient.getFrequency();
                            modeB = remoteRigClient.getMode();
                            remoteRigClient.setVFO("VFOA");
                        } else {
                            freqB = freqA;
                            modeB = modeA;
                        }
                    } catch (Exception e) {
                        freqB = 0;
                        modeB = "--";
                    }
                } catch (Exception e) {
                    // Connection lost
                    SwingUtilities.invokeLater(() -> {
                        if (remoteConnected) {
                            appendResponse("Connection lost: " + e.getMessage());
                            disconnectRemote();
                        }
                    });
                }
                return null;
            }

            @Override
            protected void done() {
                if (remoteConnected) {
                    freqLabelA.setText(formatFrequency(freqA));
                    modeLabelA.setText(modeA != null ? modeA : "--");
                    freqLabelB.setText(formatFrequency(freqB));
                    modeLabelB.setText(modeB != null ? modeB : "--");
                    activeVfoLabel.setText(vfo != null ? vfo : "--");
                    pttLabel.setText(ptt ? Messages.get("status.ptt.tx") : Messages.get("status.ptt.rx"));
                    pttLabel.setForeground(ptt ? Color.RED : new Color(0, 128, 0));
                }
            }
        }.execute();
    }

    /**
     * Enables auto-information mode on the radio.
     * Called automatically when connecting to the radio.
     */
    private void enableAutoInfo() {
        if (!connected || rig == null) return;

        try {
            // Register listener for auto-info data
            rig.getConnection().addDataListener(autoInfoListener);
            // Enable listener mode (background reader thread)
            rig.getConnection().setListenerMode(true);
            // Enable AI (auto-information) on radio
            rig.sendRawCommand("AI1");
            appendResponse("Auto-info enabled (AI1)");
        } catch (Exception e) {
            appendResponse("Error enabling auto-info: " + e.getMessage());
        }
    }

    /**
     * Disables auto-information mode on the radio.
     * Called automatically when disconnecting from the radio.
     */
    private void disableAutoInfo() {
        if (rig == null) return;

        try {
            // Disable AI on radio
            rig.sendRawCommand("AI0");
        } catch (Exception e) {
            // Ignore errors during disconnect
        }

        // Disable listener mode and remove listener (with null checks)
        var connection = rig.getConnection();
        if (connection != null) {
            connection.setListenerMode(false);
            connection.removeDataListener(autoInfoListener);
        }
    }

    // Listener for auto-info data from the radio
    private final CatDataListener autoInfoListener = data -> {
        // Called from background thread

        // Broadcast to connected remote clients (if server is running)
        if (server != null) {
            server.broadcastAI(data);
        }

        // Update UI on EDT
        SwingUtilities.invokeLater(() -> {
            // Log to Comm Monitor tab with "AI" direction marker
            logComm("AI", data);
            // Update radio status panel if applicable
            updateStatusFromAutoInfo(data);
        });
    };

    // Listener for Hamlib client commands
    private final RigctlCommandListener hamlibCommandListener = new RigctlCommandListener() {
        @Override
        public void onCommandReceived(String clientId, String command) {
            SwingUtilities.invokeLater(() -> {
                logComm("HL<", command);
            });
        }

        @Override
        public void onResponseSent(String clientId, String response) {
            SwingUtilities.invokeLater(() -> {
                // Format multi-line responses on single line
                String formatted = response.trim().replace("\n", " | ");
                logComm("HL>", formatted);
            });
        }

        @Override
        public void onClientConnected(String clientId, String address) {
            SwingUtilities.invokeLater(() -> {
                appendResponse("Hamlib client connected: " + formatAddress(address));
            });
        }

        @Override
        public void onClientDisconnected(String clientId) {
            SwingUtilities.invokeLater(() -> {
                appendResponse("Hamlib client disconnected");
            });
        }
    };

    /**
     * Updates the radio status panel from auto-info data.
     */
    private void updateStatusFromAutoInfo(String data) {
        if (data == null || data.length() < 2) return;

        String cmd = data.substring(0, 2).toUpperCase();
        String value = data.length() > 2 ? data.substring(2) : "";

        switch (cmd) {
            case "FA": // VFO-A frequency
                try {
                    long freq = Long.parseLong(value);
                    freqLabelA.setText(formatFrequency(freq) + " MHz");
                } catch (NumberFormatException e) { }
                break;

            case "FB": // VFO-B frequency
                try {
                    long freq = Long.parseLong(value);
                    freqLabelB.setText(formatFrequency(freq) + " MHz");
                } catch (NumberFormatException e) { }
                break;

            case "MD": // Mode - format MD0x (VFO-A) or MD1x (VFO-B)
                if (value.length() >= 2) {
                    char vfoChar = value.charAt(0);
                    char modeChar = value.charAt(1);
                    String modeName = getModeNameFromChar(modeChar);
                    if (modeName != null) {
                        if (vfoChar == '0') {
                            modeLabelA.setText(modeName);
                        } else if (vfoChar == '1') {
                            modeLabelB.setText(modeName);
                        }
                    }
                } else if (value.length() == 1) {
                    // Single char mode - apply to active VFO
                    char modeChar = value.charAt(0);
                    String modeName = getModeNameFromChar(modeChar);
                    if (modeName != null) {
                        if (activeVfoIsB) {
                            modeLabelB.setText(modeName);
                        } else {
                            modeLabelA.setText(modeName);
                        }
                    }
                }
                break;

            case "VS": // VFO select
                if (value.equals("0")) {
                    activeVfoIsB = false;
                    activeVfoLabel.setText("VFO-A");
                } else if (value.equals("1")) {
                    activeVfoIsB = true;
                    activeVfoLabel.setText("VFO-B");
                }
                break;

            case "TX": // PTT status
                if (value.equals("0")) {
                    pttLabel.setText("RX");
                    pttLabel.setForeground(new Color(0, 128, 0));
                } else {
                    pttLabel.setText("TX");
                    pttLabel.setForeground(Color.RED);
                }
                break;

            case "PC": // Power control
                // Format: PC1xxx (Field Head) or PC2xxx (SPA-1)
                // Field Head can have decimal: PC10.5 = 0.5W, PC110 = 10W
                // SPA-1 uses integer: PC2050 = 50W, PC2100 = 100W
                try {
                    if (value.length() >= 2) {
                        double powerWatts = Double.parseDouble(value.substring(1)); // Skip first digit (head type)
                        powerLabel.setText(formatPower(powerWatts));
                    }
                } catch (NumberFormatException e) { }
                break;

            case "SM": // S-meter
                if (value.length() >= 1) {
                    try {
                        // SM0xxx format - first digit is VFO, next 3 are value
                        int smeterVal = Integer.parseInt(value.substring(1));
                        // Convert 0-255 to dB (approximately)
                        int db = (smeterVal * 100 / 255) - 54;
                        String smeterStr = formatSmeter(db);
                        smeterLabel.setText(smeterStr + " (" + db + " dB)");
                    } catch (NumberFormatException e) { }
                }
                break;

            case "RM": // Read Meter (SWR, ALC, COMP, etc.)
                // Format: RMxyyy where x=meter type (1=ALC, 2=SWR, 3=COMP, 4=ID, 5=VDD), yyy=value
                if (value.length() >= 4) {
                    try {
                        char meterType = value.charAt(0);
                        int meterVal = Integer.parseInt(value.substring(1, 4));
                        int percent = (meterVal * 100) / 255;
                        switch (meterType) {
                            case '1': // ALC
                                alcLabel.setText(percent + "%");
                                break;
                            case '2': // SWR
                                // Convert 0-255 to approximate SWR (1.0 to 3.0+)
                                double swr = 1.0 + (meterVal * 2.0 / 255.0);
                                swrLabel.setText(String.format("%.1f:1", swr));
                                break;
                            case '3': // COMP (compression)
                                compLabel.setText(percent + "%");
                                break;
                        }
                    } catch (NumberFormatException e) { }
                }
                break;

            case "FT": // TX VFO select (which VFO is used for transmit)
                if (value.equals("0")) {
                    activeVfoIsB = false;
                    activeVfoLabel.setText("VFO-A");
                } else if (value.equals("1")) {
                    activeVfoIsB = true;
                    activeVfoLabel.setText("VFO-B");
                }
                break;

            case "FD": // VFO frequency data (FDx where x=VFO, followed by freq)
                // Format: FD0NNNNNNNNNNN (VFO-A) or FD1NNNNNNNNNNN (VFO-B) - 11 digit freq in Hz
                // Example: FD100430000000 = VFO-B at 430.000.000 Hz
                if (value.length() >= 10) {
                    char vfo = value.charAt(0);
                    boolean isVfoB = (vfo == '1');
                    activeVfoIsB = isVfoB;
                    String freqStr = value.substring(1); // All remaining digits are frequency
                    try {
                        long freq = Long.parseLong(freqStr);
                        if (isVfoB) {
                            freqLabelB.setText(formatFrequency(freq) + " MHz");
                        } else {
                            freqLabelA.setText(formatFrequency(freq) + " MHz");
                        }
                        activeVfoLabel.setText(isVfoB ? "VFO-B" : "VFO-A");
                    } catch (NumberFormatException e) { }
                }
                break;
        }
    }

    /**
     * Gets mode name from CAT mode character.
     */
    private String getModeNameFromChar(char modeChar) {
        switch (modeChar) {
            case '1': return "LSB";
            case '2': return "USB";
            case '3': return "CW";
            case '4': return "FM";
            case '5': return "AM";
            case '6': return "RTTY-LSB";
            case '7': return "CW-R";
            case '8': return "DATA-LSB";
            case '9': return "RTTY-USB";
            case 'A': return "DATA-FM";
            case 'B': return "FM-N";
            case 'C': return "DATA-USB";
            case 'D': return "AM-N";
            default: return null;
        }
    }

    private void connect() {
        String port = (String) portCombo.getSelectedItem();
        int baud = (Integer) baudCombo.getSelectedItem();

        if (port == null || port.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.get("connection.error.noport"),
                    Messages.get("connection.error.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Strip checkmark prefix if present
        if (port.startsWith("\u2713 ")) {
            port = port.substring(2);
        }
        final String actualPort = port;

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        connectButton.setEnabled(false);

        SwingWorker<FTX1, Void> worker = new SwingWorker<>() {
            @Override
            protected FTX1 doInBackground() throws Exception {
                return FTX1.connect(actualPort, baud);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                connectButton.setEnabled(true);

                try {
                    rig = get();
                    commandHandler = new RigctlCommandHandler(rig, true);
                    connected = true;
                    connectedPort = actualPort;

                    // Update UI
                    connectButton.setText(Messages.get("connection.disconnect"));
                    connectionStatus.setText(Messages.get("connection.status.connected"));
                    connectionStatus.setForeground(new Color(0, 128, 0));
                    daemonButton.setEnabled(true);
                    commandField.setEnabled(true);
                    sendButton.setEnabled(true);
                    portCombo.setEnabled(false);
                    baudCombo.setEnabled(false);
                    refreshPortsButton.setEnabled(false);

                    // Refresh port list to show checkmark
                    scanSerialPorts();

                    // Populate radio status on connect
                    populateRadioStatus();

                    // Enable auto-information mode
                    enableAutoInfo();

                    appendResponse(Messages.get("msg.connected", actualPort));

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(HamlibGUI.this,
                            Messages.get("connection.error.failed", ex.getMessage()),
                            Messages.get("connection.error.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void disconnect() {
        // Disable auto-information
        disableAutoInfo();

        // Stop daemon if running
        if (daemonRunning) {
            stopDaemon();
        }

        // Close rig connection
        if (rig != null) {
            rig.close();
            rig = null;
        }

        connected = false;
        connectedPort = null;
        commandHandler = null;

        // Update UI
        connectButton.setText(Messages.get("connection.connect"));
        connectionStatus.setText(Messages.get("connection.status.disconnected"));
        connectionStatus.setForeground(Color.RED);
        daemonButton.setEnabled(false);
        commandField.setEnabled(false);
        sendButton.setEnabled(false);
        portCombo.setEnabled(true);
        baudCombo.setEnabled(true);
        refreshPortsButton.setEnabled(true);

        // Refresh port list to remove checkmark
        scanSerialPorts();

        // Clear status
        resetStatusLabels();

        appendResponse(Messages.get("msg.disconnected"));
        appendResponse("");
    }

    /**
     * Resets all radio status labels to default values.
     */
    private void resetStatusLabels() {
        headTypeLabel.setText("<html>--<br>&nbsp;</html>");
        freqLabelA.setText("--");
        modeLabelA.setText("--");
        freqLabelB.setText("--");
        modeLabelB.setText("--");
        activeVfoLabel.setText("--");
        pttLabel.setText("RX");
        pttLabel.setForeground(Color.GRAY);
        powerLabel.setText("--");
        smeterLabel.setText("--");
        swrLabel.setText("--");
        alcLabel.setText("--");
        compLabel.setText("--");
        activeVfoIsB = false;
    }

    private void toggleDaemon() {
        if (!daemonRunning) {
            startDaemon();
        } else {
            stopDaemon();
        }
    }

    private void startDaemon() {
        int port = (Integer) tcpPortSpinner.getValue();

        try {
            server = new RigctldServer(rig, port, false);
            server.addCommandListener(hamlibCommandListener);
            server.start();
            daemonRunning = true;

            daemonButton.setText(Messages.get("hamlib.stop"));
            daemonStatus.setText(Messages.get("hamlib.status.running", port));
            daemonStatus.setForeground(new Color(0, 128, 0));
            tcpPortSpinner.setEnabled(false);

            appendResponse(Messages.get("msg.hamlib.started", port));
            appendResponse(Messages.get("msg.hamlib.connect", port));
            appendResponse("");

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    Messages.get("hamlib.error.start", e.getMessage()),
                    Messages.get("hamlib.error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopDaemon() {
        if (server != null) {
            server.stop();
            server = null;
        }

        daemonRunning = false;
        daemonButton.setText(Messages.get("hamlib.start"));
        daemonStatus.setText(Messages.get("hamlib.status.stopped"));
        daemonStatus.setForeground(Color.GRAY);
        tcpPortSpinner.setEnabled(true);

        appendResponse(Messages.get("msg.hamlib.stopped"));
        appendResponse("");
    }

    private void sendCurrentCommand() {
        String command = commandField.getText().trim();
        if (!command.isEmpty()) {
            // Auto-detect CAT commands and prepend "w " if needed
            // CAT commands are 2 uppercase letters, optionally followed by params and semicolon
            // e.g., "FA;", "FA014074000;", "MD0;", "FA" (without semicolon)
            if (!command.startsWith("w ") && isCatCommand(command)) {
                // Ensure it ends with semicolon
                if (!command.endsWith(";")) {
                    command = command + ";";
                }
                command = "w " + command;
            }
            sendCommand(command);
            commandField.selectAll();
        }
    }

    /**
     * Checks if a command looks like a raw CAT command (2 uppercase letters + optional params).
     */
    private boolean isCatCommand(String cmd) {
        if (cmd == null || cmd.length() < 2) return false;
        // Remove trailing semicolon for checking
        String check = cmd.endsWith(";") ? cmd.substring(0, cmd.length() - 1) : cmd;
        if (check.length() < 2) return false;
        // First two chars must be uppercase letters
        char c1 = check.charAt(0);
        char c2 = check.charAt(1);
        if (!Character.isUpperCase(c1) || !Character.isUpperCase(c2)) return false;
        // Known CAT commands (2-letter uppercase)
        String[] catCommands = {"AB", "AC", "AG", "AI", "AM", "AO", "BA", "BC", "BD", "BI", "BM", "BP", "BS", "BU",
            "CF", "CH", "CN", "CO", "CS", "CT", "DA", "DC", "DN", "DT", "EO", "EX", "FA", "FB", "FN", "FR", "FT",
            "GP", "GT", "ID", "IF", "IS", "KM", "KP", "KR", "KS", "KY", "LK", "LM", "MA", "MB", "MC", "MD", "MG",
            "ML", "MR", "MS", "MT", "MW", "MX", "MZ", "NA", "NL", "OI", "OS", "PA", "PB", "PC", "PL", "PR", "PS",
            "QI", "QR", "RA", "RG", "RI", "RL", "RM", "SC", "SD", "SF", "SH", "SM", "SQ", "SS", "ST", "SV", "TS",
            "TX", "UP", "VD", "VE", "VG", "VM", "VS", "VX", "ZI"};
        String prefix = check.substring(0, 2);
        for (String cat : catCommands) {
            if (cat.equals(prefix)) return true;
        }
        return false;
    }

    private void sendCommand(String command) {
        if (!connected || commandHandler == null) {
            appendResponse("Error: Not connected");
            return;
        }

        String response = commandHandler.handleCommand(command);
        String catResponse = response.trim();

        // If this was a raw CAT command (w command), show only the interpretation
        if (command.startsWith("w ")) {
            // Check for error responses
            if (catResponse.startsWith("RPRT -")) {
                appendResponse("Error: Command failed");
            } else if (catResponse.equals("RPRT 0") || catResponse.isEmpty()) {
                // RPRT 0 means success with no data returned (set-only commands)
                appendResponse("OK");
            } else {
                // We got data back, interpret it
                String interpretation = interpretCatResponse(catResponse);
                if (interpretation != null && !interpretation.isEmpty()) {
                    appendResponse(interpretation);
                } else {
                    // Fallback to raw response if no interpretation
                    appendResponse(catResponse);
                }
            }
        } else {
            // Hamlib command - show raw response
            appendResponse(catResponse);
        }
    }

    private void appendResponse(String text) {
        responseArea.append(text + "\n");
    }

    /**
     * Interprets a CAT response and returns a human-readable explanation.
     */
    private String interpretCatResponse(String response) {
        if (response == null || response.isEmpty() || response.startsWith("RPRT")) {
            return null;
        }

        // Remove trailing semicolon if present
        response = response.replace(";", "").trim();

        if (response.length() < 2) {
            return null;
        }

        String cmd = response.substring(0, 2).toUpperCase();
        String data = response.length() > 2 ? response.substring(2) : "";

        switch (cmd) {
            case "FA":
            case "FB":
                return interpretFrequency(cmd, data);
            case "MD":
                return interpretMode(data);
            case "VS":
                return "VFO " + (data.equals("0") ? "A" : "B") + " selected";
            case "ST":
                return "Split is " + (data.equals("0") ? "OFF" : "ON");
            case "TX":
                if (data.equals("0")) return "Radio is in RECEIVE mode";
                if (data.equals("1")) return "Radio is TRANSMITTING (MIC)";
                if (data.equals("2")) return "Radio is TRANSMITTING (DATA)";
                return "TX state: " + data;
            case "LK":
                return "Dial lock is " + (data.equals("0") ? "OFF" : "ON");
            case "AG":
                return interpretGainLevel("AF Gain", data);
            case "RG":
                return interpretGainLevel("RF Gain", data);
            case "SQ":
                return interpretGainLevel("Squelch", data);
            case "PC":
                return interpretPower(data);
            case "SM":
                return interpretSmeter(data);
            case "ID":
                if (data.equals("0763")) return "Radio ID: FTX-1 Field Head";
                if (data.equals("0840")) return "Radio ID: FTX-1 Optima/SPA-1";
                return "Radio ID: " + data;
            case "VE":
                return "Firmware version: " + data;
            case "AI":
                return "Auto Information is " + (data.equals("0") ? "OFF" : "ON");
            case "BI":
                if (data.equals("0")) return "Break-in is OFF";
                if (data.equals("1")) return "Semi break-in enabled";
                if (data.equals("2")) return "Full break-in enabled";
                return "Break-in mode: " + data;
            case "GT":
                return interpretAgc(data);
            case "PA":
                return interpretPreamp(data);
            case "RA":
                return interpretAttenuator(data);
            case "NA":
                return interpretNarrow(data);
            case "BC":
                return interpretAutoNotch(data);
            case "PR":
                return "Speech processor is " + (data.equals("0") ? "OFF" : "ON");
            case "VX":
                return "VOX is " + (data.equals("0") ? "OFF" : "ON");
            case "MX":
                return "MOX is " + (data.equals("0") ? "OFF" : "ON");
            case "PS":
                return "Power is " + (data.equals("0") ? "OFF" : "ON");
            case "KR":
                return "Keyer is " + (data.equals("0") ? "OFF" : "ON");
            case "KS":
                return "Keyer speed: " + Integer.parseInt(data) + " WPM";
            case "KP":
                return "CW pitch: " + data + " Hz";
            case "MG":
                return "Mic gain: " + Integer.parseInt(data) + "%";
            case "PL":
                return "Processor level: " + Integer.parseInt(data) + "%";
            case "VG":
                return "VOX gain: " + Integer.parseInt(data) + "%";
            case "VD":
                return "VOX delay: " + Integer.parseInt(data) + " ms";
            case "SD":
                return "Break-in delay: " + Integer.parseInt(data) + " ms";
            case "RM":
                return interpretMeter(data);
            case "AC":
                return interpretTuner(data);
            case "FR":
                if (data.equals("0")) return "Receive on VFO-A";
                if (data.equals("1")) return "Receive on VFO-B";
                if (data.equals("2")) return "Dual receive enabled";
                return "RX mode: " + data;
            case "FT":
                return "Transmit on VFO-" + (data.equals("0") ? "A" : "B");
            case "MC":
                return "Memory channel: " + Integer.parseInt(data);
            case "OS":
                return interpretRepeaterShift(data);
            case "TS":
                return "TX Watch is " + (data.equals("0") ? "OFF" : "ON");
            case "FN":
                return "Filter " + data + " selected";
            case "CS":
                return "CW Spot is " + (data.equals("0") ? "OFF" : "ON");
            case "IF":
                return interpretInfoString(data);
            case "BP":
                return interpretManualNotch(data);
            case "CF":
                return interpretClarifier(data);
            case "CN":
                return interpretCtcssTone(data);
            case "CO":
                return interpretContour(data);
            case "CT":
                return interpretCtcssMode(data);
            case "DA":
                return interpretLcdSettings(data);
            case "DC":
                return interpretDcsCode(data);
            case "DT":
                return interpretDateTime(data);
            case "IS":
                return interpretIfShift(data);
            case "KY":
                return interpretKeyerStatus(data);
            case "ML":
                return interpretMonitorLevel(data);
            case "MR":
                return "Memory channel data received";
            case "NL":
                return interpretNoiseBlankerLevel(data);
            case "OI":
                return "Opposite band info received";
            case "RL":
                return interpretNoiseReductionLevel(data);
            case "SC":
                return interpretScan(data);
            case "SH":
                return interpretWidth(data);
            case "VM":
                return interpretVfoMemoryMode(data);
            default:
                return null;
        }
    }

    private String interpretFrequency(String vfo, String data) {
        try {
            long freqHz = Long.parseLong(data);
            String vfoName = vfo.equals("FA") ? "VFO-A" : "VFO-B";
            return String.format("%s frequency: %s MHz", vfoName, formatFrequency(freqHz));
        } catch (NumberFormatException e) {
            return "Frequency: " + data;
        }
    }

    /**
     * Formats frequency in Hz to XXX.XXX.XXX MHz format with no leading zeros.
     * Examples: 14074000 -> "14.074.000", 146520000 -> "146.520.000"
     */
    private String formatFrequency(long freqHz) {
        // Convert to string padded to 9 digits
        String padded = String.format("%09d", freqHz);
        // Split into groups: MHz (first 3), kHz (next 3), Hz (last 3)
        String mhz = padded.substring(0, 3);
        String khz = padded.substring(3, 6);
        String hz = padded.substring(6, 9);
        // Remove leading zeros from MHz portion
        mhz = mhz.replaceFirst("^0+", "");
        if (mhz.isEmpty()) mhz = "0";
        return mhz + "." + khz + "." + hz;
    }

    private String interpretMode(String data) {
        if (data.length() < 1) return "Mode: unknown";
        String vfo = data.length() > 1 ? (data.charAt(0) == '0' ? "Main" : "Sub") + " " : "";
        char modeChar = data.charAt(data.length() - 1);
        String mode;
        switch (modeChar) {
            case '1': mode = "LSB"; break;
            case '2': mode = "USB"; break;
            case '3': mode = "CW"; break;
            case '4': mode = "FM"; break;
            case '5': mode = "AM"; break;
            case '6': mode = "RTTY"; break;
            case '7': mode = "CW-R"; break;
            case '8': mode = "DATA (USB)"; break;
            case '9': mode = "RTTY-R"; break;
            case 'A': mode = "FM-N"; break;
            case 'B': mode = "DATA-FM"; break;
            case 'C': mode = "DATA-LSB"; break;
            default: mode = "Unknown (" + modeChar + ")";
        }
        return vfo + "Mode: " + mode;
    }

    private String interpretGainLevel(String name, String data) {
        try {
            // Handle VFO prefix (e.g., "0128" -> VFO 0, value 128)
            int value;
            String vfo = "";
            if (data.length() > 3) {
                vfo = data.charAt(0) == '0' ? "Main " : "Sub ";
                value = Integer.parseInt(data.substring(1));
            } else {
                value = Integer.parseInt(data);
            }
            int percent = (value * 100) / 255;
            return vfo + name + ": " + value + " (" + percent + "%)";
        } catch (NumberFormatException e) {
            return name + ": " + data;
        }
    }

    private String interpretPower(String data) {
        try {
            if (data.contains(".")) {
                return "Power output: " + data + " watts (Field head)";
            }
            int watts = Integer.parseInt(data);
            return "Power output: " + watts + " watts";
        } catch (NumberFormatException e) {
            return "Power: " + data;
        }
    }

    private String interpretSmeter(String data) {
        try {
            int vfo = 0;
            int value;
            if (data.length() > 3) {
                vfo = data.charAt(0) - '0';
                value = Integer.parseInt(data.substring(1));
            } else {
                value = Integer.parseInt(data);
            }
            // Convert to S-units (roughly: S0=-54dB, S9=0dB, each S-unit is 6dB)
            int dbm = (value * 54 / 255) - 54;
            String sUnits;
            if (dbm < -48) sUnits = "S1";
            else if (dbm < -42) sUnits = "S2";
            else if (dbm < -36) sUnits = "S3";
            else if (dbm < -30) sUnits = "S4";
            else if (dbm < -24) sUnits = "S5";
            else if (dbm < -18) sUnits = "S6";
            else if (dbm < -12) sUnits = "S7";
            else if (dbm < -6) sUnits = "S8";
            else if (dbm < 0) sUnits = "S9";
            else sUnits = "S9+" + dbm + "dB";

            String vfoStr = vfo == 0 ? "Main" : "Sub";
            return vfoStr + " S-meter: " + sUnits + " (raw: " + value + ")";
        } catch (NumberFormatException e) {
            return "S-meter: " + data;
        }
    }

    private String interpretAgc(String data) {
        if (data.length() < 2) return "AGC: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        char agcChar = data.charAt(1);
        String agc;
        switch (agcChar) {
            case '0': agc = "Auto"; break;
            case '1': agc = "Fast"; break;
            case '2': agc = "Mid"; break;
            case '3': agc = "Slow"; break;
            case '4': agc = "Off"; break;
            default: agc = "Unknown";
        }
        return vfo + " AGC: " + agc;
    }

    private String interpretPreamp(String data) {
        if (data.length() < 2) return "Preamp: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        char preampChar = data.charAt(1);
        String preamp;
        switch (preampChar) {
            case '0': preamp = "IPO (off)"; break;
            case '1': preamp = "AMP1"; break;
            case '2': preamp = "AMP2"; break;
            default: preamp = "Unknown";
        }
        return vfo + " Preamp: " + preamp;
    }

    private String interpretAttenuator(String data) {
        if (data.length() < 2) return "Attenuator: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        return vfo + " Attenuator is " + (data.charAt(1) == '0' ? "OFF" : "ON (-12dB)");
    }

    private String interpretNarrow(String data) {
        if (data.length() < 2) return "Narrow: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        return vfo + " filter is " + (data.charAt(1) == '0' ? "WIDE" : "NARROW");
    }

    private String interpretAutoNotch(String data) {
        if (data.length() < 2) return "Auto Notch: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        return vfo + " Auto Notch (DNF) is " + (data.charAt(1) == '0' ? "OFF" : "ON");
    }

    private String interpretMeter(String data) {
        if (data.length() < 4) return "Meter: " + data;
        char meterType = data.charAt(0);
        int value = Integer.parseInt(data.substring(1));
        String meterName;
        switch (meterType) {
            case '1': meterName = "ALC"; break;
            case '2': meterName = "SWR"; break;
            case '3': meterName = "COMP"; break;
            case '4': meterName = "ID (current)"; break;
            case '5': meterName = "VDD (voltage)"; break;
            default: meterName = "Unknown";
        }
        return meterName + " meter: " + value;
    }

    private String interpretTuner(String data) {
        if (data.length() < 2) return "Tuner: " + data;
        boolean on = data.charAt(0) != '0';
        boolean tuned = data.length() > 1 && data.charAt(1) == '1';
        if (!on) return "Antenna tuner is OFF";
        return "Antenna tuner is ON" + (tuned ? " (tuned)" : " (tuning...)");
    }

    private String interpretRepeaterShift(String data) {
        if (data.length() < 2) return "Repeater shift: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        char shiftChar = data.charAt(1);
        String shift;
        switch (shiftChar) {
            case '0': shift = "Simplex"; break;
            case '1': shift = "+"; break;
            case '2': shift = "-"; break;
            default: shift = "Unknown";
        }
        return vfo + " repeater shift: " + shift;
    }

    private String interpretInfoString(String data) {
        // IF response is complex, just extract frequency
        if (data.length() >= 11) {
            try {
                long freq = Long.parseLong(data.substring(0, 11));
                return String.format("Frequency: %s MHz", formatFrequency(freq));
            } catch (NumberFormatException e) {
                return "Info received";
            }
        }
        return "Info received";
    }

    private String interpretManualNotch(String data) {
        // BP format: P1(VFO) + P2(on/off) + P3P3P3P3(freq)
        if (data.length() < 2) return "Manual Notch: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        boolean on = data.charAt(1) != '0';
        if (data.length() >= 6) {
            try {
                int freq = Integer.parseInt(data.substring(2));
                return vfo + " Manual Notch is " + (on ? "ON at " + freq + " Hz" : "OFF");
            } catch (NumberFormatException e) {
                return vfo + " Manual Notch is " + (on ? "ON" : "OFF");
            }
        }
        return vfo + " Manual Notch is " + (on ? "ON" : "OFF");
    }

    private String interpretClarifier(String data) {
        // CF format: P1(VFO) + P2(on/off) + P3P3P3P3(offset with sign)
        if (data.length() < 2) return "Clarifier: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        boolean on = data.charAt(1) != '0';
        if (data.length() >= 6) {
            try {
                int offset = Integer.parseInt(data.substring(2));
                String sign = offset >= 0 ? "+" : "";
                return vfo + " RIT/XIT is " + (on ? "ON (" + sign + offset + " Hz)" : "OFF");
            } catch (NumberFormatException e) {
                return vfo + " RIT/XIT is " + (on ? "ON" : "OFF");
            }
        }
        return vfo + " RIT/XIT is " + (on ? "ON" : "OFF");
    }

    private String interpretCtcssTone(String data) {
        // CTCSS tone frequencies (index 1-50)
        String[] tones = {
            "", "67.0", "69.3", "71.9", "74.4", "77.0", "79.7", "82.5", "85.4", "88.5",
            "91.5", "94.8", "97.4", "100.0", "103.5", "107.2", "110.9", "114.8", "118.8", "123.0",
            "127.3", "131.8", "136.5", "141.3", "146.2", "151.4", "156.7", "159.8", "162.2", "165.5",
            "167.9", "171.3", "173.8", "177.3", "179.9", "183.5", "186.2", "189.9", "192.8", "196.6",
            "199.5", "203.5", "206.5", "210.7", "218.1", "225.7", "229.1", "233.6", "241.8", "250.3"
        };
        try {
            // Format is P1(tx/rx) + P2P2(tone index)
            boolean isTx = data.charAt(0) == '0';
            int toneIndex = Integer.parseInt(data.substring(1));
            String toneFreq = (toneIndex > 0 && toneIndex < tones.length) ? tones[toneIndex] + " Hz" : "index " + toneIndex;
            return (isTx ? "TX" : "RX") + " CTCSS tone: " + toneFreq;
        } catch (Exception e) {
            return "CTCSS tone: " + data;
        }
    }

    private String interpretContour(String data) {
        // CO format: P1(VFO) + P2(param) + P3P3P3P3(value)
        if (data.length() < 2) return "Contour: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        char param = data.charAt(1);
        String paramName;
        switch (param) {
            case '0': paramName = "state"; break;
            case '1': paramName = "frequency"; break;
            case '2': paramName = "APF level"; break;
            default: paramName = "param " + param;
        }
        if (data.length() >= 6) {
            try {
                int value = Integer.parseInt(data.substring(2));
                if (param == '0') {
                    return vfo + " Contour is " + (value == 0 ? "OFF" : "ON");
                }
                return vfo + " Contour " + paramName + ": " + value;
            } catch (NumberFormatException e) {
                return vfo + " Contour " + paramName + ": " + data.substring(2);
            }
        }
        return vfo + " Contour " + paramName;
    }

    private String interpretCtcssMode(String data) {
        // CT format: P1(VFO) + P2(mode)
        if (data.length() < 2) return "CTCSS mode: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        char mode = data.charAt(1);
        String modeName;
        switch (mode) {
            case '0': modeName = "OFF"; break;
            case '1': modeName = "CTCSS ENC/DEC"; break;
            case '2': modeName = "CTCSS ENC only"; break;
            case '3': modeName = "DCS ENC/DEC"; break;
            case '4': modeName = "DCS ENC only"; break;
            default: modeName = "mode " + mode;
        }
        return vfo + " tone mode: " + modeName;
    }

    private String interpretLcdSettings(String data) {
        // DA format: P1(setting) + P2P2(value)
        if (data.length() < 3) return "LCD settings: " + data;
        char setting = data.charAt(0);
        try {
            int value = Integer.parseInt(data.substring(1));
            if (setting == '0') {
                return "LCD dimmer: " + value;
            } else {
                return "LCD contrast: " + value;
            }
        } catch (NumberFormatException e) {
            return "LCD settings: " + data;
        }
    }

    private String interpretDcsCode(String data) {
        // DC format: P1(tx/rx) + P2P2P2(code)
        if (data.length() < 4) return "DCS code: " + data;
        boolean isTx = data.charAt(0) == '0';
        String code = data.substring(1);
        return (isTx ? "TX" : "RX") + " DCS code: " + code;
    }

    private String interpretDateTime(String data) {
        // DT format varies by parameter
        if (data.length() < 2) return "Date/Time: " + data;
        char param = data.charAt(0);
        String value = data.substring(1);
        switch (param) {
            case '0': return "Date: " + value;
            case '1': return "Time: " + value;
            case '2': return "UTC offset: " + value;
            default: return "Date/Time: " + data;
        }
    }

    private String interpretIfShift(String data) {
        // IS format: P1(VFO) + P2P2P2P2(value, center=1000)
        if (data.length() < 5) return "IF Shift: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        try {
            int value = Integer.parseInt(data.substring(1));
            int offset = value - 1000;
            String sign = offset >= 0 ? "+" : "";
            return vfo + " IF Shift: " + sign + offset + " Hz (raw: " + value + ")";
        } catch (NumberFormatException e) {
            return vfo + " IF Shift: " + data.substring(1);
        }
    }

    private String interpretKeyerStatus(String data) {
        // KY format: just status 0=ready, 1=busy
        if (data.equals("0")) return "Keyer buffer: ready";
        if (data.equals("1")) return "Keyer buffer: busy (sending)";
        return "Keyer status: " + data;
    }

    private String interpretMonitorLevel(String data) {
        // ML format: P1(VFO) + P2P2P2(level 0-100)
        if (data.length() < 4) return "Monitor level: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        try {
            int level = Integer.parseInt(data.substring(1));
            return vfo + " monitor level: " + level + "%";
        } catch (NumberFormatException e) {
            return vfo + " monitor level: " + data.substring(1);
        }
    }

    private String interpretNoiseBlankerLevel(String data) {
        // NL format: P1(VFO) + P2P2P2(level 0-10)
        if (data.length() < 4) return "Noise Blanker: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        try {
            int level = Integer.parseInt(data.substring(1));
            if (level == 0) {
                return vfo + " Noise Blanker is OFF";
            }
            return vfo + " Noise Blanker level: " + level;
        } catch (NumberFormatException e) {
            return vfo + " Noise Blanker: " + data.substring(1);
        }
    }

    private String interpretNoiseReductionLevel(String data) {
        // RL format: P1(VFO) + P2P2(level 0-15)
        if (data.length() < 3) return "DNR: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        try {
            int level = Integer.parseInt(data.substring(1));
            if (level == 0) {
                return vfo + " DNR (Noise Reduction) is OFF";
            }
            return vfo + " DNR level: " + level;
        } catch (NumberFormatException e) {
            return vfo + " DNR: " + data.substring(1);
        }
    }

    private String interpretScan(String data) {
        // SC format: P1(mode) + P2(VFO) + P3(state)
        if (data.length() < 3) return "Scan: " + data;
        char state = data.charAt(0);
        if (state == '0') {
            return "Scan is OFF";
        }
        String vfo = data.charAt(1) == '0' ? "Main" : "Sub";
        char mode = data.charAt(2);
        String modeName;
        switch (mode) {
            case '1': modeName = "up"; break;
            case '2': modeName = "down"; break;
            default: modeName = "mode " + mode;
        }
        return vfo + " scanning " + modeName;
    }

    private String interpretWidth(String data) {
        // SH format: P1(VFO) + P2P2(width code)
        if (data.length() < 3) return "Width: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        try {
            int code = Integer.parseInt(data.substring(1));
            // Width codes vary by mode, just show the code
            return vfo + " filter width setting: " + code;
        } catch (NumberFormatException e) {
            return vfo + " filter width: " + data.substring(1);
        }
    }

    private String interpretVfoMemoryMode(String data) {
        // VM format: P1(VFO) + P2P2(mode: 00=VFO, 11=Memory)
        if (data.length() < 3) return "VFO/Memory mode: " + data;
        String vfo = data.charAt(0) == '0' ? "Main" : "Sub";
        String mode = data.substring(1);
        if (mode.equals("00")) {
            return vfo + " is in VFO mode";
        } else if (mode.equals("11")) {
            return vfo + " is in Memory mode";
        }
        return vfo + " mode: " + mode;
    }

    /**
     * Shows help information for a CAT command including format and examples.
     */
    private void showCatCommandHelp(String cmd, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(description).append(" ===\n");

        // Add format and examples based on the command
        switch (cmd) {
            case "AB":
                sb.append("Format:  AB;\n");
                sb.append("Example: AB;  (copy VFO-A to VFO-B)\n");
                sb.append("Note:    Set only, no query\n");
                break;
            case "AC":
                sb.append("Format:  AC[P1][P2]; or AC;\n");
                sb.append("Set:     AC01;   (tuner on/start)\n");
                sb.append("         AC00;   (tuner off)\n");
                sb.append("Read:    AC;  -> AC000;  (off) or AC011; (on, tuned)\n");
                break;
            case "AG":
                sb.append("Format:  AG[P1][P2P2P2]; or AG[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      000-255 (gain level)\n");
                sb.append("Set:     AG0128;  (set main AF gain to 128)\n");
                sb.append("Read:    AG0;  -> AG0128;\n");
                break;
            case "AI":
                sb.append("Format:  AI[P1]; or AI;\n");
                sb.append("P1:      0=off, 1=on\n");
                sb.append("Set:     AI1;  (enable auto information)\n");
                sb.append("Read:    AI;  -> AI0; or AI1;\n");
                break;
            case "BA":
                sb.append("Format:  BA;\n");
                sb.append("Example: BA;  (copy VFO-B to VFO-A)\n");
                sb.append("Note:    Set only, no query\n");
                break;
            case "BC":
                sb.append("Format:  BC[P1][P2]; or BC[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      0=off, 1=on\n");
                sb.append("Set:     BC01;  (enable auto notch on main)\n");
                sb.append("Read:    BC0;  -> BC00; or BC01;\n");
                break;
            case "BD":
                sb.append("Format:  BD;\n");
                sb.append("Example: BD;  (band down)\n");
                sb.append("Note:    Set only, no query\n");
                break;
            case "BI":
                sb.append("Format:  BI[P1]; or BI;\n");
                sb.append("P1:      0=off, 1=semi, 2=full\n");
                sb.append("Set:     BI1;  (semi break-in)\n");
                sb.append("Read:    BI;  -> BI1;\n");
                break;
            case "BU":
                sb.append("Format:  BU;\n");
                sb.append("Example: BU;  (band up)\n");
                sb.append("Note:    Set only, no query\n");
                break;
            case "CF":
                sb.append("Format:  CF[P1][P2][P3P3P3P3]; or CF[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      0=off, 1=on\n");
                sb.append("P3:      offset -9999 to +9999 Hz\n");
                sb.append("Set:     CF0100100;  (RIT +100Hz on main)\n");
                sb.append("Read:    CF0;  -> CF0100100;\n");
                break;
            case "DN":
                sb.append("Format:  DN;\n");
                sb.append("Example: DN;  (frequency down step)\n");
                sb.append("Note:    Set only, no query\n");
                break;
            case "FA":
                sb.append("Format:  FA[freq]; or FA;\n");
                sb.append("freq:    9-digit frequency in Hz (e.g., 014074000 = 14.074 MHz)\n");
                sb.append("Set:     FA014074000;  (set VFO-A to 14.074 MHz)\n");
                sb.append("Read:    FA;  -> FA014074000;\n");
                break;
            case "FB":
                sb.append("Format:  FB[freq]; or FB;\n");
                sb.append("freq:    9-digit frequency in Hz\n");
                sb.append("Set:     FB007074000;  (set VFO-B to 7.074 MHz)\n");
                sb.append("Read:    FB;  -> FB007074000;\n");
                break;
            case "FN":
                sb.append("Format:  FN[P1]; or FN;\n");
                sb.append("P1:      1-3 (filter number)\n");
                sb.append("Set:     FN1;  (select filter 1)\n");
                sb.append("Read:    FN;  -> FN1;\n");
                break;
            case "FR":
                sb.append("Format:  FR[P1]; or FR;\n");
                sb.append("P1:      0=VFO-A, 1=VFO-B, 2=Dual\n");
                sb.append("Set:     FR0;  (receive on VFO-A)\n");
                sb.append("Read:    FR;  -> FR0;\n");
                break;
            case "FT":
                sb.append("Format:  FT[P1]; or FT;\n");
                sb.append("P1:      0=VFO-A, 1=VFO-B\n");
                sb.append("Set:     FT1;  (transmit on VFO-B)\n");
                sb.append("Read:    FT;  -> FT0;\n");
                break;
            case "GT":
                sb.append("Format:  GT[P1][P2]; or GT[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      0=auto, 1=fast, 2=mid, 3=slow, 4=off\n");
                sb.append("Set:     GT00;  (AGC auto on main)\n");
                sb.append("Read:    GT0;  -> GT00;\n");
                break;
            case "ID":
                sb.append("Format:  ID;\n");
                sb.append("Read:    ID;  -> ID0763; (Field) or ID0840; (Optima/SPA-1)\n");
                sb.append("Note:    Read only\n");
                break;
            case "IF":
                sb.append("Format:  IF;\n");
                sb.append("Read:    IF;  -> IF00014074000...  (27-char status)\n");
                sb.append("Note:    Read only, returns comprehensive status\n");
                break;
            case "IS":
                sb.append("Format:  IS[P1][P2P2P2P2]; or IS[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      0000-2000 (center=1000)\n");
                sb.append("Set:     IS01100;  (IF shift +100 on main)\n");
                sb.append("Read:    IS0;  -> IS01000;\n");
                break;
            case "KP":
                sb.append("Format:  KP[P1P1P1]; or KP;\n");
                sb.append("P1:      300-1050 Hz (CW pitch)\n");
                sb.append("Set:     KP600;  (set pitch to 600Hz)\n");
                sb.append("Read:    KP;  -> KP600;\n");
                break;
            case "KR":
                sb.append("Format:  KR[P1]; or KR;\n");
                sb.append("P1:      0=off, 1=on\n");
                sb.append("Set:     KR1;  (keyer on)\n");
                sb.append("Read:    KR;  -> KR1;\n");
                break;
            case "KS":
                sb.append("Format:  KS[P1P1P1]; or KS;\n");
                sb.append("P1:      004-060 WPM\n");
                sb.append("Set:     KS020;  (set speed to 20 WPM)\n");
                sb.append("Read:    KS;  -> KS020;\n");
                break;
            case "KY":
                sb.append("Format:  KY[P1][P2]; or KY;\n");
                sb.append("P1:      0=query status, 1-5=send memory\n");
                sb.append("P2:      text (up to 24 chars)\n");
                sb.append("Set:     KY1;  (send keyer memory 1)\n");
                sb.append("         KY CQ CQ DE W1ABC;  (send text)\n");
                sb.append("Read:    KY;  -> KY0; (ready) or KY1; (busy)\n");
                break;
            case "LK":
                sb.append("Format:  LK[P1]; or LK;\n");
                sb.append("P1:      0=unlocked, 1=locked\n");
                sb.append("Set:     LK1;  (lock radio)\n");
                sb.append("Read:    LK;  -> LK0; or LK1;\n");
                break;
            case "MC":
                sb.append("Format:  MC[P1P1P1]; or MC;\n");
                sb.append("P1:      001-099 (memory channel)\n");
                sb.append("Set:     MC001;  (select memory 1)\n");
                sb.append("Read:    MC;  -> MC001;\n");
                break;
            case "MD":
                sb.append("Format:  MD[P1][P2]; or MD[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      1=LSB, 2=USB, 3=CW, 4=FM, 5=AM, 6=RTTY, 7=CWR,\n");
                sb.append("         8=DATA, 9=RTTY-R, A=FM-N, B=DATA-FM, C=DATA-LSB\n");
                sb.append("Set:     MD02;  (set main to USB)\n");
                sb.append("Read:    MD0;  -> MD02;\n");
                break;
            case "MG":
                sb.append("Format:  MG[P1P1P1]; or MG;\n");
                sb.append("P1:      000-100 (mic gain)\n");
                sb.append("Set:     MG050;  (set mic gain to 50)\n");
                sb.append("Read:    MG;  -> MG050;\n");
                break;
            case "ML":
                sb.append("Format:  ML[P1][P2P2P2]; or ML[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      000-100 (monitor level)\n");
                sb.append("Set:     ML0050;  (set main monitor to 50)\n");
                sb.append("Read:    ML0;  -> ML0050;\n");
                break;
            case "MR":
                sb.append("Format:  MR[P1P1P1P1P1];\n");
                sb.append("P1:      00001-00099 (5-digit memory channel)\n");
                sb.append("Read:    MR00001;  -> memory 1 contents\n");
                sb.append("Note:    Read only\n");
                break;
            case "MX":
                sb.append("Format:  MX[P1]; or MX;\n");
                sb.append("P1:      0=off, 1=on\n");
                sb.append("Set:     MX1;  (enable MOX)\n");
                sb.append("Read:    MX;  -> MX0; or MX1;\n");
                break;
            case "NA":
                sb.append("Format:  NA[P1][P2]; or NA[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      0=wide, 1=narrow\n");
                sb.append("Set:     NA01;  (narrow on main)\n");
                sb.append("Read:    NA0;  -> NA00; or NA01;\n");
                break;
            case "NL":
                sb.append("Format:  NL[P1][P2P2P2]; or NL[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      000-010 (NB level)\n");
                sb.append("Set:     NL0005;  (set main NB to 5)\n");
                sb.append("Read:    NL0;  -> NL0005;\n");
                break;
            case "OI":
                sb.append("Format:  OI;\n");
                sb.append("Read:    OI;  -> OI... (opposite band info)\n");
                sb.append("Note:    Read only\n");
                break;
            case "OS":
                sb.append("Format:  OS[P1][P2]; or OS[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      0=simplex, 1=plus, 2=minus\n");
                sb.append("Set:     OS01;  (plus shift on main)\n");
                sb.append("Read:    OS0;  -> OS00;\n");
                break;
            case "PA":
                sb.append("Format:  PA[P1][P2]; or PA[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      0=IPO, 1=AMP1, 2=AMP2\n");
                sb.append("Set:     PA01;  (preamp AMP1 on main)\n");
                sb.append("Read:    PA0;  -> PA00;\n");
                break;
            case "PC":
                sb.append("Format:  PC[P1P1P1]; or PC;\n");
                sb.append("P1:      005-100 watts (or decimal for Field head)\n");
                sb.append("Set:     PC050;  (set power to 50W)\n");
                sb.append("         PC10.5; (10.5W on Field head)\n");
                sb.append("Read:    PC;  -> PC050;\n");
                break;
            case "PL":
                sb.append("Format:  PL[P1P1P1]; or PL;\n");
                sb.append("P1:      000-100 (processor level)\n");
                sb.append("Set:     PL050;  (set processor level to 50)\n");
                sb.append("Read:    PL;  -> PL050;\n");
                break;
            case "PR":
                sb.append("Format:  PR[P1]; or PR;\n");
                sb.append("P1:      0=off, 1=on\n");
                sb.append("Set:     PR1;  (processor on)\n");
                sb.append("Read:    PR;  -> PR0; or PR1;\n");
                break;
            case "PS":
                sb.append("Format:  PS[P1]; or PS;\n");
                sb.append("P1:      0=off (cannot power on via CAT)\n");
                sb.append("Set:     PS0;  (power off radio)\n");
                sb.append("Read:    PS;  -> PS1; (on)\n");
                sb.append("Note:    Can only power OFF via CAT command\n");
                break;
            case "RA":
                sb.append("Format:  RA[P1][P2]; or RA[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      0=off, 1=on\n");
                sb.append("Set:     RA01;  (attenuator on main)\n");
                sb.append("Read:    RA0;  -> RA00; or RA01;\n");
                break;
            case "RG":
                sb.append("Format:  RG[P1][P2P2P2]; or RG[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      000-255 (RF gain)\n");
                sb.append("Set:     RG0255;  (max RF gain on main)\n");
                sb.append("Read:    RG0;  -> RG0255;\n");
                break;
            case "RI":
                sb.append("Format:  RI[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("Read:    RI0;  -> RI0+0000; (RIT offset)\n");
                sb.append("Note:    Read only\n");
                break;
            case "RL":
                sb.append("Format:  RL[P1][P2P2]; or RL[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      00-15 (DNR level)\n");
                sb.append("Set:     RL005;  (DNR level 5 on main)\n");
                sb.append("Read:    RL0;  -> RL005;\n");
                break;
            case "RM":
                sb.append("Format:  RM[P1];\n");
                sb.append("P1:      1=ALC, 2=SWR, 3=COMP, 4=ID, 5=VDD\n");
                sb.append("Read:    RM1;  -> RM1XXX; (ALC reading)\n");
                sb.append("         RM2;  -> RM2XXX; (SWR reading)\n");
                sb.append("Note:    Read only\n");
                break;
            case "SC":
                sb.append("Format:  SC[P1P2P3]; or SC;\n");
                sb.append("Set:     SC100;  (start scan)\n");
                sb.append("         SC000;  (stop scan)\n");
                sb.append("Read:    SC;  -> SC000; (not scanning)\n");
                break;
            case "SD":
                sb.append("Format:  SD[P1P1P1P1]; or SD;\n");
                sb.append("P1:      0030-3000 ms (break-in delay)\n");
                sb.append("Set:     SD0200;  (200ms delay)\n");
                sb.append("Read:    SD;  -> SD0200;\n");
                break;
            case "SH":
                sb.append("Format:  SH[P1][P2P2]; or SH[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      00-21 (width/shift setting)\n");
                sb.append("Set:     SH010;  (width setting 10)\n");
                sb.append("Read:    SH0;  -> SH010;\n");
                break;
            case "SM":
                sb.append("Format:  SM[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("Read:    SM0;  -> SM0XXX; (S-meter 0-255)\n");
                sb.append("Note:    Read only, returns 0-255 value\n");
                break;
            case "SQ":
                sb.append("Format:  SQ[P1][P2P2P2]; or SQ[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      000-255 (squelch level)\n");
                sb.append("Set:     SQ0128;  (set main squelch to 128)\n");
                sb.append("Read:    SQ0;  -> SQ0128;\n");
                break;
            case "ST":
                sb.append("Format:  ST[P1]; or ST;\n");
                sb.append("P1:      0=off, 1=on\n");
                sb.append("Set:     ST1;  (enable split)\n");
                sb.append("Read:    ST;  -> ST0; or ST1;\n");
                break;
            case "SV":
                sb.append("Format:  SV;\n");
                sb.append("Example: SV;  (swap VFO-A and VFO-B)\n");
                sb.append("Note:    Set only, no query\n");
                break;
            case "TS":
                sb.append("Format:  TS[P1]; or TS;\n");
                sb.append("P1:      0=off, 1=on\n");
                sb.append("Set:     TS1;  (TX watch on)\n");
                sb.append("Read:    TS;  -> TS0; or TS1;\n");
                break;
            case "TX":
                sb.append("Format:  TX[P1]; or TX;\n");
                sb.append("P1:      0=RX, 1=TX (MIC), 2=TX (DATA)\n");
                sb.append("Set:     TX1;  (transmit on mic)\n");
                sb.append("         TX0;  (back to receive)\n");
                sb.append("Read:    TX;  -> TX0; (RX) or TX1; (TX)\n");
                break;
            case "UP":
                sb.append("Format:  UP;\n");
                sb.append("Example: UP;  (frequency up step)\n");
                sb.append("Note:    Set only, no query\n");
                break;
            case "VD":
                sb.append("Format:  VD[P1P1P1P1]; or VD;\n");
                sb.append("P1:      0030-3000 ms (VOX delay)\n");
                sb.append("Set:     VD0500;  (500ms VOX delay)\n");
                sb.append("Read:    VD;  -> VD0500;\n");
                break;
            case "VE":
                sb.append("Format:  VE;\n");
                sb.append("Read:    VE;  -> VE01.08; (firmware version)\n");
                sb.append("Note:    Read only\n");
                break;
            case "VG":
                sb.append("Format:  VG[P1P1P1]; or VG;\n");
                sb.append("P1:      000-100 (VOX gain)\n");
                sb.append("Set:     VG050;  (VOX gain 50)\n");
                sb.append("Read:    VG;  -> VG050;\n");
                break;
            case "VM":
                sb.append("Format:  VM[P1][P2P2]; or VM[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("P2:      00=VFO, 11=Memory\n");
                sb.append("Set:     VM011;  (main to memory mode)\n");
                sb.append("Read:    VM0;  -> VM000; or VM011;\n");
                break;
            case "VS":
                sb.append("Format:  VS[P1]; or VS;\n");
                sb.append("P1:      0=VFO-A, 1=VFO-B\n");
                sb.append("Set:     VS0;  (select VFO-A)\n");
                sb.append("Read:    VS;  -> VS0; or VS1;\n");
                break;
            case "VX":
                sb.append("Format:  VX[P1]; or VX;\n");
                sb.append("P1:      0=off, 1=on\n");
                sb.append("Set:     VX1;  (VOX on)\n");
                sb.append("Read:    VX;  -> VX0; or VX1;\n");
                break;
            case "ZI":
                sb.append("Format:  ZI[P1];\n");
                sb.append("P1:      0=Main, 1=Sub\n");
                sb.append("Example: ZI0;  (zero-in on main)\n");
                sb.append("Note:    Set only, no query\n");
                break;
            default:
                sb.append("Format:  ").append(cmd).append("[params]; or ").append(cmd).append(";\n");
                sb.append("Read:    ").append(cmd).append(";\n");
                sb.append("Note:    See FTX-1 CAT manual for parameters\n");
                break;
        }

        sb.append("\nPress Enter or click Send to execute: ").append(cmd).append(";");
        responseArea.setText("");
        appendResponse(sb.toString());
    }

    /**
     * Populates the radio status panel with current values from the radio.
     */
    private void populateRadioStatus() {
        if (!connected || rig == null) return;

        try {
            // Head type - display name may include "Yaesu FTX-1" prefix
            HeadType headType = rig.getHeadType();
            String displayName = headType.getDisplayName();
            // Strip prefixes to get just the head type (e.g., "Field (12V)")
            String headOnly = displayName
                .replace("Yaesu FTX-1 ", "")
                .replace("FTX-1 ", "")
                .replace("Yaesu ", "");
            headTypeLabel.setText("<html>Yaesu FTX-1<br>" + headOnly + "</html>");

            // VFO-A Frequency
            long freqA = rig.getFrequency(VFO.MAIN);
            freqLabelA.setText(formatFrequency(freqA) + " MHz");

            // VFO-A Mode
            OperatingMode modeA = rig.getMode(VFO.MAIN);
            modeLabelA.setText(modeA != null ? modeA.name() : "--");

            // VFO-B Frequency
            long freqB = rig.getFrequency(VFO.SUB);
            freqLabelB.setText(formatFrequency(freqB) + " MHz");

            // VFO-B Mode
            OperatingMode modeB = rig.getMode(VFO.SUB);
            modeLabelB.setText(modeB != null ? modeB.name() : "--");

            // Active VFO
            VFO vfo = rig.getActiveVfo();
            activeVfoIsB = (vfo == VFO.SUB);
            activeVfoLabel.setText(vfo == VFO.MAIN ? "VFO-A" : "VFO-B");

            // PTT
            boolean ptt = rig.isPtt();
            if (ptt) {
                pttLabel.setText("TX");
                pttLabel.setForeground(Color.RED);
            } else {
                pttLabel.setText("RX");
                pttLabel.setForeground(new Color(0, 128, 0));
            }

            // Power
            double power = rig.getPower();
            powerLabel.setText(formatPower(power));

            // S-Meter
            int smeter = rig.getSmeter();
            String smeterStr = formatSmeter(smeter);
            smeterLabel.setText(smeterStr + " (" + smeter + " dB)");

        } catch (CatException e) {
            // Ignore errors during status population
        }
    }

    /**
     * Formats S-meter value to S-units.
     */
    private String formatSmeter(int smeter) {
        if (smeter < -54) return "S0";
        if (smeter < -48) return "S1";
        if (smeter < -42) return "S2";
        if (smeter < -36) return "S3";
        if (smeter < -30) return "S4";
        if (smeter < -24) return "S5";
        if (smeter < -18) return "S6";
        if (smeter < -12) return "S7";
        if (smeter < -6) return "S8";
        if (smeter < 0) return "S9";
        return "S9+" + smeter + "dB";
    }

    /**
     * Formats power value for display.
     * Shows one decimal place for fractional watts, whole numbers otherwise.
     */
    private String formatPower(double power) {
        if (power == Math.floor(power)) {
            return String.format("%.0f W", power);
        } else {
            return String.format("%.1f W", power);
        }
    }

    private void showAbout() {
        String message = "FTX1-Hamlib GUI v" + VERSION + "\n\n" +
                "Hamlib-compatible daemon for Yaesu FTX-1\n\n" +
                "Copyright (c) 2025 Terrell Deppe (KJ5HST)\n\n" +
                "Licensed under LGPL";
        JOptionPane.showMessageDialog(this, message, "About FTX1-Hamlib",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void exitApplication() {
        if (audioControlPanel != null) {
            audioControlPanel.shutdown();
        }
        if (connected) {
            disconnect();
        }
        System.exit(0);
    }

    /**
     * Sets up SLF4J logging interception for comm monitor.
     */
    private void setupCommMonitorLogging() {
        // Get the root logger context and add our appender there to capture all CatConnection logs
        ch.qos.logback.classic.LoggerContext loggerContext =
            (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();

        // Create and configure our appender
        ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> commAppender =
            new ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent>() {
            @Override
            protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                if (!monitorEnabled || commMonitorArea == null) {
                    return;
                }

                // Only process CatConnection logs
                if (!event.getLoggerName().equals("com.yaesu.ftx1.core.CatConnection")) {
                    return;
                }

                String msg = event.getFormattedMessage();
                if (msg == null) return;

                // Filter for Sending/Received messages
                String direction = null;
                String data = null;

                if (msg.startsWith("Sending: ")) {
                    direction = "TX";
                    data = msg.substring(9);
                } else if (msg.startsWith("Received: ")) {
                    direction = "RX";
                    data = msg.substring(10);
                }

                if (direction != null && data != null) {
                    final String dir = direction;
                    final String d = data;
                    SwingUtilities.invokeLater(() -> logComm(dir, d));
                }
            }
        };

        commAppender.setName("CommMonitorAppender");
        commAppender.setContext(loggerContext);
        commAppender.start();

        // Add to root logger to capture everything
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(commAppender);
    }

    /**
     * Formats a socket address for display.
     * Simplifies IPv6 loopback to "localhost".
     */
    private String formatAddress(String address) {
        if (address == null) return "unknown";
        // Remove leading slash if present
        String addr = address.startsWith("/") ? address.substring(1) : address;
        // Replace IPv6 loopback with localhost
        if (addr.startsWith("[0:0:0:0:0:0:0:1]:") || addr.startsWith("[::1]:")) {
            return "localhost:" + addr.substring(addr.lastIndexOf(':') + 1);
        }
        // Replace IPv4 loopback with localhost
        if (addr.startsWith("127.0.0.1:")) {
            return "localhost:" + addr.substring(10);
        }
        return addr;
    }

    /**
     * Logs a communication event to the monitor.
     */
    private void logComm(String direction, String data) {
        if (!monitorEnabled || commMonitorArea == null) return;

        StringBuilder sb = new StringBuilder();
        if (showTimestamps) {
            sb.append(java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
            sb.append(" ");
        }
        sb.append("[").append(direction).append("] ");
        sb.append(data).append("\n");

        commMonitorArea.append(sb.toString());

        // Limit buffer size (keep last 10000 chars)
        if (commMonitorArea.getText().length() > 10000) {
            String text = commMonitorArea.getText();
            commMonitorArea.setText(text.substring(text.length() - 8000));
        }
    }

    /**
     * Public method to log CAT communications from external sources.
     */
    public void logCommunication(String direction, String data) {
        if (monitorEnabled) {
            SwingUtilities.invokeLater(() -> logComm(direction, data));
        }
    }

    /**
     * Changes the application language and rebuilds the UI.
     */
    private void changeLanguage(Locale locale) {
        Messages.setLocale(locale);

        // Save current selections before rebuilding
        String selectedPort = (String) portCombo.getSelectedItem();
        Integer selectedBaud = (Integer) baudCombo.getSelectedItem();

        // Update window title
        setTitle(Messages.get("app.title"));

        // Rebuild menu bar
        setJMenuBar(createMenuBar());

        // Rebuild all panels
        getContentPane().removeAll();

        JPanel topPanel = createTopPanel();
        JPanel centerPanel = createCenterPanel();
        JPanel bottomPanel = createBottomPanel();

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Re-setup event handlers for new components
        setupEventHandlers();

        // Refresh port list and restore selection
        scanSerialPorts();
        if (selectedPort != null) {
            portCombo.setSelectedItem(selectedPort);
        }
        if (selectedBaud != null) {
            baudCombo.setSelectedItem(selectedBaud);
        }

        // Restore connection state
        if (connected) {
            connectionStatus.setText(Messages.get("connection.status.connected"));
            connectionStatus.setForeground(new Color(0, 128, 0));
            connectButton.setText(Messages.get("connection.disconnect"));
            commandField.setEnabled(true);
            sendButton.setEnabled(true);
            daemonButton.setEnabled(true);

            // Disable port selection while connected
            portCombo.setEnabled(false);
            baudCombo.setEnabled(false);
            refreshPortsButton.setEnabled(false);

            // Refresh radio status display
            populateRadioStatus();
        }

        // Restore daemon state
        if (daemonRunning) {
            int port = (Integer) tcpPortSpinner.getValue();
            daemonButton.setText(Messages.get("hamlib.stop"));
            daemonStatus.setText(Messages.get("hamlib.status.running", port));
            daemonStatus.setForeground(new Color(0, 128, 0));
        }

        // Revalidate and repaint
        revalidate();
        repaint();
    }

    /**
     * Resets to system default language and rebuilds the UI.
     */
    private void resetToDefaultLanguage() {
        Messages.resetToDefault();

        // Save current selections before rebuilding
        String selectedPort = (String) portCombo.getSelectedItem();
        Integer selectedBaud = (Integer) baudCombo.getSelectedItem();

        // Update window title
        setTitle(Messages.get("app.title"));

        // Rebuild menu bar
        setJMenuBar(createMenuBar());

        // Rebuild all panels
        getContentPane().removeAll();

        JPanel topPanel = createTopPanel();
        JPanel centerPanel = createCenterPanel();
        JPanel bottomPanel = createBottomPanel();

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Re-setup event handlers for new components
        setupEventHandlers();

        // Refresh port list and restore selection
        scanSerialPorts();
        if (selectedPort != null) {
            portCombo.setSelectedItem(selectedPort);
        }
        if (selectedBaud != null) {
            baudCombo.setSelectedItem(selectedBaud);
        }

        // Restore connection state
        if (connected) {
            connectionStatus.setText(Messages.get("connection.status.connected"));
            connectionStatus.setForeground(new Color(0, 128, 0));
            connectButton.setText(Messages.get("connection.disconnect"));
            commandField.setEnabled(true);
            sendButton.setEnabled(true);
            daemonButton.setEnabled(true);

            // Disable port selection while connected
            portCombo.setEnabled(false);
            baudCombo.setEnabled(false);
            refreshPortsButton.setEnabled(false);

            // Refresh radio status display
            populateRadioStatus();
        }

        // Restore daemon state
        if (daemonRunning) {
            int port = (Integer) tcpPortSpinner.getValue();
            daemonButton.setText(Messages.get("hamlib.stop"));
            daemonStatus.setText(Messages.get("hamlib.status.running", port));
            daemonStatus.setForeground(new Color(0, 128, 0));
        }

        // Revalidate and repaint
        revalidate();
        repaint();
    }

    public static void main(String[] args) {
        // Set look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use default
        }

        // Create and show GUI
        SwingUtilities.invokeLater(() -> {
            HamlibGUI gui = new HamlibGUI();
            gui.setVisible(true);
        });
    }
}
