package com.chatapp.gui;

import com.chatapp.client.ChatClientImpl;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VoiceRecorderPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(VoiceRecorderPanel.class.getName());
    
    private final ChatClientImpl chatClient;
    private final String recipientId;
    private JButton recordButton;
    private JButton stopButton;
    private JButton sendButton;
    private JButton cancelButton;
    private JLabel statusLabel;
    private JProgressBar levelMeter;
    private javax.swing.Timer meterTimer;
    
    private boolean isRecording = false;
    private AudioFormat audioFormat;
    private TargetDataLine targetDataLine;
    private ByteArrayOutputStream audioStream;
    private Thread recordingThread;
    private long startTime;
    private int recordingDurationSecs = 0;
    
    public VoiceRecorderPanel(ChatClientImpl chatClient, String recipientId) {
        this.chatClient = chatClient;
        this.recipientId = recipientId;
        
        setLayout(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(60, 63, 65));
        
        initComponents();
    }
    
    private void initComponents() {
        recordButton = new JButton("Record");
        recordButton.setForeground(Color.WHITE);
        recordButton.setBackground(new Color(61, 157, 232));
        
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
        buttonPanel.add(recordButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(sendButton);
        buttonPanel.add(cancelButton);
        
        statusLabel = new JLabel("Ready to record");
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        levelMeter = new JProgressBar(0, 100);
        levelMeter.setStringPainted(true);
        levelMeter.setString("Audio level");
        
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setOpaque(false);
        centerPanel.add(statusLabel, BorderLayout.NORTH);
        centerPanel.add(levelMeter, BorderLayout.CENTER);
        
        add(centerPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        recordButton.addActionListener(e -> startRecording());
        stopButton.addActionListener(e -> stopRecording());
        sendButton.addActionListener(e -> sendVoiceMessage());
        cancelButton.addActionListener(e -> {
            if (isRecording) {
                stopRecording();
            }
            clearRecording();
        });
    }
    
    private void startRecording() {
        try {
            audioFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    44100.0F, 
                    16, 
                    1, 
                    2, 
                    44100.0F, 
                    false 
            );
            
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
            
            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                JOptionPane.showMessageDialog(this,
                        "Audio recording is not supported on this system.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            targetDataLine.open(audioFormat);
            targetDataLine.start();
            
            meterTimer = new javax.swing.Timer(100, e -> updateLevelMeter());
            meterTimer.start();
            
            audioStream = new ByteArrayOutputStream();
            isRecording = true;
            startTime = System.currentTimeMillis();
            
            recordButton.setEnabled(false);
            stopButton.setEnabled(true);
            sendButton.setEnabled(false);
            statusLabel.setText("Recording...");
            
            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                int bytesRead;
                
                try {
                    while (isRecording) {
                        bytesRead = targetDataLine.read(buffer, 0, buffer.length);
                        audioStream.write(buffer, 0, bytesRead);
                        
                        long elapsed = System.currentTimeMillis() - startTime;
                        recordingDurationSecs = (int) (elapsed / 1000);
                        
                        SwingUtilities.invokeLater(() -> 
                            statusLabel.setText("Recording... " + formatDuration(recordingDurationSecs)));
                        
                        if (recordingDurationSecs >= 60) {
                            SwingUtilities.invokeLater(this::stopRecording);
                            break;
                        }
                    }
                    
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error during recording", e);
                }
            });
            
            recordingThread.start();
            
        } catch (LineUnavailableException e) {
            LOGGER.log(Level.SEVERE, "Error starting recording", e);
            JOptionPane.showMessageDialog(this,
                    "Could not start recording: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void stopRecording() {
        if (!isRecording) return;
        
        isRecording = false;
        
        if (meterTimer != null && meterTimer.isRunning()) {
            meterTimer.stop();
        }
        
        if (targetDataLine != null) {
            targetDataLine.stop();
            targetDataLine.close();
        }
        
        levelMeter.setValue(0);
        recordButton.setEnabled(false);
        stopButton.setEnabled(false);
        sendButton.setEnabled(true);
        statusLabel.setText("Ready to send (" + formatDuration(recordingDurationSecs) + ")");
    }
    
    private void sendVoiceMessage() {
        if (audioStream == null || audioStream.size() == 0) {
            return;
        }
        
        try {
            chatClient.sendVoiceMessage(audioStream.toByteArray(), recordingDurationSecs, recipientId);
            
            clearRecording();
            
            statusLabel.setText("Voice message sent");
            
            SwingUtilities.getWindowAncestor(this).dispose();
            
        } catch (RemoteException e) {
            LOGGER.log(Level.SEVERE, "Error sending voice message", e);
            JOptionPane.showMessageDialog(this,
                    "Error sending voice message: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void clearRecording() {
        if (audioStream != null) {
            try {
                audioStream.close();
                audioStream = null;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing audio stream", e);
            }
        }
        
        recordingDurationSecs = 0;
        recordButton.setEnabled(true);
        stopButton.setEnabled(false);
        sendButton.setEnabled(false);
        statusLabel.setText("Ready to record");
    }
    
    private void updateLevelMeter() {
        if (isRecording && targetDataLine != null) {
            int level = calculateLevel();
            levelMeter.setValue(level);
        }
    }
    
    private int calculateLevel() {
        byte[] buffer = new byte[targetDataLine.getBufferSize() / 5];
        int bytesRead = targetDataLine.read(buffer, 0, buffer.length);
        
        if (bytesRead <= 0) return 0;
        
        long sum = 0;
        for (int i = 0; i < bytesRead; i += 2) {
            int sample = 0;
            if (i + 1 < bytesRead) {
                sample = (short) ((buffer[i] & 0xFF) | ((buffer[i + 1] & 0xFF) << 8));
            }
            sum += Math.abs(sample);
        }
        
        double rms = Math.sqrt(sum / (bytesRead / 2.0));
        return (int) Math.min(100, rms * 100 / 32768);
    }
    
    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    public static void showRecorder(Component parent, ChatClientImpl chatClient, String recipientId) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Voice Message");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setModal(true);
        
        VoiceRecorderPanel recorderPanel = new VoiceRecorderPanel(chatClient, recipientId);
        dialog.getContentPane().add(recorderPanel);
        
        dialog.pack();
        dialog.setSize(400, 200);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
} 