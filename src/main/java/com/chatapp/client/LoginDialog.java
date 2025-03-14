package com.chatapp.client;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LoginDialog extends JDialog {
    private JTextField serverHostField;
    private JTextField serverPortField;
    private JTextField usernameField;
    private JButton connectButton;
    private JButton cancelButton;
    
    public LoginDialog() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        setTitle("Connect to Chat Server");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setModal(true);
        
        initComponents();
        
        pack();
        setLocationRelativeTo(null);
    }
    
    private void initComponents() {
        JLabel serverHostLabel = new JLabel("Server Host:");
        serverHostField = new JTextField(15);
        serverHostField.setText("127.0.0.1");
        
        JLabel serverPortLabel = new JLabel("Server Port:");
        serverPortField = new JTextField(15);
        serverPortField.setText("1099");
        
        JLabel usernameLabel = new JLabel("Username:");
        usernameField = new JTextField(15);
        
        connectButton = new JButton("Connect");
        cancelButton = new JButton("Cancel");
        
        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setContentPane(contentPane);
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(serverHostLabel, gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(serverHostField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        formPanel.add(serverPortLabel, gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(serverPortField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        formPanel.add(usernameLabel, gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(usernameField, gbc);
        
        contentPane.add(formPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(connectButton);
        buttonPanel.add(cancelButton);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);
        
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                connect();
            }
        });
        
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                System.exit(0);
            }
        });
        
        getRootPane().setDefaultButton(connectButton);
    }
    
    private void connect() {
        String serverHost = serverHostField.getText().trim();
        String serverPortStr = serverPortField.getText().trim();
        String username = usernameField.getText().trim();
        
        if (serverHost.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a server host", "Error", JOptionPane.ERROR_MESSAGE);
            serverHostField.requestFocus();
            return;
        }
        
        if (serverPortStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a server port", "Error", JOptionPane.ERROR_MESSAGE);
            serverPortField.requestFocus();
            return;
        }
        
        int serverPort;
        try {
            serverPort = Integer.parseInt(serverPortStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid server port", "Error", JOptionPane.ERROR_MESSAGE);
            serverPortField.requestFocus();
            return;
        }
        
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a username", "Error", JOptionPane.ERROR_MESSAGE);
            usernameField.requestFocus();
            return;
        }
        
        ChatClientMain clientMain = new ChatClientMain();
        boolean connected = clientMain.connect(serverHost, serverPort, username);
        
        if (connected) {
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to connect to the server", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}