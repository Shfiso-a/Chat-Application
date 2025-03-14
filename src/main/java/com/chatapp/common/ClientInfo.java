package com.chatapp.common;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ClientInfo implements Serializable {
    private static final long serialVersionUID = 2L;
    
    private String id;
    private String name;
    private String host;
    private LocalDateTime connectedSince;
    private boolean online;
    
    private byte[] profilePicture;
    private String statusMessage;
    private PresenceStatus presenceStatus = PresenceStatus.AVAILABLE;
    private String email;
    private LocalDateTime lastSeen;
    
    public enum PresenceStatus {
        AVAILABLE,
        AWAY,
        BUSY,
        INVISIBLE
    }
    
    public ClientInfo(String id, String name, String host) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.connectedSince = LocalDateTime.now();
        this.online = true;
        this.lastSeen = LocalDateTime.now();
    }
    
    public ClientInfo(String name, String host) {
        this(null, name, host);
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public LocalDateTime getConnectedSince() {
        return connectedSince;
    }
    
    public boolean isOnline() {
        return online;
    }
    
    public void setOnline(boolean online) {
        this.online = online;
        if (!online) {
            this.lastSeen = LocalDateTime.now();
        }
    }
    
    public byte[] getProfilePicture() {
        return profilePicture;
    }
    
    public void setProfilePicture(byte[] profilePicture) {
        this.profilePicture = profilePicture;
    }
    
    public String getStatusMessage() {
        return statusMessage;
    }
    
    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }
    
    public PresenceStatus getPresenceStatus() {
        return presenceStatus;
    }
    
    public void setPresenceStatus(PresenceStatus presenceStatus) {
        this.presenceStatus = presenceStatus;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public LocalDateTime getLastSeen() {
        return lastSeen;
    }
    
    public String getDisplayStatus() {
        if (online) {
            switch (presenceStatus) {
                case AVAILABLE:
                    return "Online";
                case AWAY:
                    return "Away";
                case BUSY:
                    return "Busy";
                case INVISIBLE:
                    return "Offline"; // Show as offline to others
                default:
                    return "Online";
            }
        } else {
            return "Last seen " + formatLastSeen();
        }
    }
    
    private String formatLastSeen() {
        LocalDateTime now = LocalDateTime.now();
        if (lastSeen.toLocalDate().equals(now.toLocalDate())) {
            return "today at " + lastSeen.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        } else if (lastSeen.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
            return "yesterday at " + lastSeen.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        } else {
            return lastSeen.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ClientInfo that = (ClientInfo) obj;
        return id != null ? id.equals(that.id) : that.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
} 