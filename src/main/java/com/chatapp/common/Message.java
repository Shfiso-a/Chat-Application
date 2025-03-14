package com.chatapp.common;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Message implements Serializable {
    private static final long serialVersionUID = 2L;
    
    private String content;
    private String senderId;
    private String senderName;
    private LocalDateTime timestamp;
    private MessageType type;
    
    private String replyToMessageId;
    private boolean isRichText;
    private MessageStatus status = MessageStatus.SENDING;
    private FileAttachment fileAttachment;
    private VoiceAttachment voiceAttachment;
    private VideoAttachment videoAttachment;
    private String messageId;
    private Map<String, String> formattingTags = new HashMap<>();
    private List<MessageReaction> reactions = new ArrayList<>();
    
    public enum MessageType {
        TEXT,
        SYSTEM,
        NOTIFICATION,
        FILE,
        VOICE,
        VIDEO
    }
    
    public enum MessageStatus {
        SENDING,
        SENT,
        DELIVERED,
        READ
    }
    
    public Message(String content, String senderId, String senderName, MessageType type) {
        this.messageId = java.util.UUID.randomUUID().toString();
        this.content = content;
        this.senderId = senderId;
        this.senderName = senderName;
        this.timestamp = LocalDateTime.now();
        this.type = type;
    }
    
    public static Message createSystemMessage(String content) {
        return new Message(content, "SYSTEM", "System", MessageType.SYSTEM);
    }
    
    public static Message createNotificationMessage(String content) {
        return new Message(content, "SYSTEM", "System", MessageType.NOTIFICATION);
    }
    
    public static Message createFileMessage(String content, String senderId, String senderName, FileAttachment attachment) {
        Message message = new Message(content, senderId, senderName, MessageType.FILE);
        message.setFileAttachment(attachment);
        return message;
    }
    
    public static Message createVoiceMessage(String senderId, String senderName, byte[] voiceData, int durationSeconds) {
        Message message = new Message("Voice message", senderId, senderName, MessageType.VOICE);
        message.setVoiceAttachment(new VoiceAttachment(voiceData, durationSeconds));
        return message;
    }
    
    public static Message createVideoMessage(String senderId, String senderName, byte[] videoData, byte[] thumbnailData, int durationSeconds) {
        Message message = new Message("Video message", senderId, senderName, MessageType.VIDEO);
        message.setVideoAttachment(new VideoAttachment(videoData, thumbnailData, durationSeconds));
        return message;
    }
    
    public static Message createReplyMessage(String content, String senderId, String senderName, String replyToMessageId) {
        Message message = new Message(content, senderId, senderName, MessageType.TEXT);
        message.setReplyToMessageId(replyToMessageId);
        return message;
    }
    
    public String getContent() {
        return content;
    }
    
    public String getSenderId() {
        return senderId;
    }
    
    public String getSenderName() {
        return senderName;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public MessageType getType() {
        return type;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public String getReplyToMessageId() {
        return replyToMessageId;
    }
    
    public void setReplyToMessageId(String replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }
    
    public boolean isRichText() {
        return isRichText;
    }
    
    public void setRichText(boolean richText) {
        isRichText = richText;
    }
    
    public MessageStatus getStatus() {
        return status;
    }
    
    public void setStatus(MessageStatus status) {
        this.status = status;
    }
    
    public FileAttachment getFileAttachment() {
        return fileAttachment;
    }
    
    public void setFileAttachment(FileAttachment fileAttachment) {
        this.fileAttachment = fileAttachment;
    }
    
    public VoiceAttachment getVoiceAttachment() {
        return voiceAttachment;
    }
    
    public void setVoiceAttachment(VoiceAttachment voiceAttachment) {
        this.voiceAttachment = voiceAttachment;
    }
    
    public VideoAttachment getVideoAttachment() {
        return videoAttachment;
    }
    
    public void setVideoAttachment(VideoAttachment videoAttachment) {
        this.videoAttachment = videoAttachment;
    }
    
    public Map<String, String> getFormattingTags() {
        return formattingTags;
    }
    
    public void addFormattingTag(String range, String format) {
        formattingTags.put(range, format);
    }
    
    public List<MessageReaction> getReactions() {
        return reactions;
    }
    
    public void addReaction(String userId, String reactionType) {
        reactions.add(new MessageReaction(userId, reactionType));
    }
    
    public void removeReaction(String userId) {
        reactions.removeIf(reaction -> reaction.getUserId().equals(userId));
    }
    
    public static class FileAttachment implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String fileName;
        private String encodedContent;
        private long fileSize;
        private String contentType;
        private String fileId; // ID of the file stored on the server
        
        public FileAttachment(String fileName, String encodedContent, long fileSize, String contentType) {
            this.fileName = fileName;
            this.encodedContent = encodedContent;
            this.fileSize = fileSize;
            this.contentType = contentType;
            this.fileId = null; // Will be set by the server when stored
        }
        
        // Constructor for server-stored files
        public FileAttachment(String fileName, long fileSize, String contentType, String fileId) {
            this.fileName = fileName;
            this.encodedContent = null; // Content is stored on the server
            this.fileSize = fileSize;
            this.contentType = contentType;
            this.fileId = fileId;
        }
        
        public String getFileName() {
            return fileName;
        }
        
        public String getEncodedContent() {
            return encodedContent;
        }
        
        public long getFileSize() {
            return fileSize;
        }
        
        public String getContentType() {
            return contentType;
        }
        
        public String getFileId() {
            return fileId;
        }
        
        public void setFileId(String fileId) {
            this.fileId = fileId;
        }
        
        public boolean isStoredOnServer() {
            return fileId != null;
        }
    }
    
    public static class VoiceAttachment implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private byte[] audioData;
        private int durationInSeconds;
        private String format = "audio/mp3"; // Default format
        
        public VoiceAttachment(byte[] audioData, int durationInSeconds) {
            this.audioData = audioData;
            this.durationInSeconds = durationInSeconds;
        }
        
        public byte[] getAudioData() {
            return audioData;
        }
        
        public int getDurationInSeconds() {
            return durationInSeconds;
        }
        
        public String getFormat() {
            return format;
        }
        
        public void setFormat(String format) {
            this.format = format;
        }
        
        public String getFormattedDuration() {
            int minutes = durationInSeconds / 60;
            int seconds = durationInSeconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }
    }
    
    public static class VideoAttachment implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private byte[] videoData;
        private byte[] thumbnailData;
        private int durationInSeconds;
        private String format = "video/mp4"; // Default format
        
        public VideoAttachment(byte[] videoData, byte[] thumbnailData, int durationInSeconds) {
            this.videoData = videoData;
            this.thumbnailData = thumbnailData;
            this.durationInSeconds = durationInSeconds;
        }
        
        public byte[] getVideoData() {
            return videoData;
        }
        
        public byte[] getThumbnailData() {
            return thumbnailData;
        }
        
        public int getDurationInSeconds() {
            return durationInSeconds;
        }
        
        public String getFormat() {
            return format;
        }
        
        public void setFormat(String format) {
            this.format = format;
        }
        
        public String getFormattedDuration() {
            int minutes = durationInSeconds / 60;
            int seconds = durationInSeconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }
    }
    
    public static class MessageReaction implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String userId;
        private String reactionType;
        
        public MessageReaction(String userId, String reactionType) {
            this.userId = userId;
            this.reactionType = reactionType;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getReactionType() {
            return reactionType;
        }
    }
}