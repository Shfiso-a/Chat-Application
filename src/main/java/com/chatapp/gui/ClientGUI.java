package com.chatapp.gui;

import com.chatapp.client.ChatClientImpl;
import com.chatapp.common.ClientInfo;
import com.chatapp.common.Message;
import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientGUI extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(ClientGUI.class.getName());
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    
    private final ChatClientImpl chatClient;
    
    private JTextPane chatTextPane;
    private JTextPane inputTextPane;
    private JButton sendButton;
    private JList<ClientInfo> clientList;
    private DefaultListModel<ClientInfo> clientListModel;
    private JPanel conversationPanel;
    private CardLayout conversationCardLayout;
    private String activeRecipientId;
    
    // New UI components for reply feature
    private JPanel replyPanel;
    private JLabel replyLabel;
    private JButton cancelReplyButton;
    private Map<String, JPanel> messageBubbles = new HashMap<>();
    
    // Rich text formatting components
    private JPanel formattingToolbar;
    private JButton boldButton;
    private JButton italicButton;
    private JButton underlineButton;
    private JComboBox<String> fontSizeComboBox;
    private JComboBox<String> fontFamilyComboBox;
    private JButton colorButton;
    private Color currentTextColor = Color.WHITE;
    
    // File sharing components
    private JButton attachButton;
    private File currentAttachment;
    private JPanel attachmentPanel;
    private JLabel attachmentLabel;
    private JButton removeAttachmentButton;
    
    private static final Color SYSTEM_COLOR = new Color(153, 170, 181);
    private static final Color NOTIFICATION_COLOR = new Color(162, 194, 250);
    private static final Color MY_MESSAGE_COLOR = new Color(61, 157, 232);
    private static final Color OTHERS_MESSAGE_COLOR = new Color(104, 104, 104);
    private static final Color REPLY_PANEL_COLOR = new Color(32, 35, 39);
    
    // Status indicator icons (using unicode for simplicity)
    private static final String SENT_ICON = "✓";
    private static final String DELIVERED_ICON = "✓✓";
    private static final String READ_ICON = "✓✓";  // We'll color this differently
    
    public ClientGUI(ChatClientImpl chatClient) {
        this.chatClient = chatClient;
        
        try {
            UIManager.setLookAndFeel(new FlatArcDarkIJTheme());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        setTitle("Telegram-Style Chat - " + chatClient.getClientInfo().getName());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        
        initComponents();
        
        chatClient.addMessageListener(new ChatClientImpl.MessageListener() {
            @Override
            public void messageReceived(Message message) {
                handleMessage(message);
            }
            
            @Override
            public void messageStatusUpdated(String messageId, Message.MessageStatus status) {
                updateMessageStatus(messageId, status);
            }
        });
        
        chatClient.addClientStatusListener(new ChatClientImpl.ClientStatusListener() {
            @Override
            public void clientConnected(ClientInfo clientInfo) {
                addClient(clientInfo);
            }
            
            @Override
            public void clientDisconnected(String clientId) {
                removeClient(clientId);
            }
        });
        
        // Add profile listener to handle profile updates from other users
        chatClient.addProfileListener(updatedClientInfo -> {
            SwingUtilities.invokeLater(() -> {
                // Update the client in the list if it exists
                for (int i = 0; i < clientListModel.size(); i++) {
                    ClientInfo existingInfo = clientListModel.getElementAt(i);
                    if (existingInfo.getId().equals(updatedClientInfo.getId())) {
                        clientListModel.setElementAt(updatedClientInfo, i);
                        break;
                    }
                }
                // Refresh the client list display
                clientList.repaint();
            });
        });
        
        try {
            List<ClientInfo> onlineClients = chatClient.getOnlineClients();
            for (ClientInfo clientInfo : onlineClients) {
                if (!clientInfo.getId().equals(chatClient.getClientId())) {
                    addClient(clientInfo);
                }
            }
        } catch (RemoteException e) {
            LOGGER.log(Level.WARNING, "Error getting online clients", e);
        }
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    chatClient.disconnect();
                } catch (RemoteException ex) {
                    LOGGER.log(Level.WARNING, "Error disconnecting from server", ex);
                }
            }
        });
    }
    
    private void initComponents() {
        chatTextPane = new JTextPane();
        chatTextPane.setEditable(false);
        
        // Replace messageField with rich text input
        inputTextPane = new JTextPane();
        inputTextPane.setBackground(new Color(60, 63, 65));
        inputTextPane.setForeground(Color.WHITE);
        inputTextPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        
        // Initialize attachment panel early
        attachmentPanel = new JPanel(new BorderLayout());
        attachmentPanel.setBackground(new Color(60, 63, 65));
        attachmentPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Color.DARK_GRAY));
        attachmentPanel.setVisible(false);
        
        attachmentLabel = new JLabel("No file selected");
        attachmentLabel.setForeground(Color.WHITE);
        attachmentLabel.setBorder(new EmptyBorder(5, 10, 5, 0));
        
        removeAttachmentButton = new JButton("×");
        removeAttachmentButton.setForeground(Color.LIGHT_GRAY);
        removeAttachmentButton.setBackground(new Color(60, 63, 65));
        removeAttachmentButton.setBorderPainted(false);
        removeAttachmentButton.setFocusPainted(false);
        removeAttachmentButton.addActionListener(e -> removeAttachment());
        
        attachmentPanel.add(attachmentLabel, BorderLayout.CENTER);
        attachmentPanel.add(removeAttachmentButton, BorderLayout.EAST);
        
        // Create formatting toolbar
        createFormattingToolbar();
        
        sendButton = new JButton("Send");
        
        // Initialize reply panel
        replyPanel = new JPanel(new BorderLayout());
        replyPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Color.DARK_GRAY));
        replyPanel.setBackground(REPLY_PANEL_COLOR);
        replyPanel.setVisible(false);
        
        replyLabel = new JLabel("Replying to a message");
        replyLabel.setForeground(Color.LIGHT_GRAY);
        replyLabel.setBorder(new EmptyBorder(5, 10, 5, 0));
        
        cancelReplyButton = new JButton("×");
        cancelReplyButton.setForeground(Color.LIGHT_GRAY);
        cancelReplyButton.setBackground(REPLY_PANEL_COLOR);
        cancelReplyButton.setBorderPainted(false);
        cancelReplyButton.setFocusPainted(false);
        cancelReplyButton.addActionListener(e -> {
            chatClient.cancelReply();
            replyPanel.setVisible(false);
        });
        
        replyPanel.add(replyLabel, BorderLayout.CENTER);
        replyPanel.add(cancelReplyButton, BorderLayout.EAST);
        
        clientListModel = new DefaultListModel<>();
        clientList = new JList<>(clientListModel);
        clientList.setCellRenderer(new ClientListCellRenderer());
        
        conversationCardLayout = new CardLayout();
        conversationPanel = new JPanel(conversationCardLayout);
        
        JPanel contentPane = new JPanel(new BorderLayout(0, 0));
        setContentPane(contentPane);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(250);
        splitPane.setDividerSize(1);
        splitPane.setBorder(null);
        contentPane.add(splitPane, BorderLayout.CENTER);
        
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(new Color(42, 45, 50));
        leftPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.DARK_GRAY));
        
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        searchPanel.setBackground(new Color(42, 45, 50));
        
        JTextField searchField = new JTextField("Search");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        searchPanel.add(searchField, BorderLayout.CENTER);
        
        leftPanel.add(searchPanel, BorderLayout.NORTH);
        
        JScrollPane clientScrollPane = new JScrollPane(clientList);
        clientScrollPane.setBorder(null);
        leftPanel.add(clientScrollPane, BorderLayout.CENTER);
        
        splitPane.setLeftComponent(leftPanel);
        
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(new Color(48, 51, 56));
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(42, 45, 50));
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.DARK_GRAY));
        headerPanel.setPreferredSize(new Dimension(0, 50));
        
        JLabel headerLabel = new JLabel("Group Chat");
        headerLabel.setForeground(Color.WHITE);
        headerLabel.setBorder(new EmptyBorder(0, 15, 0, 0));
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(headerLabel, BorderLayout.CENTER);
        
        // Add profile button to header panel
        JButton profileButton = new JButton("Profile");
        profileButton.setForeground(Color.WHITE);
        profileButton.setBackground(new Color(72, 75, 80));
        profileButton.setBorderPainted(false);
        profileButton.setFocusPainted(false);
        profileButton.setToolTipText("Edit your profile");
        profileButton.addActionListener(e -> showProfilePanel());
        
        headerPanel.add(profileButton, BorderLayout.EAST);
        
        rightPanel.add(headerPanel, BorderLayout.NORTH);
        
        conversationPanel.setBackground(new Color(48, 51, 56));
        rightPanel.add(conversationPanel, BorderLayout.CENTER);
        
        JPanel groupChatPanel = new JPanel(new BorderLayout());
        groupChatPanel.setBackground(new Color(48, 51, 56));
        
        JScrollPane chatScrollPane = new JScrollPane(chatTextPane);
        chatScrollPane.setBorder(null);
        groupChatPanel.add(chatScrollPane, BorderLayout.CENTER);
        
        conversationPanel.add(groupChatPanel, "group");
        conversationCardLayout.show(conversationPanel, "group");
        
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setBackground(new Color(42, 45, 50));
        messagePanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.DARK_GRAY));
        
        // Add reply panel to message panel
        messagePanel.add(replyPanel, BorderLayout.NORTH);
        
        // Add attachment panel below reply panel
        JPanel topPanels = new JPanel(new BorderLayout());
        topPanels.setOpaque(false);
        topPanels.add(replyPanel, BorderLayout.NORTH);
        topPanels.add(attachmentPanel, BorderLayout.SOUTH);
        
        messagePanel.add(topPanels, BorderLayout.NORTH);
        
        // Add formatting toolbar
        messagePanel.add(formattingToolbar, BorderLayout.CENTER);
        
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        inputPanel.setBackground(new Color(42, 45, 50));
        
        // Use inputTextPane instead of messageField
        JScrollPane inputScrollPane = new JScrollPane(inputTextPane);
        inputScrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        inputScrollPane.setPreferredSize(new Dimension(0, 80));
        
        sendButton.setBackground(new Color(61, 157, 232));
        sendButton.setForeground(Color.WHITE);
        sendButton.setBorderPainted(false);
        
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        messagePanel.add(inputPanel, BorderLayout.SOUTH);
        
        rightPanel.add(messagePanel, BorderLayout.SOUTH);
        
        splitPane.setRightComponent(rightPanel);
        
        sendButton.addActionListener(e -> sendMessage());
        
        inputTextPane.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendMessage();
                }
            }
        });
        
        clientList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ClientInfo selectedClient = clientList.getSelectedValue();
                if (selectedClient != null) {
                    activeRecipientId = selectedClient.getId();
                    headerLabel.setText(selectedClient.getName());
                } else {
                    activeRecipientId = null;
                    headerLabel.setText("Group Chat");
                }
            }
        });
        
        // Add mouse listener to chat pane for right-click menu
        chatTextPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(e);
                }
            }
        });
    }
    
    private void handleRightClick(MouseEvent e) {
        int position = chatTextPane.viewToModel2D(e.getPoint());
        AttributeSet attributes = ((StyledDocument) chatTextPane.getDocument()).getCharacterElement(position).getAttributes();
        
        if (attributes.getAttribute(StyleConstants.ComponentAttribute) != null) {
            Component component = (Component) attributes.getAttribute(StyleConstants.ComponentAttribute);
            
            if (component instanceof JPanel) {
                String messageId = null;
                
                // Find the message ID associated with this component
                for (Map.Entry<String, JPanel> entry : messageBubbles.entrySet()) {
                    if (entry.getValue() == component || component.getParent() == entry.getValue()) {
                        messageId = entry.getKey();
                        break;
                    }
                }
                
                if (messageId != null) {
                    showMessagePopupMenu(messageId, e.getX(), e.getY());
                }
            }
        }
    }
    
    private void showMessagePopupMenu(String messageId, int x, int y) {
        JPopupMenu popupMenu = new JPopupMenu();
        
        JMenuItem replyItem = new JMenuItem("Reply");
        replyItem.addActionListener(e -> {
            setReplyToMessage(messageId);
        });
        
        popupMenu.add(replyItem);
        popupMenu.show(chatTextPane, x, y);
    }
    
    private void setReplyToMessage(String messageId) {
        Message message = chatClient.getMessageById(messageId);
        if (message != null) {
            chatClient.setReplyToMessage(messageId);
            replyLabel.setText("Replying to: " + message.getSenderName() + " - " + 
                    (message.getContent().length() > 30 ? 
                            message.getContent().substring(0, 27) + "..." : 
                            message.getContent()));
            replyPanel.setVisible(true);
            inputTextPane.requestFocus();
        }
    }
    
    private void handleMessage(Message message) {
        SwingUtilities.invokeLater(() -> {
            appendMessage(message);
        });
    }
    
    private void updateMessageStatus(String messageId, Message.MessageStatus status) {
        SwingUtilities.invokeLater(() -> {
            JPanel messageBubble = messageBubbles.get(messageId);
            if (messageBubble != null) {
                // Find the status label in the message bubble
                updateStatusLabel(messageBubble, status);
            }
        });
    }
    
    private void updateStatusLabel(JPanel messageBubble, Message.MessageStatus status) {
        // Recursively search for the status label in the component hierarchy
        for (Component component : messageBubble.getComponents()) {
            if (component instanceof JPanel) {
                JPanel panel = (JPanel) component;
                updateStatusLabelInPanel(panel, status);
            }
        }
    }
    
    private void updateStatusLabelInPanel(JPanel panel, Message.MessageStatus status) {
        for (Component innerComponent : panel.getComponents()) {
            if (innerComponent instanceof JLabel) {
                JLabel label = (JLabel) innerComponent;
                if ("status_indicator".equals(label.getName()) || "statusLabel".equals(label.getName())) {
                    updateStatusIcon(label, status);
                    return;
                }
            }
            if (innerComponent instanceof JPanel) {
                updateStatusLabelInPanel((JPanel) innerComponent, status);
            }
        }
    }
    
    private void updateStatusIcon(JLabel statusLabel, Message.MessageStatus status) {
        switch (status) {
            case SENT:
                statusLabel.setText(SENT_ICON);
                statusLabel.setForeground(Color.LIGHT_GRAY);
                break;
            case DELIVERED:
                statusLabel.setText(DELIVERED_ICON);
                statusLabel.setForeground(Color.LIGHT_GRAY);
                break;
            case READ:
                statusLabel.setText(READ_ICON);
                statusLabel.setForeground(new Color(52, 152, 219)); // Blue color for read
                break;
        }
    }
    
    private void appendMessage(Message message) {
        StyledDocument doc = chatTextPane.getStyledDocument();
        
        try {
            Style timestampStyle = chatTextPane.addStyle("Timestamp", null);
            StyleConstants.setForeground(timestampStyle, Color.GRAY);
            StyleConstants.setFontSize(timestampStyle, 10);
            
            Style nameStyle = chatTextPane.addStyle("Name", null);
            StyleConstants.setBold(nameStyle, true);
            
            Style messageStyle = chatTextPane.addStyle("Message", null);
            
            Style systemStyle = chatTextPane.addStyle("System", null);
            StyleConstants.setForeground(systemStyle, SYSTEM_COLOR);
            StyleConstants.setItalic(systemStyle, true);
            
            Style notificationStyle = chatTextPane.addStyle("Notification", null);
            StyleConstants.setForeground(notificationStyle, NOTIFICATION_COLOR);
            StyleConstants.setItalic(notificationStyle, true);
            
            String timestamp = message.getTimestamp().format(TIME_FORMATTER);
            
            switch (message.getType()) {
                case SYSTEM:
                    doc.insertString(doc.getLength(), "[" + timestamp + "] ", timestampStyle);
                    doc.insertString(doc.getLength(), message.getContent() + "\n", systemStyle);
                    break;
                    
                case NOTIFICATION:
                    doc.insertString(doc.getLength(), "[" + timestamp + "] ", timestampStyle);
                    doc.insertString(doc.getLength(), message.getContent() + "\n", notificationStyle);
                    break;
                    
                case FILE:
                    boolean isMyMessage = message.getSenderId().equals(chatClient.getClientId());
                    JPanel bubblePanel = createFileBubble(message, isMyMessage);
                    messageBubbles.put(message.getMessageId(), bubblePanel);
                    
                    doc.insertString(doc.getLength(), "\n", null);
                    StyleConstants.setComponent(messageStyle, bubblePanel);
                    doc.insertString(doc.getLength(), " ", messageStyle);
                    doc.insertString(doc.getLength(), "\n", null);
                    break;
                    
                case VOICE:
                    isMyMessage = message.getSenderId().equals(chatClient.getClientId());
                    bubblePanel = createVoiceBubble(message, isMyMessage);
                    messageBubbles.put(message.getMessageId(), bubblePanel);
                    
                    doc.insertString(doc.getLength(), "\n", null);
                    StyleConstants.setComponent(messageStyle, bubblePanel);
                    doc.insertString(doc.getLength(), " ", messageStyle);
                    doc.insertString(doc.getLength(), "\n", null);
                    break;
                    
                case VIDEO:
                    isMyMessage = message.getSenderId().equals(chatClient.getClientId());
                    bubblePanel = createVideoBubble(message, isMyMessage);
                    messageBubbles.put(message.getMessageId(), bubblePanel);
                    
                    doc.insertString(doc.getLength(), "\n", null);
                    StyleConstants.setComponent(messageStyle, bubblePanel);
                    doc.insertString(doc.getLength(), " ", messageStyle);
                    doc.insertString(doc.getLength(), "\n", null);
                    break;
                    
                case TEXT:
                    isMyMessage = message.getSenderId().equals(chatClient.getClientId());
                    
                    bubblePanel = createMessageBubble(message, isMyMessage);
                    messageBubbles.put(message.getMessageId(), bubblePanel);
                    
                    doc.insertString(doc.getLength(), "\n", null);
                    StyleConstants.setComponent(messageStyle, bubblePanel);
                    doc.insertString(doc.getLength(), " ", messageStyle);
                    doc.insertString(doc.getLength(), "\n", null);
                    break;
                    
                default:
                    // Handle other message types later
                    break;
            }
            
            chatTextPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            LOGGER.log(Level.SEVERE, "Error appending message", e);
        }
    }
    
    private JPanel createMessageBubble(Message message, boolean isMyMessage) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setOpaque(false);
        
        JPanel bubblePanel = new JPanel(new BorderLayout(5, 5));
        bubblePanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        bubblePanel.setBackground(isMyMessage ? MY_MESSAGE_COLOR : OTHERS_MESSAGE_COLOR);
        
        JLabel nameLabel = new JLabel(message.getSenderName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(Color.WHITE);
        
        // Add reply information if this is a reply
        JPanel contentPanel = new JPanel(new BorderLayout(5, 5));
        contentPanel.setOpaque(false);
        
        if (message.getReplyToMessageId() != null) {
            Message replyToMessage = chatClient.getMessageById(message.getReplyToMessageId());
            JPanel replyInfoPanel = new JPanel(new BorderLayout());
            replyInfoPanel.setOpaque(false);
            replyInfoPanel.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, Color.LIGHT_GRAY));
            
            String replyText = replyToMessage != null ? 
                    replyToMessage.getContent() : "Message unavailable";
            String replySender = replyToMessage != null ? 
                    replyToMessage.getSenderName() : "Unknown";
            
            if (replyText.length() > 30) {
                replyText = replyText.substring(0, 27) + "...";
            }
            
            JLabel replyLabel = new JLabel("<html><b>Reply to " + replySender + ":</b> " + replyText + "</html>");
            replyLabel.setForeground(Color.LIGHT_GRAY);
            replyLabel.setFont(replyLabel.getFont().deriveFont(10.0f));
            replyLabel.setBorder(new EmptyBorder(0, 5, 5, 0));
            
            replyInfoPanel.add(replyLabel, BorderLayout.CENTER);
            contentPanel.add(replyInfoPanel, BorderLayout.NORTH);
        }
        
        // Check if message contains HTML formatting
        String content = message.getContent();
        JComponent contentComponent;
        
        if (content.startsWith("<html>") && content.endsWith("</html>")) {
            JEditorPane editorPane = new JEditorPane("text/html", content);
            editorPane.setEditable(false);
            editorPane.setOpaque(false);
            editorPane.setForeground(Color.WHITE);
            editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            
            contentComponent = editorPane;
        } else {
            JLabel contentLabel = new JLabel("<html><p style='width: 200px'>" + content + "</p></html>");
            contentLabel.setForeground(Color.WHITE);
            contentComponent = contentLabel;
        }
        
        contentPanel.add(contentComponent, BorderLayout.CENTER);
        
        JLabel timeLabel = new JLabel(message.getTimestamp().format(TIME_FORMATTER));
        timeLabel.setFont(timeLabel.getFont().deriveFont(9f));
        timeLabel.setForeground(new Color(220, 220, 220));
        
        // Add status indicator for messages sent by this client
        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        timePanel.setOpaque(false);
        
        timePanel.add(timeLabel);
        
        if (isMyMessage) {
            JLabel statusLabel = new JLabel(SENT_ICON);
            statusLabel.setName("status_indicator");
            statusLabel.setFont(statusLabel.getFont().deriveFont(9f));
            statusLabel.setForeground(Color.LIGHT_GRAY);
            
            // Set initial status based on message status if available
            if (message.getStatus() != null) {
                updateStatusIcon(statusLabel, message.getStatus());
            }
            
            timePanel.add(statusLabel);
        }
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(nameLabel, BorderLayout.WEST);
        headerPanel.add(timePanel, BorderLayout.EAST);
        
        bubblePanel.add(headerPanel, BorderLayout.NORTH);
        bubblePanel.add(contentPanel, BorderLayout.CENTER);
        
        if (isMyMessage) {
            panel.add(Box.createHorizontalGlue(), BorderLayout.WEST);
            panel.add(bubblePanel, BorderLayout.EAST);
        } else {
            panel.add(bubblePanel, BorderLayout.WEST);
            panel.add(Box.createHorizontalGlue(), BorderLayout.EAST);
        }
        
        return panel;
    }
    
    private void sendMessage() {
        if ((inputTextPane.getDocument().getLength() > 0 || currentAttachment != null)) {
            try {
                // Convert formatted text to HTML
                String formattedContent = getFormattedText();
                
                // If there's an attachment, handle it
                if (currentAttachment != null) {
                    // Check if a recipient is selected
                    if (activeRecipientId == null) {
                        if (!promptForRecipientSelection()) {
                            return;  // User cancelled the operation
                        }
                    }
                    sendFileMessage(formattedContent);
                } else {
                    // For regular messages, we can send to group if no recipient is selected
                    chatClient.sendMessage(formattedContent, activeRecipientId);
                }
                
                inputTextPane.setText("");
                if (chatClient.isReplyingToMessage()) {
                    replyPanel.setVisible(false);
                }
                removeAttachment();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error sending message", e);
                JOptionPane.showMessageDialog(this, "Error sending message: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private boolean promptForRecipientSelection() {
        if (clientListModel.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No online users available to send the file to.",
                "No Recipients Available",
                JOptionPane.WARNING_MESSAGE);
            return false;
        }
        
        // Create a list of client names for the dialog
        Object[] possibleValues = new Object[clientListModel.getSize()];
        for (int i = 0; i < clientListModel.getSize(); i++) {
            possibleValues[i] = clientListModel.getElementAt(i).getName();
        }
        
        Object selectedValue = JOptionPane.showInputDialog(
            this,
            "Please select a recipient for your file:",
            "Select Recipient",
            JOptionPane.QUESTION_MESSAGE,
            null,
            possibleValues,
            possibleValues[0]);
        
        if (selectedValue == null) {
            return false;  // User cancelled the dialog
        }
        
        // Find the corresponding ClientInfo and set as active recipient
        for (int i = 0; i < clientListModel.getSize(); i++) {
            ClientInfo clientInfo = clientListModel.getElementAt(i);
            if (clientInfo.getName().equals(selectedValue)) {
                activeRecipientId = clientInfo.getId();
                clientList.setSelectedValue(clientInfo, true);
                return true;
            }
        }
        
        return false;
    }
    
    private String getFormattedText() {
        StyledDocument doc = inputTextPane.getStyledDocument();
        StringBuffer html = new StringBuffer("<html>");
        
        try {
            for (int i = 0; i < doc.getLength(); i++) {
                Element element = doc.getCharacterElement(i);
                AttributeSet attrs = element.getAttributes();
                
                String text = doc.getText(i, 1);
                
                if (text.equals("\n")) {
                    html.append("<br>");
                    continue;
                }
                
                // Check for special characters
                if (text.equals("<")) {
                    text = "&lt;";
                } else if (text.equals(">")) {
                    text = "&gt;";
                } else if (text.equals("&")) {
                    text = "&amp;";
                } else if (text.equals(" ")) {
                    text = "&nbsp;";
                }
                
                // Start building the span with style attributes
                html.append("<span style=\"");
                
                // Add font family
                String fontFamily = StyleConstants.getFontFamily(attrs);
                if (fontFamily != null && !fontFamily.isEmpty()) {
                    html.append("font-family:").append(fontFamily).append(";");
                }
                
                // Add font size
                int fontSize = StyleConstants.getFontSize(attrs);
                html.append("font-size:").append(fontSize).append("pt;");
                
                // Add font color
                Color color = StyleConstants.getForeground(attrs);
                if (color != null) {
                    html.append("color:rgb(").append(color.getRed()).append(",")
                           .append(color.getGreen()).append(",")
                           .append(color.getBlue()).append(");");
                }
                
                // Add bold
                if (StyleConstants.isBold(attrs)) {
                    html.append("font-weight:bold;");
                }
                
                // Add italic
                if (StyleConstants.isItalic(attrs)) {
                    html.append("font-style:italic;");
                }
                
                // Add underline
                if (StyleConstants.isUnderline(attrs)) {
                    html.append("text-decoration:underline;");
                }
                
                html.append("\">");
                html.append(text);
                html.append("</span>");
            }
            
            html.append("</html>");
        } catch (BadLocationException e) {
            LOGGER.log(Level.SEVERE, "Error formatting text", e);
            return inputTextPane.getText();
        }
        
        return html.toString();
    }
    
    private void addClient(ClientInfo clientInfo) {
        if (clientInfo.getId().equals(chatClient.getClientId())) {
            return;
        }
        
        for (int i = 0; i < clientListModel.size(); i++) {
            if (clientListModel.getElementAt(i).getId().equals(clientInfo.getId())) {
                return;
            }
        }
        
        SwingUtilities.invokeLater(() -> {
            clientListModel.addElement(clientInfo);
        });
    }
    
    private void removeClient(String clientId) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < clientListModel.size(); i++) {
                if (clientListModel.getElementAt(i).getId().equals(clientId)) {
                    clientListModel.remove(i);
                    break;
                }
            }
            
            if (activeRecipientId != null && activeRecipientId.equals(clientId)) {
                activeRecipientId = null;
            }
        });
    }
    
    private static class ClientListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof ClientInfo) {
                ClientInfo clientInfo = (ClientInfo) value;
                
                // Create a panel to hold the user info
                JPanel panel = new JPanel(new BorderLayout(5, 0));
                panel.setOpaque(true);
                
                if (isSelected) {
                    panel.setBackground(new Color(61, 127, 182));
                } else {
                    panel.setBackground(new Color(42, 45, 50));
                }
                
                // Profile picture or placeholder
                JLabel avatarLabel = new JLabel();
                avatarLabel.setPreferredSize(new Dimension(30, 30));
                
                if (clientInfo.getProfilePicture() != null) {
                    try {
                        ImageIcon icon = new ImageIcon(clientInfo.getProfilePicture());
                        Image img = icon.getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH);
                        avatarLabel.setIcon(new ImageIcon(img));
                    } catch (Exception e) {
                        // Use first letter avatar as fallback
                        avatarLabel.setText(clientInfo.getName().substring(0, 1).toUpperCase());
                        avatarLabel.setHorizontalAlignment(SwingConstants.CENTER);
                        avatarLabel.setVerticalAlignment(SwingConstants.CENTER);
                        avatarLabel.setForeground(Color.WHITE);
                        avatarLabel.setBackground(new Color(61, 157, 232));
                        avatarLabel.setOpaque(true);
                    }
                } else {
                    // Use first letter avatar
                    avatarLabel.setText(clientInfo.getName().substring(0, 1).toUpperCase());
                    avatarLabel.setHorizontalAlignment(SwingConstants.CENTER);
                    avatarLabel.setVerticalAlignment(SwingConstants.CENTER);
                    avatarLabel.setForeground(Color.WHITE);
                    
                    // Generate color based on name
                    int hash = clientInfo.getName().hashCode();
                    Color avatarColor = new Color(
                            Math.abs(hash) % 200 + 55,
                            Math.abs(hash / 256) % 200 + 55,
                            Math.abs(hash / 65536) % 200 + 55
                    );
                    avatarLabel.setBackground(avatarColor);
                    avatarLabel.setOpaque(true);
                }
                
                // Make avatar circular
                avatarLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
                
                // User info
                JPanel infoPanel = new JPanel(new GridLayout(2, 1));
                infoPanel.setOpaque(false);
                
                JLabel nameLabel = new JLabel(clientInfo.getName());
                nameLabel.setForeground(isSelected ? Color.WHITE : Color.LIGHT_GRAY);
                nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
                
                JLabel statusLabel = new JLabel(clientInfo.getDisplayStatus());
                statusLabel.setForeground(isSelected ? Color.WHITE : Color.GRAY);
                statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 10));
                
                infoPanel.add(nameLabel);
                infoPanel.add(statusLabel);
                
                panel.add(avatarLabel, BorderLayout.WEST);
                panel.add(infoPanel, BorderLayout.CENTER);
                
                // Status indicator
                JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                statusPanel.setOpaque(false);
                
                JLabel statusIndicator = new JLabel("●");
                
                // Set status color
                if (clientInfo.isOnline()) {
                    switch (clientInfo.getPresenceStatus()) {
                        case AVAILABLE:
                            statusIndicator.setForeground(new Color(76, 217, 100)); // Green
                            break;
                        case AWAY:
                            statusIndicator.setForeground(new Color(255, 204, 0)); // Yellow
                            break;
                        case BUSY:
                            statusIndicator.setForeground(new Color(255, 59, 48)); // Red
                            break;
                        case INVISIBLE:
                            statusIndicator.setForeground(new Color(142, 142, 147)); // Gray
                            break;
                    }
                } else {
                    statusIndicator.setForeground(new Color(142, 142, 147)); // Gray
                }
                
                statusPanel.add(statusIndicator);
                panel.add(statusPanel, BorderLayout.EAST);
                
                panel.setBorder(new EmptyBorder(5, 10, 5, 10));
                
                return panel;
            }
            
            return label;
        }
    }
    
    private void createFormattingToolbar() {
        formattingToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        formattingToolbar.setBackground(new Color(50, 53, 58));
        
        boldButton = createToolbarButton("B", Font.BOLD, "Bold");
        italicButton = createToolbarButton("I", Font.ITALIC, "Italic");
        underlineButton = createToolbarButton("U", Font.PLAIN, "Underline");
        
        fontSizeComboBox = new JComboBox<>(new String[]{"10", "12", "14", "16", "18", "20", "24"});
        fontSizeComboBox.setSelectedItem("14");
        fontSizeComboBox.setToolTipText("Font Size");
        fontSizeComboBox.addActionListener(e -> applyFontSize());
        
        String[] fontFamilies = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        fontFamilyComboBox = new JComboBox<>(fontFamilies);
        fontFamilyComboBox.setSelectedItem("Arial");
        fontFamilyComboBox.setToolTipText("Font Family");
        fontFamilyComboBox.addActionListener(e -> applyFontFamily());
        
        colorButton = new JButton("A");
        colorButton.setFont(new Font("Arial", Font.BOLD, 12));
        colorButton.setForeground(currentTextColor);
        colorButton.setBackground(new Color(60, 63, 65));
        colorButton.setToolTipText("Text Color");
        colorButton.addActionListener(e -> chooseColor());
        
        // Create attachment button
        attachButton = new JButton("📎");
        attachButton.setFont(new Font("Arial", Font.PLAIN, 16));
        attachButton.setForeground(Color.WHITE);
        attachButton.setBackground(new Color(60, 63, 65));
        attachButton.setToolTipText("Attach File");
        attachButton.addActionListener(e -> chooseFile());
        
        // Add voice and video message buttons to formatting toolbar
        JButton voiceMessageButton = new JButton("🎤");
        voiceMessageButton.setFont(new Font("Arial", Font.PLAIN, 16));
        voiceMessageButton.setForeground(Color.WHITE);
        voiceMessageButton.setBackground(new Color(60, 63, 65));
        voiceMessageButton.setToolTipText("Send Voice Message");
        voiceMessageButton.addActionListener(e -> showVoiceRecorder());
        
        JButton videoMessageButton = new JButton("📹");
        videoMessageButton.setFont(new Font("Arial", Font.PLAIN, 16));
        videoMessageButton.setForeground(Color.WHITE);
        videoMessageButton.setBackground(new Color(60, 63, 65));
        videoMessageButton.setToolTipText("Send Video Message");
        videoMessageButton.addActionListener(e -> showVideoRecorder());
        
        formattingToolbar.add(boldButton);
        formattingToolbar.add(italicButton);
        formattingToolbar.add(underlineButton);
        formattingToolbar.add(fontSizeComboBox);
        formattingToolbar.add(fontFamilyComboBox);
        formattingToolbar.add(colorButton);
        formattingToolbar.add(Box.createHorizontalStrut(20));
        formattingToolbar.add(attachButton);
        formattingToolbar.add(Box.createHorizontalStrut(10));
        formattingToolbar.add(voiceMessageButton);
        formattingToolbar.add(videoMessageButton);
    }
    
    private JButton createToolbarButton(String text, int style, String toolTip) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", style, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(60, 63, 65));
        button.setToolTipText(toolTip);
        
        if ("U".equals(text)) {
            button.addActionListener(e -> toggleUnderline());
        } else {
            button.addActionListener(e -> toggleStyle(style));
        }
        
        return button;
    }
    
    private void toggleStyle(int style) {
        StyledDocument doc = inputTextPane.getStyledDocument();
        MutableAttributeSet attributes = new SimpleAttributeSet();
        
        int selectionStart = inputTextPane.getSelectionStart();
        int selectionEnd = inputTextPane.getSelectionEnd();
        
        AttributeSet currentAttrs = doc.getCharacterElement(selectionStart).getAttributes();
        
        boolean hasStyle = StyleConstants.isBold(currentAttrs) && style == Font.BOLD ||
                StyleConstants.isItalic(currentAttrs) && style == Font.ITALIC;
        
        if (style == Font.BOLD) {
            StyleConstants.setBold(attributes, !hasStyle);
        } else if (style == Font.ITALIC) {
            StyleConstants.setItalic(attributes, !hasStyle);
        }
        
        doc.setCharacterAttributes(selectionStart, selectionEnd - selectionStart, attributes, false);
        inputTextPane.requestFocus();
    }
    
    private void toggleUnderline() {
        StyledDocument doc = inputTextPane.getStyledDocument();
        MutableAttributeSet attributes = new SimpleAttributeSet();
        
        int selectionStart = inputTextPane.getSelectionStart();
        int selectionEnd = inputTextPane.getSelectionEnd();
        
        AttributeSet currentAttrs = doc.getCharacterElement(selectionStart).getAttributes();
        boolean hasUnderline = StyleConstants.isUnderline(currentAttrs);
        
        StyleConstants.setUnderline(attributes, !hasUnderline);
        
        doc.setCharacterAttributes(selectionStart, selectionEnd - selectionStart, attributes, false);
        inputTextPane.requestFocus();
    }
    
    private void applyFontSize() {
        String size = (String) fontSizeComboBox.getSelectedItem();
        StyledDocument doc = inputTextPane.getStyledDocument();
        MutableAttributeSet attributes = new SimpleAttributeSet();
        
        StyleConstants.setFontSize(attributes, Integer.parseInt(size));
        
        int selectionStart = inputTextPane.getSelectionStart();
        int selectionEnd = inputTextPane.getSelectionEnd();
        
        doc.setCharacterAttributes(selectionStart, selectionEnd - selectionStart, attributes, false);
        inputTextPane.requestFocus();
    }
    
    private void applyFontFamily() {
        String family = (String) fontFamilyComboBox.getSelectedItem();
        StyledDocument doc = inputTextPane.getStyledDocument();
        MutableAttributeSet attributes = new SimpleAttributeSet();
        
        StyleConstants.setFontFamily(attributes, family);
        
        int selectionStart = inputTextPane.getSelectionStart();
        int selectionEnd = inputTextPane.getSelectionEnd();
        
        doc.setCharacterAttributes(selectionStart, selectionEnd - selectionStart, attributes, false);
        inputTextPane.requestFocus();
    }
    
    private void chooseColor() {
        Color newColor = JColorChooser.showDialog(this, "Choose Text Color", currentTextColor);
        if (newColor != null) {
            currentTextColor = newColor;
            colorButton.setForeground(currentTextColor);
            
            StyledDocument doc = inputTextPane.getStyledDocument();
            MutableAttributeSet attributes = new SimpleAttributeSet();
            
            StyleConstants.setForeground(attributes, currentTextColor);
            
            int selectionStart = inputTextPane.getSelectionStart();
            int selectionEnd = inputTextPane.getSelectionEnd();
            
            doc.setCharacterAttributes(selectionStart, selectionEnd - selectionStart, attributes, false);
            inputTextPane.requestFocus();
        }
    }
    
    private void chooseFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select File to Share");
        
        int result = fileChooser.showOpenDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            
            // Check file size (limit to 10MB for example)
            if (selectedFile.length() > 10 * 1024 * 1024) {
                JOptionPane.showMessageDialog(this, 
                        "File size exceeds 10MB limit.", 
                        "File Too Large", 
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            currentAttachment = selectedFile;
            attachmentLabel.setText("Attachment: " + selectedFile.getName());
            attachmentPanel.setVisible(true);
        }
    }
    
    private void removeAttachment() {
        currentAttachment = null;
        attachmentPanel.setVisible(false);
    }
    
    private void sendFileMessage(String messageText) throws IOException, RemoteException {
        try {
            // Check if file still exists
            if (currentAttachment == null || !currentAttachment.exists()) {
                throw new IOException("The selected file no longer exists or is not accessible.");
            }
            
            // Read file content as bytes
            byte[] fileBytes = Files.readAllBytes(currentAttachment.toPath());
            
            // Encode file content
            String encodedFile = Base64.getEncoder().encodeToString(fileBytes);
            
            // Create file transfer message
            Message.FileAttachment attachment = new Message.FileAttachment(
                    currentAttachment.getName(),
                    encodedFile,
                    currentAttachment.length(),
                    Files.probeContentType(currentAttachment.toPath()));
            
            // Send message with file attachment
            if (messageText.isEmpty()) {
                messageText = "Sent a file: " + currentAttachment.getName();
            }
            
            // Ensure we have a recipient
            if (activeRecipientId == null) {
                throw new IOException("No recipient selected. Please select a user to send the file to.");
            }
            
            chatClient.sendFileMessage(messageText, attachment, activeRecipientId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error sending file", e);
            throw new IOException("Error sending file: " + e.getMessage(), e);
        }
    }
    
    private JPanel createFileBubble(Message message, boolean isMyMessage) {
        Message.FileAttachment attachment = message.getFileAttachment();
        
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        panel.setBackground(isMyMessage ? MY_MESSAGE_COLOR : OTHERS_MESSAGE_COLOR);
        
        // File icon and info panel
        JPanel fileInfoPanel = new JPanel(new BorderLayout(10, 0));
        fileInfoPanel.setOpaque(false);
        
        // File icon with appropriate image based on file type
        String contentType = attachment.getContentType();
        ImageIcon icon;
        
        if (contentType != null) {
            if (contentType.startsWith("image/")) {
                icon = new ImageIcon(getClass().getResource("/icons/image_file.png"));
            } else if (contentType.startsWith("audio/")) {
                icon = new ImageIcon(getClass().getResource("/icons/audio_file.png"));
            } else if (contentType.startsWith("video/")) {
                icon = new ImageIcon(getClass().getResource("/icons/video_file.png"));
            } else if (contentType.startsWith("text/")) {
                icon = new ImageIcon(getClass().getResource("/icons/text_file.png"));
            } else if (contentType.contains("pdf")) {
                icon = new ImageIcon(getClass().getResource("/icons/pdf_file.png"));
            } else {
                icon = new ImageIcon(getClass().getResource("/icons/generic_file.png"));
            }
        } else {
            icon = new ImageIcon(getClass().getResource("/icons/generic_file.png"));
        }
        
        // If icon doesn't exist or fails to load, use a default text label
        JLabel iconLabel;
        if (icon.getIconWidth() == -1) {
            iconLabel = new JLabel("📄");
            iconLabel.setFont(new Font("Arial", Font.PLAIN, 24));
        } else {
            iconLabel = new JLabel(icon);
        }
        
        // File information panel
        JPanel fileDetailsPanel = new JPanel(new GridLayout(2, 1));
        fileDetailsPanel.setOpaque(false);
        
        JLabel fileNameLabel = new JLabel(attachment.getFileName());
        fileNameLabel.setForeground(Color.WHITE);
        fileNameLabel.setFont(new Font("Arial", Font.BOLD, 12));
        
        JLabel fileSizeLabel = new JLabel(formatFileSize(attachment.getFileSize()));
        fileSizeLabel.setForeground(new Color(200, 200, 200));
        fileSizeLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        
        fileDetailsPanel.add(fileNameLabel);
        fileDetailsPanel.add(fileSizeLabel);
        
        fileInfoPanel.add(iconLabel, BorderLayout.WEST);
        fileInfoPanel.add(fileDetailsPanel, BorderLayout.CENTER);
        
        // Download button
        JButton downloadButton = new JButton("Download");
        downloadButton.setForeground(Color.WHITE);
        downloadButton.setBackground(new Color(60, 63, 65));
        downloadButton.setBorderPainted(false);
        downloadButton.setFocusPainted(false);
        downloadButton.addActionListener(e -> downloadFile(attachment));
        
        // Display file source info (client or server storage)
        JLabel sourceLabel = new JLabel(
            attachment.isStoredOnServer() ? "Stored on server" : "Attached file"
        );
        sourceLabel.setForeground(new Color(200, 200, 200));
        sourceLabel.setFont(new Font("Arial", Font.ITALIC, 10));
        
        JPanel buttonPanel = new JPanel(new BorderLayout(5, 2));
        buttonPanel.setOpaque(false);
        buttonPanel.add(downloadButton, BorderLayout.CENTER);
        buttonPanel.add(sourceLabel, BorderLayout.SOUTH);
        
        // Add message content if any
        if (message.getContent() != null && !message.getContent().equals("Sent a file: " + attachment.getFileName())) {
            JTextArea contentArea = new JTextArea(message.getContent());
            contentArea.setOpaque(false);
            contentArea.setForeground(Color.WHITE);
            contentArea.setWrapStyleWord(true);
            contentArea.setLineWrap(true);
            contentArea.setEditable(false);
            
            panel.add(contentArea, BorderLayout.NORTH);
        }
        
        // Add the file info and button panel to the main panel
        JPanel centerPanel = new JPanel(new BorderLayout(10, 0));
        centerPanel.setOpaque(false);
        centerPanel.add(fileInfoPanel, BorderLayout.CENTER);
        centerPanel.add(buttonPanel, BorderLayout.EAST);
        
        panel.add(centerPanel, BorderLayout.CENTER);
        
        // Add sender info and timestamp if it's not my message
        if (!isMyMessage) {
            JLabel senderLabel = new JLabel(message.getSenderName());
            senderLabel.setForeground(new Color(200, 255, 200));
            senderLabel.setFont(new Font("Arial", Font.BOLD, 10));
            
            panel.add(senderLabel, BorderLayout.NORTH);
        }
        
        // Add timestamp
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        
        JLabel timeLabel = new JLabel(message.getTimestamp().format(TIME_FORMATTER));
        timeLabel.setForeground(new Color(200, 200, 200));
        timeLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        
        bottomPanel.add(timeLabel, BorderLayout.EAST);
        
        // Add status indicator for my messages
        if (isMyMessage) {
            JLabel statusLabel = new JLabel(SENT_ICON);
            statusLabel.setName("statusLabel");
            statusLabel.setForeground(new Color(200, 200, 200));
            statusLabel.setFont(new Font("Arial", Font.PLAIN, 10));
            
            updateStatusIcon(statusLabel, message.getStatus());
            
            bottomPanel.add(statusLabel, BorderLayout.WEST);
        }
        
        panel.add(bottomPanel, BorderLayout.SOUTH);
        
        // Store reference to this message bubble
        messageBubbles.put(message.getMessageId(), panel);
        
        return panel;
    }
    
    private String formatFileSize(long size) {
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int unitIndex = 0;
        double unitValue = size;
        
        while (unitValue > 1024 && unitIndex < units.length - 1) {
            unitValue /= 1024;
            unitIndex++;
        }
        
        return String.format("%.1f %s", unitValue, units[unitIndex]);
    }
    
    private void downloadFile(Message.FileAttachment fileAttachment) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save File");
        fileChooser.setSelectedFile(new File(fileAttachment.getFileName()));
        
        int userSelection = fileChooser.showSaveDialog(this);
        
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            
            try {
                byte[] fileData;
                
                // Check if file is stored on server
                if (fileAttachment.isStoredOnServer() && fileAttachment.getFileId() != null) {
                    // Retrieve file from server
                    String encodedContent = chatClient.getChatService().getFileContent(fileAttachment.getFileId());
                    if (encodedContent == null) {
                        throw new IOException("File not found on server");
                    }
                    fileData = Base64.getDecoder().decode(encodedContent);
                } else {
                    // Use the encoded content directly
                    fileData = Base64.getDecoder().decode(fileAttachment.getEncodedContent());
                }
                
                // Write to file
                try (FileOutputStream outputStream = new FileOutputStream(fileToSave)) {
                    outputStream.write(fileData);
                }
                
                JOptionPane.showMessageDialog(this,
                        "File downloaded successfully",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error downloading file", e);
                JOptionPane.showMessageDialog(this,
                        "Error downloading file: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void showProfilePanel() {
        JDialog profileDialog = new JDialog(this, "Your Profile", true);
        profileDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        ProfilePanel profilePanel = new ProfilePanel(chatClient);
        profileDialog.getContentPane().add(profilePanel);
        
        profileDialog.pack();
        profileDialog.setSize(450, 550);
        profileDialog.setLocationRelativeTo(this);
        profileDialog.setVisible(true);
    }
    
    private void showVoiceRecorder() {
        VoiceRecorderPanel.showRecorder(this, chatClient, activeRecipientId);
    }
    
    private void showVideoRecorder() {
        VideoRecorderPanel.showRecorder(this, chatClient, activeRecipientId);
    }
    
    private JPanel createVoiceBubble(Message message, boolean isMyMessage) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setOpaque(false);
        
        JPanel bubblePanel = new JPanel(new BorderLayout(5, 5));
        bubblePanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        bubblePanel.setBackground(isMyMessage ? MY_MESSAGE_COLOR : OTHERS_MESSAGE_COLOR);
        
        JLabel nameLabel = new JLabel(message.getSenderName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(Color.WHITE);
        
        JPanel contentPanel = new JPanel(new BorderLayout(5, 5));
        contentPanel.setOpaque(false);
        
        if (message.getReplyToMessageId() != null) {
            // Add reply info (similar to createMessageBubble)
            Message replyToMessage = chatClient.getMessageById(message.getReplyToMessageId());
            JPanel replyInfoPanel = new JPanel(new BorderLayout());
            replyInfoPanel.setOpaque(false);
            replyInfoPanel.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, Color.LIGHT_GRAY));
            
            String replyText = replyToMessage != null ? 
                    replyToMessage.getContent() : "Message unavailable";
            String replySender = replyToMessage != null ? 
                    replyToMessage.getSenderName() : "Unknown";
            
            if (replyText.length() > 30) {
                replyText = replyText.substring(0, 27) + "...";
            }
            
            JLabel replyLabel = new JLabel("<html><b>Reply to " + replySender + ":</b> " + replyText + "</html>");
            replyLabel.setForeground(Color.LIGHT_GRAY);
            replyLabel.setFont(replyLabel.getFont().deriveFont(10.0f));
            replyLabel.setBorder(new EmptyBorder(0, 5, 5, 0));
            
            replyInfoPanel.add(replyLabel, BorderLayout.CENTER);
            contentPanel.add(replyInfoPanel, BorderLayout.NORTH);
        }
        
        // Create voice message player panel
        JPanel voicePanel = new JPanel(new BorderLayout(10, 0));
        voicePanel.setOpaque(false);
        
        JLabel voiceIcon = new JLabel("🎤");
        voiceIcon.setFont(new Font("Arial", Font.PLAIN, 24));
        voiceIcon.setForeground(Color.WHITE);
        
        Message.VoiceAttachment voiceAttachment = message.getVoiceAttachment();
        
        JLabel durationLabel = new JLabel(voiceAttachment.getFormattedDuration());
        durationLabel.setForeground(Color.WHITE);
        
        JButton playButton = new JButton("▶");
        playButton.setBackground(new Color(61, 157, 232));
        playButton.setForeground(Color.WHITE);
        playButton.setBorderPainted(false);
        playButton.setFocusPainted(false);
        playButton.setToolTipText("Play voice message");
        playButton.addActionListener(e -> playVoiceMessage(voiceAttachment));
        
        voicePanel.add(voiceIcon, BorderLayout.WEST);
        voicePanel.add(durationLabel, BorderLayout.CENTER);
        voicePanel.add(playButton, BorderLayout.EAST);
        
        contentPanel.add(voicePanel, BorderLayout.CENTER);
        
        JLabel timeLabel = new JLabel(message.getTimestamp().format(TIME_FORMATTER));
        timeLabel.setFont(timeLabel.getFont().deriveFont(9f));
        timeLabel.setForeground(new Color(220, 220, 220));
        
        // Add status indicator for messages sent by this client
        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        timePanel.setOpaque(false);
        
        timePanel.add(timeLabel);
        
        if (isMyMessage) {
            JLabel statusLabel = new JLabel(SENT_ICON);
            statusLabel.setName("statusLabel");
            statusLabel.setForeground(new Color(200, 200, 200));
            statusLabel.setFont(new Font("Arial", Font.PLAIN, 10));
            
            updateStatusIcon(statusLabel, message.getStatus());
            
            timePanel.add(statusLabel, BorderLayout.WEST);
        }
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(nameLabel, BorderLayout.WEST);
        headerPanel.add(timePanel, BorderLayout.EAST);
        
        bubblePanel.add(headerPanel, BorderLayout.NORTH);
        bubblePanel.add(contentPanel, BorderLayout.CENTER);
        
        if (isMyMessage) {
            panel.add(Box.createHorizontalGlue(), BorderLayout.WEST);
            panel.add(bubblePanel, BorderLayout.EAST);
        } else {
            panel.add(bubblePanel, BorderLayout.WEST);
            panel.add(Box.createHorizontalGlue(), BorderLayout.EAST);
        }
        
        return panel;
    }
    
    private JPanel createVideoBubble(Message message, boolean isMyMessage) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setOpaque(false);
        
        JPanel bubblePanel = new JPanel(new BorderLayout(5, 5));
        bubblePanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        bubblePanel.setBackground(isMyMessage ? MY_MESSAGE_COLOR : OTHERS_MESSAGE_COLOR);
        
        JLabel nameLabel = new JLabel(message.getSenderName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(Color.WHITE);
        
        JPanel contentPanel = new JPanel(new BorderLayout(5, 5));
        contentPanel.setOpaque(false);
        
        if (message.getReplyToMessageId() != null) {
            // Add reply info (similar to createMessageBubble)
            Message replyToMessage = chatClient.getMessageById(message.getReplyToMessageId());
            JPanel replyInfoPanel = new JPanel(new BorderLayout());
            replyInfoPanel.setOpaque(false);
            replyInfoPanel.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, Color.LIGHT_GRAY));
            
            String replyText = replyToMessage != null ? 
                    replyToMessage.getContent() : "Message unavailable";
            String replySender = replyToMessage != null ? 
                    replyToMessage.getSenderName() : "Unknown";
            
            if (replyText.length() > 30) {
                replyText = replyText.substring(0, 27) + "...";
            }
            
            JLabel replyLabel = new JLabel("<html><b>Reply to " + replySender + ":</b> " + replyText + "</html>");
            replyLabel.setForeground(Color.LIGHT_GRAY);
            replyLabel.setFont(replyLabel.getFont().deriveFont(10.0f));
            replyLabel.setBorder(new EmptyBorder(0, 5, 5, 0));
            
            replyInfoPanel.add(replyLabel, BorderLayout.CENTER);
            contentPanel.add(replyInfoPanel, BorderLayout.NORTH);
        }
        
        // Create video message panel
        JPanel videoPanel = new JPanel(new BorderLayout(10, 5));
        videoPanel.setOpaque(false);
        
        Message.VideoAttachment videoAttachment = message.getVideoAttachment();
        
        // Create thumbnail
        JLabel thumbnailLabel = new JLabel();
        thumbnailLabel.setPreferredSize(new Dimension(160, 120));
        thumbnailLabel.setHorizontalAlignment(SwingConstants.CENTER);
        thumbnailLabel.setVerticalAlignment(SwingConstants.CENTER);
        thumbnailLabel.setBackground(Color.BLACK);
        thumbnailLabel.setOpaque(true);
        
        // Add play icon overlay on thumbnail
        JPanel thumbnailPanel = new JPanel(new BorderLayout());
        thumbnailPanel.setOpaque(false);
        thumbnailPanel.add(thumbnailLabel, BorderLayout.CENTER);
        
        JLabel playIcon = new JLabel("▶");
        playIcon.setFont(new Font("Arial", Font.BOLD, 36));
        playIcon.setForeground(new Color(255, 255, 255, 180));
        playIcon.setHorizontalAlignment(SwingConstants.CENTER);
        thumbnailPanel.add(playIcon, BorderLayout.CENTER);
        
        // Set thumbnail if available
        if (videoAttachment.getThumbnailData() != null) {
            try {
                ImageIcon thumbnailIcon = new ImageIcon(videoAttachment.getThumbnailData());
                Image scaledImage = thumbnailIcon.getImage().getScaledInstance(
                        160, 120, Image.SCALE_SMOOTH);
                thumbnailLabel.setIcon(new ImageIcon(scaledImage));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error setting thumbnail", e);
            }
        }
        
        JLabel durationLabel = new JLabel(videoAttachment.getFormattedDuration());
        durationLabel.setForeground(Color.WHITE);
        
        JButton playButton = new JButton("Play Video");
        playButton.setBackground(new Color(61, 157, 232));
        playButton.setForeground(Color.WHITE);
        playButton.setBorderPainted(false);
        playButton.setFocusPainted(false);
        playButton.addActionListener(e -> playVideoMessage(videoAttachment));
        
        videoPanel.add(thumbnailPanel, BorderLayout.CENTER);
        videoPanel.add(durationLabel, BorderLayout.NORTH);
        videoPanel.add(playButton, BorderLayout.SOUTH);
        
        contentPanel.add(videoPanel, BorderLayout.CENTER);
        
        JLabel timeLabel = new JLabel(message.getTimestamp().format(TIME_FORMATTER));
        timeLabel.setFont(timeLabel.getFont().deriveFont(9f));
        timeLabel.setForeground(new Color(220, 220, 220));
        
        // Add status indicator for messages sent by this client
        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        timePanel.setOpaque(false);
        
        timePanel.add(timeLabel);
        
        if (isMyMessage) {
            JLabel statusLabel = new JLabel(SENT_ICON);
            statusLabel.setName("statusLabel");
            statusLabel.setForeground(new Color(200, 200, 200));
            statusLabel.setFont(new Font("Arial", Font.PLAIN, 10));
            
            updateStatusIcon(statusLabel, message.getStatus());
            
            timePanel.add(statusLabel, BorderLayout.WEST);
        }
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(nameLabel, BorderLayout.WEST);
        headerPanel.add(timePanel, BorderLayout.EAST);
        
        bubblePanel.add(headerPanel, BorderLayout.NORTH);
        bubblePanel.add(contentPanel, BorderLayout.CENTER);
        
        if (isMyMessage) {
            panel.add(Box.createHorizontalGlue(), BorderLayout.WEST);
            panel.add(bubblePanel, BorderLayout.EAST);
        } else {
            panel.add(bubblePanel, BorderLayout.WEST);
            panel.add(Box.createHorizontalGlue(), BorderLayout.EAST);
        }
        
        return panel;
    }
    
    private void playVoiceMessage(Message.VoiceAttachment voiceAttachment) {
        MediaPlayerDialog.playVoiceMessage(this, voiceAttachment);
    }
    
    private void playVideoMessage(Message.VideoAttachment videoAttachment) {
        MediaPlayerDialog.playVideoMessage(this, videoAttachment);
    }
}