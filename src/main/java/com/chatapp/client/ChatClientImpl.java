package com.chatapp.client;

import com.chatapp.common.ChatClient;
import com.chatapp.common.ChatService;
import com.chatapp.common.ClientInfo;
import com.chatapp.common.Message;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatClientImpl extends UnicastRemoteObject implements ChatClient {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ChatClientImpl.class.getName());
    
    private final ClientInfo clientInfo;
    private final ChatService chatService;
    private String clientId;
    
    private final List<MessageListener> messageListeners = new CopyOnWriteArrayList<>();
    private final List<ClientStatusListener> clientStatusListeners = new CopyOnWriteArrayList<>();
    private final List<Message> receivedMessages = Collections.synchronizedList(new ArrayList<>());
    
    private final Map<String, Message> messagesById = new HashMap<>();
    private String replyToMessageId = null;
    private final List<ProfileListener> profileListeners = new CopyOnWriteArrayList<>();
    private final List<PresenceStatusListener> presenceStatusListeners = new CopyOnWriteArrayList<>();
    
    public ChatClientImpl(ClientInfo clientInfo, ChatService chatService) throws RemoteException {
        super();
        this.clientInfo = clientInfo;
        this.chatService = chatService;
    }
    
    public void connect() throws RemoteException {
        clientId = chatService.registerClient(this);
        clientInfo.setId(clientId);
        
        List<Message> history = chatService.getChatHistory(clientId);
        for (Message message : history) {
            receiveMessage(message);
        }
        
        LOGGER.info("Connected to chat server with ID: " + clientId);
    }
    
    public void disconnect() throws RemoteException {
        if (clientId != null) {
            chatService.unregisterClient(clientId);
            LOGGER.info("Disconnected from chat server");
        }
    }
    
    public void sendMessage(String content, String recipientId) throws RemoteException {
        Message message;
        if (replyToMessageId != null) {
            message = Message.createReplyMessage(content, clientId, clientInfo.getName(), replyToMessageId);
            replyToMessageId = null;
        } else {
            message = new Message(content, clientId, clientInfo.getName(), Message.MessageType.TEXT);
        }
        chatService.sendMessage(message, clientId, recipientId);
    }
    
    public void sendFileMessage(String content, Message.FileAttachment attachment, String recipientId) throws RemoteException {
        Message message = Message.createFileMessage(content, clientId, clientInfo.getName(), attachment);
        if (replyToMessageId != null) {
            message.setReplyToMessageId(replyToMessageId);
            replyToMessageId = null;
        }
        chatService.sendMessage(message, clientId, recipientId);
    }
    
    public void sendVoiceMessage(byte[] audioData, int durationSeconds, String recipientId) throws RemoteException {
        Message message = Message.createVoiceMessage(clientId, clientInfo.getName(), audioData, durationSeconds);
        if (replyToMessageId != null) {
            message.setReplyToMessageId(replyToMessageId);
            replyToMessageId = null;
        }
        chatService.sendMessage(message, clientId, recipientId);
    }
    
    public void sendVideoMessage(byte[] videoData, byte[] thumbnailData, int durationSeconds, String recipientId) throws RemoteException {
        Message message = Message.createVideoMessage(clientId, clientInfo.getName(), videoData, thumbnailData, durationSeconds);
        if (replyToMessageId != null) {
            message.setReplyToMessageId(replyToMessageId);
            replyToMessageId = null;
        }
        chatService.sendMessage(message, clientId, recipientId);
    }
    
    public void updateProfile(byte[] profilePicture, String statusMessage, String email) throws RemoteException {
        if (profilePicture != null) {
            clientInfo.setProfilePicture(profilePicture);
        }
        if (statusMessage != null) {
            clientInfo.setStatusMessage(statusMessage);
        }
        if (email != null) {
            clientInfo.setEmail(email);
        }
        
        chatService.updateClientProfile(clientId, clientInfo);
    }
    
    public void updatePresenceStatus(ClientInfo.PresenceStatus status) throws RemoteException {
        clientInfo.setPresenceStatus(status);
        chatService.updateClientPresence(clientId, status);
    }
    
    public void setReplyToMessage(String messageId) {
        this.replyToMessageId = messageId;
    }
    
    public Message getMessageById(String messageId) {
        return messagesById.get(messageId);
    }
    
    public boolean isReplyingToMessage() {
        return replyToMessageId != null;
    }
    
    public String getReplyToMessageId() {
        return replyToMessageId;
    }
    
    public void cancelReply() {
        replyToMessageId = null;
    }
    
    public List<ClientInfo> getOnlineClients() throws RemoteException {
        return chatService.getOnlineClients();
    }
    
    public String getClientId() {
        return clientId;
    }
    
    @Override
    public ClientInfo getClientInfo() {
        return clientInfo;
    }
    
    public void addMessageListener(MessageListener listener) {
        messageListeners.add(listener);
    }
    
    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }
    
    public void addClientStatusListener(ClientStatusListener listener) {
        clientStatusListeners.add(listener);
    }
    
    public void removeClientStatusListener(ClientStatusListener listener) {
        clientStatusListeners.remove(listener);
    }
    
    public void addProfileListener(ProfileListener listener) {
        profileListeners.add(listener);
    }
    
    public void removeProfileListener(ProfileListener listener) {
        profileListeners.remove(listener);
    }
    
    public void addPresenceStatusListener(PresenceStatusListener listener) {
        presenceStatusListeners.add(listener);
    }
    
    public void removePresenceStatusListener(PresenceStatusListener listener) {
        presenceStatusListeners.remove(listener);
    }
    
    public List<Message> getReceivedMessages() {
        return new ArrayList<>(receivedMessages);
    }
    
    @Override
    public void receiveMessage(Message message) {
        receivedMessages.add(message);
        messagesById.put(message.getMessageId(), message);
        
        for (MessageListener listener : messageListeners) {
            try {
                listener.messageReceived(message);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error notifying message listener", e);
            }
        }
    }
    
    @Override
    public void clientConnected(ClientInfo clientInfo) {
        for (ClientStatusListener listener : clientStatusListeners) {
            try {
                listener.clientConnected(clientInfo);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error notifying client status listener", e);
            }
        }
        
        Message message = Message.createNotificationMessage(clientInfo.getName() + " has joined the chat");
        receiveMessage(message);
    }
    
    @Override
    public void clientDisconnected(String clientId) {
        ClientInfo clientInfo = null;
        try {
            List<ClientInfo> onlineClients = chatService.getOnlineClients();
            for (ClientInfo info : onlineClients) {
                if (info.getId().equals(clientId)) {
                    clientInfo = info;
                    break;
                }
            }
        } catch (RemoteException e) {
            LOGGER.log(Level.WARNING, "Error getting online clients", e);
        }
        
        for (ClientStatusListener listener : clientStatusListeners) {
            try {
                listener.clientDisconnected(clientId);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error notifying client status listener", e);
            }
        }
        
        String name = clientInfo != null ? clientInfo.getName() : "Unknown user";
        Message message = Message.createNotificationMessage(name + " has left the chat");
        receiveMessage(message);
    }
    
    @Override
    public void receiveMessageStatusUpdate(String messageId, Message.MessageStatus status) throws RemoteException {
        Message message = messagesById.get(messageId);
        if (message != null) {
            message.setStatus(status);
            for (MessageListener listener : messageListeners) {
                try {
                    listener.messageStatusUpdated(messageId, status);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error notifying message status listener", e);
                }
            }
        }
    }
    
    @Override
    public void clientProfileUpdated(ClientInfo updatedClientInfo) throws RemoteException {
        for (ProfileListener listener : profileListeners) {
            try {
                listener.profileUpdated(updatedClientInfo);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error notifying profile listener", e);
            }
        }
        
        if (!updatedClientInfo.getId().equals(clientId)) {
            Message message = Message.createNotificationMessage(
                    updatedClientInfo.getName() + " updated their profile");
            receiveMessage(message);
        }
    }
    
    @Override
    public void messageReactionUpdated(String messageId) throws RemoteException {
    }
    
    @Override
    public void clientPresenceUpdated(String clientId, ClientInfo.PresenceStatus status) throws RemoteException {
        for (PresenceStatusListener listener : presenceStatusListeners) {
            try {
                listener.presenceStatusUpdated(clientId, status);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error notifying presence status listener", e);
            }
        }
    }
    
    @Override
    public void receiveVoiceCall(String callerId) throws RemoteException {
    }
    
    @Override
    public void receiveVideoCall(String callerId) throws RemoteException {
    }
    
    @Override
    public void callAccepted(String clientId, String callId) throws RemoteException {
    }
    
    @Override
    public void callRejected(String clientId, String reason) throws RemoteException {
    }
    
    public ChatService getChatService() {
        return chatService;
    }
    
    public interface MessageListener {
        void messageReceived(Message message);
        
        default void messageStatusUpdated(String messageId, Message.MessageStatus status) {
        }
    }
    
    public interface ClientStatusListener {
        void clientConnected(ClientInfo clientInfo);
        void clientDisconnected(String clientId);
    }
    
    public interface ProfileListener {
        void profileUpdated(ClientInfo clientInfo);
    }
    
    public interface PresenceStatusListener {
        void presenceStatusUpdated(String clientId, ClientInfo.PresenceStatus status);
    }
}