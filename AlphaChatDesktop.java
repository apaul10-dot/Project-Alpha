import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AlphaChatDesktop extends JFrame {
    // Color scheme
    private static final Color PRIMARY_COLOR = new Color(99, 102, 241);
    private static final Color SECONDARY_COLOR = new Color(6, 182, 212);
    private static final Color BACKGROUND_DARK = new Color(15, 15, 35);
    private static final Color CARD_BACKGROUND = new Color(30, 41, 59);
    private static final Color TEXT_PRIMARY = new Color(248, 250, 252);
    private static final Color TEXT_SECONDARY = new Color(203, 213, 225);
    private static final Color BORDER_COLOR = new Color(51, 65, 85);
    private static final Color SUCCESS_COLOR = new Color(16, 185, 129);
    
    // Components
    private JPanel mainPanel;
    private JTextPane messagesArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton emojiButton;
    private JLabel statusLabel;
    private JLabel userCountLabel;
    private JMenuBar menuBar;
    private JPopupMenu emojiMenu;
    
    // Server components
    private ServerSocket serverSocket;
    private List<PrintWriter> clients;
    private List<String> messageHistory;
    private Map<String, String> userProfiles;
    private boolean isServerRunning;
    private int port = 3000;
    private String networkIP;
    
    // Settings
    private boolean darkMode = true;
    private boolean soundEnabled = true;
    private boolean notificationsEnabled = true;
    private String fontSize = "medium";
    private String currentUser = "Desktop";
    
    public AlphaChatDesktop() {
        initializeComponents();
        setupUI();
        setupEventHandlers();
        startServer();
        loadSettings();
    }
    
    private void initializeComponents() {
        // Get network IP
        try {
            networkIP = getNetworkIP();
        } catch (Exception e) {
            networkIP = "localhost";
        }
        
        // Initialize data structures
        clients = new CopyOnWriteArrayList<>();
        messageHistory = new CopyOnWriteArrayList<>();
        userProfiles = new HashMap<>();
        
        // Initialize components
        mainPanel = new JPanel(new BorderLayout());
        messagesArea = new JTextPane();
        messageField = new JTextField();
        sendButton = new JButton("Send");
        emojiButton = new JButton("😀");
        statusLabel = new JLabel("Server starting...");
        userCountLabel = new JLabel("Users: 1");
        
        // Initialize menu bar
        menuBar = new JMenuBar();
        
        // Initialize emoji menu
        emojiMenu = new JPopupMenu();
        setupEmojiMenu();
    }
    
    private void setupUI() {
        setTitle("AlphaChat Desktop - Phone to Desktop Messaging");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        
        // Apply dark theme
        applyDarkTheme();
        
        // Setup main panel
        setupMainPanel();
        
        // Setup menu bar
        setupMenuBar();
        
        // Message area and input area are set up in createMessagesPanel and createInputPanel
        
        // Setup status bar
        setupStatusBar();
        
        setJMenuBar(menuBar);
        add(mainPanel);
    }
    
    private void setupMainPanel() {
        mainPanel.setBackground(BACKGROUND_DARK);
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Create header panel
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Create messages panel
        JPanel messagesPanel = createMessagesPanel();
        mainPanel.add(messagesPanel, BorderLayout.CENTER);
        
        // Create input panel
        JPanel inputPanel = createInputPanel();
        mainPanel.add(inputPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(CARD_BACKGROUND);
        headerPanel.setBorder(new CompoundBorder(
            new LineBorder(BORDER_COLOR, 1),
            new EmptyBorder(15, 20, 15, 20)
        ));
        
        // Title
        JLabel titleLabel = new JLabel("AlphaChat Desktop");
        titleLabel.setFont(new Font("Inter", Font.BOLD, 24));
        titleLabel.setForeground(TEXT_PRIMARY);
        
        // Status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        statusPanel.setBackground(CARD_BACKGROUND);
        
        // Connection status
        JPanel connectionStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        connectionStatus.setBackground(CARD_BACKGROUND);
        
        JLabel statusIcon = new JLabel("●");
        statusIcon.setForeground(SUCCESS_COLOR);
        statusIcon.setFont(new Font("Arial", Font.BOLD, 12));
        
        statusLabel.setForeground(TEXT_SECONDARY);
        statusLabel.setFont(new Font("Inter", Font.PLAIN, 12));
        
        connectionStatus.add(statusIcon);
        connectionStatus.add(statusLabel);
        
        userCountLabel.setForeground(TEXT_SECONDARY);
        userCountLabel.setFont(new Font("Inter", Font.PLAIN, 12));
        
        statusPanel.add(connectionStatus);
        statusPanel.add(Box.createHorizontalStrut(20));
        statusPanel.add(userCountLabel);
        
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(statusPanel, BorderLayout.EAST);
        
        return headerPanel;
    }
    
    private JPanel createMessagesPanel() {
        JPanel messagesPanel = new JPanel(new BorderLayout());
        messagesPanel.setBackground(CARD_BACKGROUND);
        messagesPanel.setBorder(new CompoundBorder(
            new LineBorder(BORDER_COLOR, 1),
            new EmptyBorder(10, 10, 10, 10)
        ));
        
        // Messages area
        messagesArea.setEditable(false);
        messagesArea.setBackground(CARD_BACKGROUND);
        messagesArea.setForeground(TEXT_PRIMARY);
        messagesArea.setFont(new Font("Inter", Font.PLAIN, 14));
        messagesArea.setBorder(null);
        
        // Make messages area scrollable
        JScrollPane scrollPane = new JScrollPane(messagesArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setBackground(BACKGROUND_DARK);
        scrollPane.getVerticalScrollBar().setForeground(TEXT_SECONDARY);
        
        messagesPanel.add(scrollPane, BorderLayout.CENTER);
        
        return messagesPanel;
    }
    
    private JPanel createInputPanel() {
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(CARD_BACKGROUND);
        inputPanel.setBorder(new CompoundBorder(
            new LineBorder(BORDER_COLOR, 1),
            new EmptyBorder(15, 15, 15, 15)
        ));
        
        // Input field
        messageField.setFont(new Font("Inter", Font.PLAIN, 14));
        messageField.setBackground(new Color(51, 65, 85));
        messageField.setForeground(TEXT_PRIMARY);
        messageField.setBorder(new CompoundBorder(
            new LineBorder(BORDER_COLOR, 1),
            new EmptyBorder(10, 15, 10, 15)
        ));
        messageField.setCaretColor(TEXT_PRIMARY);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setBackground(CARD_BACKGROUND);
        
        // Emoji button
        emojiButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        emojiButton.setBackground(new Color(51, 65, 85));
        emojiButton.setForeground(TEXT_PRIMARY);
        emojiButton.setBorder(new LineBorder(BORDER_COLOR, 1));
        emojiButton.setPreferredSize(new Dimension(40, 40));
        emojiButton.setFocusPainted(false);
        
        // Send button
        sendButton.setFont(new Font("Inter", Font.BOLD, 14));
        sendButton.setBackground(PRIMARY_COLOR);
        sendButton.setForeground(Color.WHITE);
        sendButton.setBorder(null);
        sendButton.setPreferredSize(new Dimension(80, 40));
        sendButton.setFocusPainted(false);
        
        buttonPanel.add(emojiButton);
        buttonPanel.add(sendButton);
        
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);
        
        return inputPanel;
    }
    
    private void setupStatusBar() {
        // Status bar is integrated into header panel
    }
    
    private void setupMenuBar() {
        // File menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.setForeground(TEXT_PRIMARY);
        
        JMenuItem settingsItem = new JMenuItem("Settings");
        JMenuItem profileItem = new JMenuItem("Profile");
        JMenuItem connectItem = new JMenuItem("Connection Info");
        JMenuItem exitItem = new JMenuItem("Exit");
        
        // Add action listeners
        settingsItem.addActionListener(e -> showSettingsDialog());
        profileItem.addActionListener(e -> showProfileDialog());
        connectItem.addActionListener(e -> showConnectionDialog());
        exitItem.addActionListener(e -> System.exit(0));
        
        fileMenu.add(settingsItem);
        fileMenu.add(profileItem);
        fileMenu.add(connectItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        
        // Help menu
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setForeground(TEXT_PRIMARY);
        
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);
        
        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        menuBar.setBackground(CARD_BACKGROUND);
    }
    
    private void setupEmojiMenu() {
        String[] emojis = {
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
            "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
            "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
            "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
            "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯",
            "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
            "❤️", "💙", "💚", "💛", "🧡", "💜", "🖤", "🤍", "🤎", "💔",
            "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉",
            "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🤝", "👏",
            "🙌", "👐", "🤲", "🤜", "🤛", "✊", "👊", "👎", "👍", "👌"
        };
        
        JPanel emojiPanel = new JPanel(new GridLayout(10, 10, 2, 2));
        emojiPanel.setBackground(CARD_BACKGROUND);
        emojiPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        for (String emoji : emojis) {
            JButton emojiBtn = new JButton(emoji);
            emojiBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
            emojiBtn.setBackground(CARD_BACKGROUND);
            emojiBtn.setForeground(TEXT_PRIMARY);
            emojiBtn.setBorder(new LineBorder(BORDER_COLOR, 1));
            emojiBtn.setPreferredSize(new Dimension(30, 30));
            emojiBtn.setFocusPainted(false);
            
            emojiBtn.addActionListener(e -> {
                messageField.setText(messageField.getText() + emoji);
                messageField.requestFocus();
                emojiMenu.setVisible(false);
            });
            
            emojiPanel.add(emojiBtn);
        }
        
        emojiMenu.add(emojiPanel);
    }
    
    private void setupEventHandlers() {
        // Send button
        sendButton.addActionListener(e -> sendMessage());
        
        // Enter key in message field
        messageField.addActionListener(e -> sendMessage());
        
        // Emoji button
        emojiButton.addActionListener(e -> {
            emojiMenu.show(emojiButton, 0, -emojiMenu.getPreferredSize().height);
        });
        
        // Window closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopServer();
                System.exit(0);
            }
        });
    }
    
    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isServerRunning = true;
                
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Server running on " + networkIP + ":" + port);
                    statusLabel.setForeground(SUCCESS_COLOR);
                });
                
                addMessage("System", "Server started successfully!", Color.GRAY);
                addMessage("System", "Share this URL with your phone: http://" + networkIP + ":" + port, Color.CYAN);
                
                while (isServerRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        new Thread(() -> handleClient(clientSocket)).start();
                    } catch (IOException e) {
                        if (isServerRunning) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Failed to start server");
                    statusLabel.setForeground(Color.RED);
                });
                e.printStackTrace();
            }
        }).start();
    }
    
    private void handleClient(Socket clientSocket) {
        try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            
            clients.add(out);
            updateUserCount();
            
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.startsWith("MESSAGE:")) {
                    String message = inputLine.substring(8);
                    addMessage("Phone", message, PRIMARY_COLOR);
                    broadcastToClients("MESSAGE:Desktop:" + message);
                } else if (inputLine.startsWith("USER:")) {
                    String username = inputLine.substring(5);
                    currentUser = username;
                    addMessage("System", username + " connected", Color.GREEN);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            clients.removeIf(writer -> {
                try {
                    writer.println("DISCONNECT");
                    return false;
                } catch (Exception e) {
                    return true;
                }
            });
            updateUserCount();
            addMessage("System", "Phone disconnected", Color.ORANGE);
        }
    }
    
    private void broadcastToClients(String message) {
        for (PrintWriter client : clients) {
            try {
                client.println(message);
            } catch (Exception e) {
                // Remove failed client
                clients.remove(client);
            }
        }
    }
    
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            addMessage("Desktop", message, SECONDARY_COLOR);
            broadcastToClients("MESSAGE:Desktop:" + message);
            messageField.setText("");
        }
    }
    
    private void addMessage(String sender, String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = messagesArea.getStyledDocument();
                
                // Create style for sender
                SimpleAttributeSet senderStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(senderStyle, color);
                StyleConstants.setBold(senderStyle, true);
                StyleConstants.setFontSize(senderStyle, 12);
                
                // Create style for message
                SimpleAttributeSet messageStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(messageStyle, TEXT_PRIMARY);
                StyleConstants.setFontSize(messageStyle, 14);
                
                // Create style for timestamp
                SimpleAttributeSet timeStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(timeStyle, TEXT_SECONDARY);
                StyleConstants.setFontSize(timeStyle, 10);
                StyleConstants.setItalic(timeStyle, true);
                
                // Add timestamp
                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                doc.insertString(doc.getLength(), "[" + timestamp + "] ", timeStyle);
                
                // Add sender
                doc.insertString(doc.getLength(), sender + ": ", senderStyle);
                
                // Add message
                doc.insertString(doc.getLength(), message + "\n", messageStyle);
                
                // Scroll to bottom
                messagesArea.setCaretPosition(doc.getLength());
                
                // Play sound if enabled
                if (soundEnabled) {
                    Toolkit.getDefaultToolkit().beep();
                }
                
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }
    
    private void updateUserCount() {
        SwingUtilities.invokeLater(() -> {
            int count = clients.size() + 1; // +1 for desktop
            userCountLabel.setText("Users: " + count);
        });
    }
    
    private void stopServer() {
        isServerRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void applyDarkTheme() {
        UIManager.put("control", CARD_BACKGROUND);
        UIManager.put("info", CARD_BACKGROUND);
        UIManager.put("nimbusBase", PRIMARY_COLOR);
        UIManager.put("nimbusAlertYellow", Color.YELLOW);
        UIManager.put("nimbusDisabledText", TEXT_SECONDARY);
        UIManager.put("nimbusFocus", PRIMARY_COLOR);
        UIManager.put("nimbusLightBackground", CARD_BACKGROUND);
        UIManager.put("nimbusOrange", Color.ORANGE);
        UIManager.put("nimbusRed", Color.RED);
        UIManager.put("nimbusSelectedText", TEXT_PRIMARY);
        UIManager.put("nimbusSelectionBackground", PRIMARY_COLOR);
        UIManager.put("text", TEXT_PRIMARY);
    }
    
    private String getNetworkIP() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("8.8.8.8", 80));
            return socket.getLocalAddress().getHostAddress();
        }
    }
    
    private void showSettingsDialog() {
        JDialog settingsDialog = new JDialog(this, "Settings", true);
        settingsDialog.setSize(500, 400);
        settingsDialog.setLocationRelativeTo(this);
        settingsDialog.getContentPane().setBackground(CARD_BACKGROUND);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(CARD_BACKGROUND);
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Title
        JLabel titleLabel = new JLabel("⚙️ Settings");
        titleLabel.setFont(new Font("Inter", Font.BOLD, 20));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // Settings panel
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setBackground(CARD_BACKGROUND);
        settingsPanel.setBorder(new EmptyBorder(20, 0, 20, 0));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Appearance section
        JLabel appearanceLabel = new JLabel("🎨 Appearance");
        appearanceLabel.setFont(new Font("Inter", Font.BOLD, 16));
        appearanceLabel.setForeground(PRIMARY_COLOR);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        settingsPanel.add(appearanceLabel, gbc);
        
        // Dark mode checkbox
        JCheckBox darkModeCheck = new JCheckBox("Dark Mode", darkMode);
        darkModeCheck.setBackground(CARD_BACKGROUND);
        darkModeCheck.setForeground(TEXT_PRIMARY);
        darkModeCheck.setFont(new Font("Inter", Font.PLAIN, 14));
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        settingsPanel.add(darkModeCheck, gbc);
        
        // Font size
        JLabel fontSizeLabel = new JLabel("Font Size:");
        fontSizeLabel.setForeground(TEXT_PRIMARY);
        fontSizeLabel.setFont(new Font("Inter", Font.PLAIN, 14));
        gbc.gridx = 0; gbc.gridy = 2;
        settingsPanel.add(fontSizeLabel, gbc);
        
        String[] fontSizes = {"Small", "Medium", "Large"};
        JComboBox<String> fontSizeCombo = new JComboBox<>(fontSizes);
        fontSizeCombo.setSelectedItem(fontSize.substring(0, 1).toUpperCase() + fontSize.substring(1));
        fontSizeCombo.setBackground(new Color(51, 65, 85));
        fontSizeCombo.setForeground(TEXT_PRIMARY);
        fontSizeCombo.setFont(new Font("Inter", Font.PLAIN, 14));
        gbc.gridx = 1; gbc.gridy = 2;
        settingsPanel.add(fontSizeCombo, gbc);
        
        // Audio section
        JLabel audioLabel = new JLabel("🔊 Audio");
        audioLabel.setFont(new Font("Inter", Font.BOLD, 16));
        audioLabel.setForeground(PRIMARY_COLOR);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        settingsPanel.add(audioLabel, gbc);
        
        // Sound checkbox
        JCheckBox soundCheck = new JCheckBox("Sound Effects", soundEnabled);
        soundCheck.setBackground(CARD_BACKGROUND);
        soundCheck.setForeground(TEXT_PRIMARY);
        soundCheck.setFont(new Font("Inter", Font.PLAIN, 14));
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1;
        settingsPanel.add(soundCheck, gbc);
        
        // Notifications section
        JLabel notificationsLabel = new JLabel("🔔 Notifications");
        notificationsLabel.setFont(new Font("Inter", Font.BOLD, 16));
        notificationsLabel.setForeground(PRIMARY_COLOR);
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        settingsPanel.add(notificationsLabel, gbc);
        
        // Notifications checkbox
        JCheckBox notificationsCheck = new JCheckBox("Push Notifications", notificationsEnabled);
        notificationsCheck.setBackground(CARD_BACKGROUND);
        notificationsCheck.setForeground(TEXT_PRIMARY);
        notificationsCheck.setFont(new Font("Inter", Font.PLAIN, 14));
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 1;
        settingsPanel.add(notificationsCheck, gbc);
        
        mainPanel.add(settingsPanel, BorderLayout.CENTER);
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(CARD_BACKGROUND);
        
        JButton saveButton = new JButton("💾 Save Settings");
        saveButton.setBackground(PRIMARY_COLOR);
        saveButton.setForeground(Color.WHITE);
        saveButton.setBorder(null);
        saveButton.setFont(new Font("Inter", Font.BOLD, 14));
        saveButton.setPreferredSize(new Dimension(150, 40));
        saveButton.addActionListener(e -> {
            darkMode = darkModeCheck.isSelected();
            soundEnabled = soundCheck.isSelected();
            notificationsEnabled = notificationsCheck.isSelected();
            fontSize = fontSizeCombo.getSelectedItem().toString().toLowerCase();
            saveSettings();
            JOptionPane.showMessageDialog(settingsDialog, "Settings saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            settingsDialog.dispose();
        });
        
        JButton cancelButton = new JButton("❌ Cancel");
        cancelButton.setBackground(new Color(51, 65, 85));
        cancelButton.setForeground(TEXT_PRIMARY);
        cancelButton.setBorder(new LineBorder(BORDER_COLOR, 1));
        cancelButton.setFont(new Font("Inter", Font.BOLD, 14));
        cancelButton.setPreferredSize(new Dimension(150, 40));
        cancelButton.addActionListener(e -> settingsDialog.dispose());
        
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        settingsDialog.add(mainPanel);
        settingsDialog.setVisible(true);
    }
    
    private void showProfileDialog() {
        JDialog profileDialog = new JDialog(this, "Profile Settings", true);
        profileDialog.setSize(600, 500);
        profileDialog.setLocationRelativeTo(this);
        profileDialog.getContentPane().setBackground(CARD_BACKGROUND);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(CARD_BACKGROUND);
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Title
        JLabel titleLabel = new JLabel("👤 Profile Settings");
        titleLabel.setFont(new Font("Inter", Font.BOLD, 20));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // Profile content panel
        JPanel profilePanel = new JPanel(new GridBagLayout());
        profilePanel.setBackground(CARD_BACKGROUND);
        profilePanel.setBorder(new EmptyBorder(20, 0, 20, 0));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Current profile display
        JLabel currentProfileLabel = new JLabel("📋 Current Profile");
        currentProfileLabel.setFont(new Font("Inter", Font.BOLD, 16));
        currentProfileLabel.setForeground(PRIMARY_COLOR);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        profilePanel.add(currentProfileLabel, gbc);
        
        // Current profile info
        JPanel currentProfilePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        currentProfilePanel.setBackground(new Color(51, 65, 85));
        currentProfilePanel.setBorder(new LineBorder(BORDER_COLOR, 1));
        currentProfilePanel.setPreferredSize(new Dimension(500, 80));
        
        JLabel currentAvatarLabel = new JLabel("👤");
        currentAvatarLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        currentProfilePanel.add(currentAvatarLabel);
        
        JLabel currentNameLabel = new JLabel("Name: " + currentUser);
        currentNameLabel.setFont(new Font("Inter", Font.BOLD, 14));
        currentNameLabel.setForeground(TEXT_PRIMARY);
        currentProfilePanel.add(currentNameLabel);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        profilePanel.add(currentProfilePanel, gbc);
        
        // Name input section
        JLabel nameLabel = new JLabel("✏️ Display Name:");
        nameLabel.setFont(new Font("Inter", Font.BOLD, 14));
        nameLabel.setForeground(TEXT_PRIMARY);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        profilePanel.add(nameLabel, gbc);
        
        JTextField nameField = new JTextField(currentUser, 20);
        nameField.setBackground(new Color(51, 65, 85));
        nameField.setForeground(TEXT_PRIMARY);
        nameField.setBorder(new LineBorder(BORDER_COLOR, 1));
        nameField.setFont(new Font("Inter", Font.PLAIN, 14));
        nameField.setPreferredSize(new Dimension(300, 35));
        gbc.gridx = 1; gbc.gridy = 2;
        profilePanel.add(nameField, gbc);
        
        // Avatar selection section
        JLabel avatarLabel = new JLabel("🖼️ Profile Picture:");
        avatarLabel.setFont(new Font("Inter", Font.BOLD, 14));
        avatarLabel.setForeground(TEXT_PRIMARY);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        profilePanel.add(avatarLabel, gbc);
        
        // Avatar selection panel
        JPanel avatarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        avatarPanel.setBackground(CARD_BACKGROUND);
        
        String[] avatarOptions = {
            "👤", "👨", "👩", "🧑", "👦", "👧", "👴", "👵", 
            "🤵", "👰", "👨‍💼", "👩‍💼", "👨‍🎓", "👩‍🎓", "👨‍🔬", "👩‍🔬",
            "👨‍💻", "👩‍💻", "👨‍🎨", "👩‍🎨", "👨‍🚀", "👩‍🚀", "👨‍✈️", "👩‍✈️",
            "🦸", "🦹", "🧙", "🧚", "🧛", "🧜", "🧝", "🧞"
        };
        
        JComboBox<String> avatarCombo = new JComboBox<>(avatarOptions);
        avatarCombo.setBackground(new Color(51, 65, 85));
        avatarCombo.setForeground(TEXT_PRIMARY);
        avatarCombo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        avatarCombo.setPreferredSize(new Dimension(100, 35));
        avatarPanel.add(avatarCombo);
        
        JLabel selectedAvatarPreview = new JLabel("👤");
        selectedAvatarPreview.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        selectedAvatarPreview.setBorder(new LineBorder(BORDER_COLOR, 1));
        selectedAvatarPreview.setPreferredSize(new Dimension(60, 60));
        selectedAvatarPreview.setHorizontalAlignment(SwingConstants.CENTER);
        selectedAvatarPreview.setVerticalAlignment(SwingConstants.CENTER);
        avatarPanel.add(selectedAvatarPreview);
        
        // Update preview when selection changes
        avatarCombo.addActionListener(e -> {
            selectedAvatarPreview.setText(avatarCombo.getSelectedItem().toString());
        });
        
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        profilePanel.add(avatarPanel, gbc);
        
        // Theme selection
        JLabel themeLabel = new JLabel("🎨 Theme Color:");
        themeLabel.setFont(new Font("Inter", Font.BOLD, 14));
        themeLabel.setForeground(TEXT_PRIMARY);
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 1;
        profilePanel.add(themeLabel, gbc);
        
        String[] themes = {"Blue", "Purple", "Green", "Orange", "Red", "Pink"};
        JComboBox<String> themeCombo = new JComboBox<>(themes);
        themeCombo.setBackground(new Color(51, 65, 85));
        themeCombo.setForeground(TEXT_PRIMARY);
        themeCombo.setFont(new Font("Inter", Font.PLAIN, 14));
        themeCombo.setPreferredSize(new Dimension(150, 35));
        gbc.gridx = 1; gbc.gridy = 5;
        profilePanel.add(themeCombo, gbc);
        
        mainPanel.add(profilePanel, BorderLayout.CENTER);
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(CARD_BACKGROUND);
        
        JButton saveButton = new JButton("💾 Save Profile");
        saveButton.setBackground(PRIMARY_COLOR);
        saveButton.setForeground(Color.WHITE);
        saveButton.setBorder(null);
        saveButton.setFont(new Font("Inter", Font.BOLD, 14));
        saveButton.setPreferredSize(new Dimension(150, 40));
        saveButton.addActionListener(e -> {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) {
                JOptionPane.showMessageDialog(profileDialog, "Please enter a valid name!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            currentUser = newName;
            // Save avatar and theme preferences
            saveSettings();
            JOptionPane.showMessageDialog(profileDialog, "Profile updated successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            profileDialog.dispose();
        });
        
        JButton cancelButton = new JButton("❌ Cancel");
        cancelButton.setBackground(new Color(51, 65, 85));
        cancelButton.setForeground(TEXT_PRIMARY);
        cancelButton.setBorder(new LineBorder(BORDER_COLOR, 1));
        cancelButton.setFont(new Font("Inter", Font.BOLD, 14));
        cancelButton.setPreferredSize(new Dimension(150, 40));
        cancelButton.addActionListener(e -> profileDialog.dispose());
        
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        profileDialog.add(mainPanel);
        profileDialog.setVisible(true);
    }
    
    private void showConnectionDialog() {
        JDialog connectionDialog = new JDialog(this, "Connection Information", true);
        connectionDialog.setSize(500, 300);
        connectionDialog.setLocationRelativeTo(this);
        connectionDialog.getContentPane().setBackground(CARD_BACKGROUND);
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CARD_BACKGROUND);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JTextArea infoArea = new JTextArea();
        infoArea.setBackground(new Color(51, 65, 85));
        infoArea.setForeground(TEXT_PRIMARY);
        infoArea.setBorder(new LineBorder(BORDER_COLOR, 1));
        infoArea.setEditable(false);
        infoArea.setFont(new Font("Monaco", Font.PLAIN, 12));
        
        String connectionInfo = "AlphaChat Desktop Connection Information\n\n" +
                "Server IP: " + networkIP + "\n" +
                "Port: " + port + "\n" +
                "Status: " + (isServerRunning ? "Running" : "Stopped") + "\n\n" +
                "To connect from your phone:\n" +
                "1. Make sure your phone is on the same WiFi network\n" +
                "2. Open a web browser on your phone\n" +
                "3. Go to: http://" + networkIP + ":" + port + "\n" +
                "4. Start chatting!\n\n" +
                "QR Code: Use the QR code generator in the web interface\n" +
                "to easily connect your phone.";
        
        infoArea.setText(connectionInfo);
        
        JScrollPane scrollPane = new JScrollPane(infoArea);
        scrollPane.setBorder(null);
        
        JButton closeButton = new JButton("Close");
        closeButton.setBackground(PRIMARY_COLOR);
        closeButton.setForeground(Color.WHITE);
        closeButton.setBorder(null);
        closeButton.addActionListener(e -> connectionDialog.dispose());
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(CARD_BACKGROUND);
        buttonPanel.add(closeButton);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        connectionDialog.add(panel);
        connectionDialog.setVisible(true);
    }
    
    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
                "AlphaChat Desktop v1.0\n\n" +
                "A modern desktop application for phone-to-desktop messaging.\n" +
                "Built with Java Swing for cross-platform compatibility.\n\n" +
                "Features:\n" +
                "• Real-time messaging\n" +
                "• Modern dark theme\n" +
                "• Emoji support\n" +
                "• Customizable settings\n" +
                "• Easy phone connection",
                "About AlphaChat Desktop",
                JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void loadSettings() {
        // Load settings from properties file or use defaults
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("alphachat.properties")) {
            props.load(fis);
            darkMode = Boolean.parseBoolean(props.getProperty("darkMode", "true"));
            soundEnabled = Boolean.parseBoolean(props.getProperty("soundEnabled", "true"));
            notificationsEnabled = Boolean.parseBoolean(props.getProperty("notificationsEnabled", "true"));
            fontSize = props.getProperty("fontSize", "medium");
            currentUser = props.getProperty("currentUser", "Desktop");
        } catch (IOException e) {
            // Use defaults if file doesn't exist
        }
    }
    
    private void saveSettings() {
        Properties props = new Properties();
        props.setProperty("darkMode", String.valueOf(darkMode));
        props.setProperty("soundEnabled", String.valueOf(soundEnabled));
        props.setProperty("notificationsEnabled", String.valueOf(notificationsEnabled));
        props.setProperty("fontSize", fontSize);
        props.setProperty("currentUser", currentUser);
        
        try (FileOutputStream fos = new FileOutputStream("alphachat.properties")) {
            props.store(fos, "AlphaChat Desktop Settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        // Set system look and feel
        try {
            // UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Enable anti-aliasing
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        
        SwingUtilities.invokeLater(() -> {
            new AlphaChatDesktop().setVisible(true);
        });
    }
}
