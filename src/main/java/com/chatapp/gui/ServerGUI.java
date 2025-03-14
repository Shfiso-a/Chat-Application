package com.chatapp.gui;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class ServerGUI extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(ServerGUI.class.getName());
    
    private JTextArea logTextArea;
    private JList<String> clientList;
    private DefaultListModel<String> clientListModel;
    
    public ServerGUI(String hostAddress, int port) {
        try {
            UIManager.setLookAndFeel(new FlatArcDarkIJTheme());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        setTitle("Chat Server");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        
        initComponents();
        
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                SwingUtilities.invokeLater(() -> {
                    logTextArea.append(record.getMessage() + "\n");
                    logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
                });
            }
            
            @Override
            public void flush() {
            }
            
            @Override
            public void close() throws SecurityException {
            }
        });
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                LOGGER.info("Server shutting down...");
                System.exit(0);
            }
        });
        
        LOGGER.info("Server running at " + hostAddress + ":" + port);
    }
    
    private void initComponents() {
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        clientListModel = new DefaultListModel<>();
        clientList = new JList<>(clientListModel);
        clientList.setCellRenderer(new ClientListCellRenderer());
        
        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(contentPane);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.7);
        contentPane.add(splitPane, BorderLayout.CENTER);
        
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Server Log"));
        logPanel.add(new JScrollPane(logTextArea), BorderLayout.CENTER);
        splitPane.setLeftComponent(logPanel);
        
        JPanel clientPanel = new JPanel(new BorderLayout());
        clientPanel.setBorder(BorderFactory.createTitledBorder("Connected Clients"));
        clientPanel.add(new JScrollPane(clientList), BorderLayout.CENTER);
        splitPane.setRightComponent(clientPanel);
        
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel statusLabel = new JLabel("Server running");
        statusLabel.setForeground(Color.GREEN.darker());
        statusPanel.add(statusLabel);
        contentPane.add(statusPanel, BorderLayout.SOUTH);
    }
    
    public void addClient(String clientInfo) {
        SwingUtilities.invokeLater(() -> {
            clientListModel.addElement(clientInfo);
        });
    }
    
    public void removeClient(String clientInfo) {
        SwingUtilities.invokeLater(() -> {
            clientListModel.removeElement(clientInfo);
        });
    }
    
    private static class ClientListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setIcon(new ImageIcon(getClass().getResource("/icons/user.png")));
            return label;
        }
    }
}