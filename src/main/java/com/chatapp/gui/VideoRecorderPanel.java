package com.chatapp.gui;

import com.chatapp.client.ChatClientImpl;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VideoRecorderPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(VideoRecorderPanel.class.getName());
    
    private final ChatClientImpl chatClient;
    private final String recipientId;
    private JPanel videoPreviewPanel;
    private JButton startButton;
    private JButton stopButton;
    private JButton sendButton;
    private JButton cancelButton;
    private JLabel statusLabel;
    private JLabel timerLabel;
    private Timer recordingTimer;
    
    private boolean isRecording = false;
    private int recordingDurationSecs = 0;
    private byte[] videoData;
    private byte[] thumbnailData;
    
    private Timer webcamSimulationTimer;
    private BufferedImage currentFrame;
    
    public VideoRecorderPanel(ChatClientImpl chatClient, String recipientId) {
        this.chatClient = chatClient;
        this.recipientId = recipientId;
        
        setLayout(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(60, 63, 65));
        
        initComponents();
        initWebcamSimulation();
    }
    
    private void initComponents() {
        videoPreviewPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (currentFrame != null) {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.drawImage(currentFrame, 0, 0, getWidth(), getHeight(), null);
                }
            }
        };
        videoPreviewPanel.setPreferredSize(new Dimension(320, 240));
        videoPreviewPanel.setBackground(Color.BLACK);
        
        startButton = new JButton("Start Recording");
        startButton.setForeground(Color.WHITE);
        startButton.setBackground(new Color(61, 157, 232));
        
        stopButton = new JButton("Stop");
        stopButton.setForeground(Color.WHITE);
        stopButton.setBackground(new Color(232, 61, 61));
        stopButton.setEnabled(false);
        
        sendButton = new JButton("Send");
        sendButton.setForeground(Color.WHITE);
        sendButton.setBackground(new Color(61, 232, 91));
        sendButton.setEnabled(false);
        
        cancelButton = new JButton("Cancel");
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setBackground(new Color(150, 150, 150));
        
        JPanel buttonPanel = new JPanel(new GridLayout(1, 4, 5, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(sendButton);
        buttonPanel.add(cancelButton);
        
        statusLabel = new JLabel("Ready to record video");
        statusLabel.setForeground(Color.WHITE);
        
        timerLabel = new JLabel("0:00");
        timerLabel.setForeground(Color.RED);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JPanel statusPanel = new JPanel(new BorderLayout(5, 5));
        statusPanel.setOpaque(false);
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(timerLabel, BorderLayout.EAST);
        
        JPanel mainPanel = new JPanel(new BorderLayout(5, 10));
        mainPanel.setOpaque(false);
        mainPanel.add(videoPreviewPanel, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.NORTH);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        startButton.addActionListener(e -> startRecording());
        stopButton.addActionListener(e -> stopRecording());
        sendButton.addActionListener(e -> sendVideoMessage());
        cancelButton.addActionListener(e -> {
            if (isRecording) {
                stopRecording();
            }
            clearRecording();
            SwingUtilities.getWindowAncestor(this).dispose();
        });
    }
    
    private void initWebcamSimulation() {
        webcamSimulationTimer = new Timer(100, e -> {
            if (currentFrame == null) {
                currentFrame = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
            }
            
            Graphics2D g2d = currentFrame.createGraphics();
            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, currentFrame.getWidth(), currentFrame.getHeight());
            
            g2d.setColor(Color.WHITE);
            g2d.drawString("Webcam Simulation", 10, 20);
            g2d.drawString(isRecording ? "RECORDING" : "PREVIEW", 10, 40);
            if (isRecording) {
                g2d.setColor(Color.RED);
                g2d.fillOval(300, 10, 10, 10);
            }
            
            long time = System.currentTimeMillis() / 100;
            int x = (int) (Math.sin(time * 0.1) * 100 + 160);
            int y = (int) (Math.cos(time * 0.1) * 100 + 120);
            g2d.setColor(new Color(61, 157, 232));
            g2d.fillOval(x - 5, y - 5, 10, 10);
            
            g2d.dispose();
            videoPreviewPanel.repaint();
        });
        webcamSimulationTimer.start();
    }
    
    private void startRecording() {
        isRecording = true;
        recordingDurationSecs = 0;
        
        ByteArrayOutputStream videoBuffer = new ByteArrayOutputStream();
        AtomicReference<BufferedImage> thumbnailFrame = new AtomicReference<>();
        
        recordingTimer = new Timer(1000, e -> {
            recordingDurationSecs++;
            timerLabel.setText(formatDuration(recordingDurationSecs));
            
            try {
                ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
                ImageIO.write(currentFrame, "jpg", frameBuffer);
                videoBuffer.write(frameBuffer.toByteArray());
                
                if (recordingDurationSecs == 1) {
                    thumbnailFrame.set(currentFrame);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Error capturing frame", ex);
            }
            
            if (recordingDurationSecs >= 30) {
                stopRecording();
            }
        });
        recordingTimer.start();
        
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        sendButton.setEnabled(false);
        statusLabel.setText("Recording video...");
        
        try {
            videoData = videoBuffer.toByteArray();
            
            if (thumbnailFrame.get() != null) {
                ByteArrayOutputStream thumbnailBuffer = new ByteArrayOutputStream();
                ImageIO.write(thumbnailFrame.get(), "jpg", thumbnailBuffer);
                thumbnailData = thumbnailBuffer.toByteArray();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error in video capture", ex);
        }
    }
    
    private void stopRecording() {
        if (!isRecording) return;
        
        isRecording = false;
        
        if (recordingTimer != null && recordingTimer.isRunning()) {
            recordingTimer.stop();
        }
        
        if (thumbnailData == null) {
            try {
                ByteArrayOutputStream thumbnailBuffer = new ByteArrayOutputStream();
                ImageIO.write(currentFrame, "jpg", thumbnailBuffer);
                thumbnailData = thumbnailBuffer.toByteArray();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Error capturing thumbnail", ex);
            }
        }
        
        startButton.setEnabled(false);
        stopButton.setEnabled(false);
        sendButton.setEnabled(true);
        statusLabel.setText("Recording stopped. Press Send to send the video or Cancel to discard it.");
    }
    
    private void sendVideoMessage() {
        if (thumbnailData == null || recordingDurationSecs == 0) {
            JOptionPane.showMessageDialog(this,
                    "No video recording available to send.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (recipientId == null) {
            JOptionPane.showMessageDialog(this,
                    "No recipient selected. Please select a user to send the video to.",
                    "No Recipient",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        try {
            ByteArrayOutputStream fakeVideoBuffer = new ByteArrayOutputStream();
            ImageIO.write(currentFrame, "jpg", fakeVideoBuffer);
            byte[] actualVideoData = fakeVideoBuffer.toByteArray();
            
            chatClient.sendVideoMessage(
                    actualVideoData,
                    thumbnailData,
                    recordingDurationSecs,
                    recipientId
            );
            
            clearRecording();
            
            JOptionPane.showMessageDialog(this,
                    "Video message sent successfully.",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE);
            
            SwingUtilities.getWindowAncestor(this).dispose();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error sending video message", e);
            JOptionPane.showMessageDialog(this,
                    "Error sending video message: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void clearRecording() {
        videoData = null;
        thumbnailData = null;
        recordingDurationSecs = 0;
        
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        sendButton.setEnabled(false);
        statusLabel.setText("Ready to record video");
        timerLabel.setText("0:00");
    }
    
    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    @Override
    public void removeNotify() {
        super.removeNotify();
        
        if (webcamSimulationTimer != null && webcamSimulationTimer.isRunning()) {
            webcamSimulationTimer.stop();
        }
        
        if (recordingTimer != null && recordingTimer.isRunning()) {
            recordingTimer.stop();
        }
    }
    
    public static void showRecorder(Component parent, ChatClientImpl chatClient, String recipientId) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Video Message");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setModal(true);
        
        VideoRecorderPanel recorderPanel = new VideoRecorderPanel(chatClient, recipientId);
        dialog.getContentPane().add(recorderPanel);
        
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
} 