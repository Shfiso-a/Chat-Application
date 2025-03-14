package com.chatapp.gui;

import com.chatapp.client.ChatClientImpl;
import com.chatapp.common.ClientInfo;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProfilePanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(ProfilePanel.class.getName());
    private static final int PROFILE_PIC_SIZE = 150;
    
    private final ChatClientImpl chatClient;
    private JLabel profilePictureLabel;
    private JTextField nameField;
    private JTextField emailField;
    private JTextArea statusMessageArea;
    private JComboBox<String> presenceStatusComboBox;
    private JButton uploadPictureButton;
    private JButton updateProfileButton;
    private byte[] currentProfilePicture;
    
    public ProfilePanel(ChatClientImpl chatClient) {
        this.chatClient = chatClient;
        
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(20, 20, 20, 20));
        setBackground(new Color(48, 51, 56));
        
        initComponents();
        loadProfileData();
        
        // Register as profile listener to update when changes come from server
        chatClient.addProfileListener(this::updateProfileFromServer);
    }
    
    private void initComponents() {
        // Profile picture section
        JPanel profilePicturePanel = new JPanel(new BorderLayout(5, 5));
        profilePicturePanel.setOpaque(false);
        
        profilePictureLabel = new JLabel();
        profilePictureLabel.setPreferredSize(new Dimension(PROFILE_PIC_SIZE, PROFILE_PIC_SIZE));
        profilePictureLabel.setHorizontalAlignment(SwingConstants.CENTER);
        profilePictureLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        uploadPictureButton = new JButton("Change Picture");
        uploadPictureButton.addActionListener(e -> chooseProfilePicture());
        
        profilePicturePanel.add(profilePictureLabel, BorderLayout.CENTER);
        profilePicturePanel.add(uploadPictureButton, BorderLayout.SOUTH);
        
        // Profile details section
        JPanel detailsPanel = new JPanel(new GridBagLayout());
        detailsPanel.setOpaque(false);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Name
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel nameLabel = new JLabel("Name:");
        nameLabel.setForeground(Color.WHITE);
        detailsPanel.add(nameLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        nameField = new JTextField(20);
        nameField.setEditable(false); // Name cannot be changed after registration
        detailsPanel.add(nameField, gbc);
        
        // Email
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        JLabel emailLabel = new JLabel("Email:");
        emailLabel.setForeground(Color.WHITE);
        detailsPanel.add(emailLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        emailField = new JTextField(20);
        detailsPanel.add(emailField, gbc);
        
        // Status message
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        JLabel statusLabel = new JLabel("Status:");
        statusLabel.setForeground(Color.WHITE);
        detailsPanel.add(statusLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        statusMessageArea = new JTextArea(3, 20);
        statusMessageArea.setLineWrap(true);
        statusMessageArea.setWrapStyleWord(true);
        JScrollPane statusScrollPane = new JScrollPane(statusMessageArea);
        detailsPanel.add(statusScrollPane, gbc);
        
        // Presence status
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        JLabel presenceLabel = new JLabel("Presence:");
        presenceLabel.setForeground(Color.WHITE);
        detailsPanel.add(presenceLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        presenceStatusComboBox = new JComboBox<>(new String[]{
                "Available", "Away", "Busy", "Invisible"
        });
        detailsPanel.add(presenceStatusComboBox, gbc);
        
        // Last seen info
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        JLabel lastSeenLabel = new JLabel("Last seen: Now (Online)");
        lastSeenLabel.setForeground(Color.LIGHT_GRAY);
        detailsPanel.add(lastSeenLabel, gbc);
        
        // Update button
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        updateProfileButton = new JButton("Update Profile");
        updateProfileButton.addActionListener(e -> updateProfile());
        detailsPanel.add(updateProfileButton, gbc);
        
        // Add sections to main panel
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setOpaque(false);
        northPanel.add(profilePicturePanel, BorderLayout.CENTER);
        
        add(northPanel, BorderLayout.NORTH);
        add(detailsPanel, BorderLayout.CENTER);
    }
    
    private void loadProfileData() {
        ClientInfo clientInfo = chatClient.getClientInfo();
        
        nameField.setText(clientInfo.getName());
        emailField.setText(clientInfo.getEmail() != null ? clientInfo.getEmail() : "");
        statusMessageArea.setText(clientInfo.getStatusMessage() != null ? clientInfo.getStatusMessage() : "");
        
        // Set presence status in combo box
        switch (clientInfo.getPresenceStatus()) {
            case AVAILABLE:
                presenceStatusComboBox.setSelectedIndex(0);
                break;
            case AWAY:
                presenceStatusComboBox.setSelectedIndex(1);
                break;
            case BUSY:
                presenceStatusComboBox.setSelectedIndex(2);
                break;
            case INVISIBLE:
                presenceStatusComboBox.setSelectedIndex(3);
                break;
        }
        
        // Set profile picture if available
        currentProfilePicture = clientInfo.getProfilePicture();
        if (currentProfilePicture != null) {
            updateProfilePictureLabel(currentProfilePicture);
        } else {
            // Set default profile picture
            profilePictureLabel.setIcon(createDefaultProfilePicture(clientInfo.getName(), PROFILE_PIC_SIZE));
        }
    }
    
    private Icon createDefaultProfilePicture(String name, int size) {
        // Create a default profile picture with the first letter of the name
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        // Draw circle with random background color
        int hash = name.hashCode();
        Color bgColor = new Color(
                Math.abs(hash) % 200 + 55,
                Math.abs(hash / 256) % 200 + 55,
                Math.abs(hash / 65536) % 200 + 55
        );
        
        g2d.setColor(bgColor);
        g2d.fillOval(0, 0, size, size);
        
        // Draw first letter
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, size / 2));
        
        String firstLetter = name.substring(0, 1).toUpperCase();
        FontMetrics metrics = g2d.getFontMetrics();
        int x = (size - metrics.stringWidth(firstLetter)) / 2;
        int y = ((size - metrics.getHeight()) / 2) + metrics.getAscent();
        
        g2d.drawString(firstLetter, x, y);
        g2d.dispose();
        
        return new ImageIcon(image);
    }
    
    private void updateProfilePictureLabel(byte[] imageData) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageData));
            if (img != null) {
                Image scaledImg = img.getScaledInstance(PROFILE_PIC_SIZE, PROFILE_PIC_SIZE, Image.SCALE_SMOOTH);
                profilePictureLabel.setIcon(new ImageIcon(scaledImg));
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error loading profile picture", e);
        }
    }
    
    private void chooseProfilePicture() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Profile Picture");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Image files", "jpg", "jpeg", "png", "gif"));
        
        int result = fileChooser.showOpenDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                // Read and resize image
                BufferedImage originalImage = ImageIO.read(selectedFile);
                if (originalImage != null) {
                    // Scale to a reasonable size (not too large)
                    int maxSize = 300; // Maximum width or height
                    int width = originalImage.getWidth();
                    int height = originalImage.getHeight();
                    
                    if (width > maxSize || height > maxSize) {
                        double scale = Math.min((double) maxSize / width, (double) maxSize / height);
                        int scaledWidth = (int) (width * scale);
                        int scaledHeight = (int) (height * scale);
                        
                        BufferedImage scaledImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = scaledImage.createGraphics();
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g.drawImage(originalImage, 0, 0, scaledWidth, scaledHeight, null);
                        g.dispose();
                        
                        // Convert to bytes
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(scaledImage, "png", baos);
                        currentProfilePicture = baos.toByteArray();
                    } else {
                        // No resize needed
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(originalImage, "png", baos);
                        currentProfilePicture = baos.toByteArray();
                    }
                    
                    // Update UI
                    updateProfilePictureLabel(currentProfilePicture);
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error reading image file", e);
                JOptionPane.showMessageDialog(this,
                        "Error reading image file: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void updateProfile() {
        String email = emailField.getText().trim();
        String statusMessage = statusMessageArea.getText().trim();
        
        ClientInfo.PresenceStatus presenceStatus;
        switch (presenceStatusComboBox.getSelectedIndex()) {
            case 1:
                presenceStatus = ClientInfo.PresenceStatus.AWAY;
                break;
            case 2:
                presenceStatus = ClientInfo.PresenceStatus.BUSY;
                break;
            case 3:
                presenceStatus = ClientInfo.PresenceStatus.INVISIBLE;
                break;
            default:
                presenceStatus = ClientInfo.PresenceStatus.AVAILABLE;
                break;
        }
        
        try {
            // Update profile
            chatClient.updateProfile(currentProfilePicture, statusMessage, email);
            
            // Update presence status
            chatClient.updatePresenceStatus(presenceStatus);
            
            JOptionPane.showMessageDialog(this,
                    "Profile updated successfully",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (RemoteException e) {
            LOGGER.log(Level.SEVERE, "Error updating profile", e);
            JOptionPane.showMessageDialog(this,
                    "Error updating profile: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void updateProfileFromServer(ClientInfo updatedClientInfo) {
        // Only update if this is our profile
        if (updatedClientInfo.getId().equals(chatClient.getClientId())) {
            loadProfileData();
        }
    }
} 