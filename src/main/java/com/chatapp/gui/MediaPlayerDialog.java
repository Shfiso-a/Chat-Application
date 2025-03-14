package com.chatapp.gui;

import com.chatapp.common.Message;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MediaPlayerDialog extends JDialog {
    private static final Logger LOGGER = Logger.getLogger(MediaPlayerDialog.class.getName());
    
    private enum MediaType {
        VOICE,
        VIDEO
    }
    
    private final MediaType mediaType;
    private final byte[] mediaData;
    private final int durationSeconds;
    private JLabel timeLabel;
    private JButton playPauseButton;
    private JSlider progressSlider;
    private Timer progressTimer;
    private int currentPosition = 0;
    private boolean isPlaying = false;
    
    // For voice playback
    private Clip audioClip;
    
    // For video playback (simulated in this implementation)
    private byte[] thumbnailData;
    private JLabel videoDisplayLabel;
    private Timer videoFrameTimer;
    
    public MediaPlayerDialog(Dialog owner, String title, Message.VoiceAttachment voiceAttachment) {
        super(owner, title, true);
        this.mediaType = MediaType.VOICE;
        this.mediaData = voiceAttachment.getAudioData();
        this.durationSeconds = voiceAttachment.getDurationInSeconds();
        
        initialize();
        setupVoicePlayer();
    }
    
    public MediaPlayerDialog(Dialog owner, String title, Message.VideoAttachment videoAttachment) {
        super(owner, title, true);
        this.mediaType = MediaType.VIDEO;
        this.mediaData = videoAttachment.getVideoData();
        this.thumbnailData = videoAttachment.getThumbnailData();
        this.durationSeconds = videoAttachment.getDurationInSeconds();
        
        initialize();
        setupVideoPlayer();
    }
    
    private void initialize() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(mediaType == MediaType.VOICE ? 400 : 480, mediaType == MediaType.VOICE ? 150 : 400);
        setLocationRelativeTo(getOwner());
        
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPanel.setBackground(new Color(48, 51, 56));
        
        // Add components based on media type
        if (mediaType == MediaType.VOICE) {
            contentPanel.add(createAudioControls(), BorderLayout.CENTER);
        } else {
            contentPanel.add(createVideoControls(), BorderLayout.CENTER);
        }
        
        setContentPane(contentPanel);
        
        // Clean up resources when dialog is closed
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanupResources();
            }
        });
    }
    
    private JPanel createAudioControls() {
        JPanel controlPanel = new JPanel(new BorderLayout(5, 10));
        controlPanel.setOpaque(false);
        
        // Create progress slider
        progressSlider = new JSlider(0, durationSeconds, 0);
        progressSlider.setForeground(new Color(61, 157, 232));
        progressSlider.setOpaque(false);
        progressSlider.addChangeListener(e -> {
            if (progressSlider.getValueIsAdjusting()) {
                currentPosition = progressSlider.getValue();
                updateTimeLabel();
                
                if (audioClip != null && audioClip.isOpen()) {
                    // Set playback position
                    long microseconds = currentPosition * 1000000L;
                    audioClip.setMicrosecondPosition(microseconds);
                }
            }
        });
        
        // Create play/pause button
        playPauseButton = new JButton("▶");
        playPauseButton.setFont(new Font("Arial", Font.BOLD, 16));
        playPauseButton.setForeground(Color.WHITE);
        playPauseButton.setBackground(new Color(61, 157, 232));
        playPauseButton.setFocusPainted(false);
        playPauseButton.addActionListener(e -> togglePlayback());
        
        // Create time label
        timeLabel = new JLabel("0:00 / " + formatDuration(durationSeconds));
        timeLabel.setForeground(Color.WHITE);
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        
        // Layout components
        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        topPanel.setOpaque(false);
        topPanel.add(progressSlider, BorderLayout.CENTER);
        
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.add(playPauseButton, BorderLayout.WEST);
        bottomPanel.add(timeLabel, BorderLayout.EAST);
        
        controlPanel.add(topPanel, BorderLayout.CENTER);
        controlPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        return controlPanel;
    }
    
    private JPanel createVideoControls() {
        JPanel videoPanel = new JPanel(new BorderLayout(5, 10));
        videoPanel.setOpaque(false);
        
        // Create video display area
        videoDisplayLabel = new JLabel();
        videoDisplayLabel.setHorizontalAlignment(SwingConstants.CENTER);
        videoDisplayLabel.setVerticalAlignment(SwingConstants.CENTER);
        videoDisplayLabel.setPreferredSize(new Dimension(420, 240));
        videoDisplayLabel.setBackground(Color.BLACK);
        videoDisplayLabel.setOpaque(true);
        
        // Set thumbnail as initial image
        if (thumbnailData != null) {
            try {
                ImageIcon icon = new ImageIcon(thumbnailData);
                Image img = icon.getImage().getScaledInstance(420, 240, Image.SCALE_SMOOTH);
                videoDisplayLabel.setIcon(new ImageIcon(img));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error setting thumbnail", e);
            }
        }
        
        // Create controls similar to audio controls
        JPanel controlPanel = createAudioControls();
        
        videoPanel.add(videoDisplayLabel, BorderLayout.CENTER);
        videoPanel.add(controlPanel, BorderLayout.SOUTH);
        
        return videoPanel;
    }
    
    private void setupVoicePlayer() {
        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(
                    new ByteArrayInputStream(mediaData));
            
            audioClip = AudioSystem.getClip();
            audioClip.open(audioStream);
            
            // Add listener to update UI when playback is complete
            audioClip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    SwingUtilities.invokeLater(() -> {
                        if (!audioClip.isRunning() && currentPosition >= durationSeconds) {
                            isPlaying = false;
                            playPauseButton.setText("▶");
                            currentPosition = 0;
                            progressSlider.setValue(0);
                            updateTimeLabel();
                        }
                    });
                }
            });
            
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            LOGGER.log(Level.SEVERE, "Error setting up audio player", e);
            JOptionPane.showMessageDialog(this,
                    "Error playing audio: " + e.getMessage(),
                    "Playback Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void setupVideoPlayer() {
        // This is a simulation - a real implementation would use a video player library
        
        // Set up a timer to simulate updating video frames
        videoFrameTimer = new Timer(100, e -> {
            if (isPlaying && thumbnailData != null) {
                // In a real implementation, this would update with actual video frames
                // For our simulation, we'll just manipulate the thumbnail to simulate "playback"
                try {
                    // Create a slightly different image each frame
                    ImageIcon icon = new ImageIcon(thumbnailData);
                    Image img = icon.getImage();
                    
                    // Create a modified version of the image based on current position
                    BufferedImage modifiedImg = new BufferedImage(
                            420, 240, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = modifiedImg.createGraphics();
                    
                    // Draw the base image
                    g2d.drawImage(img, 0, 0, 420, 240, null);
                    
                    // Add a timestamp
                    g2d.setColor(Color.WHITE);
                    g2d.setFont(new Font("Arial", Font.BOLD, 16));
                    g2d.drawString(formatDuration(currentPosition), 20, 30);
                    
                    // Add some animation based on current position
                    int offset = currentPosition * 10 % 100;
                    g2d.setColor(new Color(255, 255, 255, 100));
                    g2d.fillOval(offset + 50, 50, 30, 30);
                    
                    g2d.dispose();
                    
                    videoDisplayLabel.setIcon(new ImageIcon(modifiedImg));
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error simulating video playback", ex);
                }
            }
        });
        videoFrameTimer.start();
    }
    
    private void togglePlayback() {
        if (isPlaying) {
            pausePlayback();
        } else {
            startPlayback();
        }
    }
    
    private void startPlayback() {
        isPlaying = true;
        playPauseButton.setText("⏸");
        
        // Start progress timer
        if (progressTimer == null) {
            progressTimer = new Timer(1000, e -> {
                if (isPlaying && currentPosition < durationSeconds) {
                    currentPosition++;
                    progressSlider.setValue(currentPosition);
                    updateTimeLabel();
                    
                    if (currentPosition >= durationSeconds) {
                        pausePlayback();
                        currentPosition = 0;
                        progressSlider.setValue(0);
                        updateTimeLabel();
                    }
                }
            });
        }
        progressTimer.start();
        
        // Start actual playback based on media type
        if (mediaType == MediaType.VOICE && audioClip != null) {
            audioClip.setMicrosecondPosition(currentPosition * 1000000L);
            audioClip.start();
        }
        // For video, the videoFrameTimer will handle playback simulation
    }
    
    private void pausePlayback() {
        isPlaying = false;
        playPauseButton.setText("▶");
        
        if (progressTimer != null) {
            progressTimer.stop();
        }
        
        if (mediaType == MediaType.VOICE && audioClip != null) {
            audioClip.stop();
        }
        // For video, the videoFrameTimer will continue but the simulation won't update
    }
    
    private void updateTimeLabel() {
        timeLabel.setText(formatDuration(currentPosition) + " / " + formatDuration(durationSeconds));
    }
    
    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
    
    private void cleanupResources() {
        // Stop timers
        if (progressTimer != null) {
            progressTimer.stop();
            progressTimer = null;
        }
        if (videoFrameTimer != null) {
            videoFrameTimer.stop();
            videoFrameTimer = null;
        }
        
        // Close audio resources
        if (audioClip != null) {
            audioClip.stop();
            audioClip.close();
            audioClip = null;
        }
    }
    
    // Static convenience methods to show the dialog
    public static void playVoiceMessage(Component parent, Message.VoiceAttachment voiceAttachment) {
        Window parentWindow = SwingUtilities.getWindowAncestor(parent);
        JDialog parentDialog = new JDialog(parentWindow);
        MediaPlayerDialog playerDialog = new MediaPlayerDialog(parentDialog, "Voice Message", voiceAttachment);
        playerDialog.setVisible(true);
    }
    
    public static void playVideoMessage(Component parent, Message.VideoAttachment videoAttachment) {
        Window parentWindow = SwingUtilities.getWindowAncestor(parent);
        JDialog parentDialog = new JDialog(parentWindow);
        MediaPlayerDialog playerDialog = new MediaPlayerDialog(parentDialog, "Video Message", videoAttachment);
        playerDialog.setVisible(true);
    }
} 