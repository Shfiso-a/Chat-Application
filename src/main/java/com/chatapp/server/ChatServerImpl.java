package com.chatapp.server;

import com.chatapp.common.ChatClient;
import com.chatapp.common.ChatService;
import com.chatapp.common.ClientInfo;
import com.chatapp.common.Message;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatServerImpl extends UnicastRemoteObject implements ChatService {
    private static final long serialVersionUID = 2L;
    private static final Logger LOGGER = Logger.getLogger(ChatServerImpl.class.getName());
    
    private final Map<String, ChatClient> clients = new ConcurrentHashMap<>();
    
    private final Map<String, ClientInfo> clientInfos = new ConcurrentHashMap<>();
    
    private final List<Message> chatHistory = Collections.synchronizedList(new ArrayList<>());
    
    private static final int MAX_HISTORY_SIZE = 500;
    
    private final Map<String, Message> messagesById = new ConcurrentHashMap<>();
    
    private final Map<String, Set<String>> unreadMessages = new ConcurrentHashMap<>();
    
    private final FileStorageService fileStorage;
    
    public ChatServerImpl() throws RemoteException {
        super();
        String userHome = System.getProperty("user.home");
        String storageDir = userHome + File.separator + "ChatAppFiles";
        this.fileStorage = new FileStorageService(storageDir);
        LOGGER.info("Chat server started with enhanced features and permanent file storage");
    }
    
    @Override
    public String registerClient(ChatClient client) throws RemoteException {
        ClientInfo clientInfo = client.getClientInfo();
        String clientId = UUID.randomUUID().toString();
        clientInfo.setId(clientId);
        
        clients.put(clientId, client);
        clientInfos.put(clientId, clientInfo);
        
        broadcastClientConnected(clientInfo);
        
        Message welcomeMessage = Message.createSystemMessage("Welcome to the chat server!");
        client.receiveMessage(welcomeMessage);
        
        unreadMessages.put(clientId, new HashSet<>());
        
        LOGGER.info("Client registered: " + clientInfo.getName() + " (" + clientId + ")");
        return clientId;
    }
    
    @Override
    public void unregisterClient(String clientId) throws RemoteException {
        if (clients.containsKey(clientId)) {
            ClientInfo clientInfo = clientInfos.get(clientId);
            clients.remove(clientId);
            clientInfo.setOnline(false);
            
            broadcastClientDisconnected(clientId);
            
            LOGGER.info("Client unregistered: " + clientInfo.getName() + " (" + clientId + ")");
        }
    }
    
    @Override
    public void sendMessage(Message message, String senderId, String recipientId) throws RemoteException {
        if (message.getType() == Message.MessageType.FILE && 
            message.getFileAttachment() != null && 
            message.getFileAttachment().getEncodedContent() != null) {
            
            try {
                String fileId = storeFile(
                    message.getFileAttachment().getFileName(),
                    message.getFileAttachment().getEncodedContent(),
                    message.getFileAttachment().getFileSize(),
                    message.getFileAttachment().getContentType()
                );
                
                message.getFileAttachment().setFileId(fileId);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to store file", e);
            }
        }
        
        chatHistory.add(message);
        messagesById.put(message.getMessageId(), message);
        
        if (chatHistory.size() > MAX_HISTORY_SIZE) {
            Message removed = chatHistory.remove(0);
            messagesById.remove(removed.getMessageId());
        }
        
        message.setStatus(Message.MessageStatus.SENT);
        
        if (recipientId == null) {
            for (Map.Entry<String, ChatClient> entry : clients.entrySet()) {
                try {
                    if (!entry.getKey().equals(senderId)) {
                        unreadMessages.get(entry.getKey()).add(message.getMessageId());
                    }
                    entry.getValue().receiveMessage(message);
                } catch (RemoteException e) {
                    LOGGER.log(Level.WARNING, "Failed to send message to client: " + entry.getKey(), e);
                }
            }
        } else {
            ChatClient client = clients.get(recipientId);
            if (client != null) {
                unreadMessages.get(recipientId).add(message.getMessageId());
                client.receiveMessage(message);
                
                if (!senderId.equals(recipientId)) {
                    ChatClient sender = clients.get(senderId);
                    if (sender != null) {
                        sender.receiveMessage(message);
                    }
                }
            }
        }
    }
    
    @Override
    public List<ClientInfo> getOnlineClients() throws RemoteException {
        return new ArrayList<>(clientInfos.values());
    }
    
    @Override
    public List<Message> getChatHistory(String clientId) throws RemoteException {
        return new ArrayList<>(chatHistory);
    }
    
    @Override
    public void updateMessageStatus(String messageId, Message.MessageStatus status) throws RemoteException {
        Message message = messagesById.get(messageId);
        if (message != null) {
            message.setStatus(status);
            
            if (status == Message.MessageStatus.READ) {
                for (Set<String> unread : unreadMessages.values()) {
                    unread.remove(messageId);
                }
            }
            
            String senderId = message.getSenderId();
            ChatClient sender = clients.get(senderId);
            if (sender != null) {
                try {
                    sender.receiveMessageStatusUpdate(messageId, status);
                } catch (RemoteException e) {
                    LOGGER.log(Level.WARNING, "Failed to update message status for sender: " + senderId, e);
                }
            }
        }
    }
    
    public void updateClientProfile(ClientInfo clientInfo) throws RemoteException {
        String clientId = clientInfo.getId();
        if (clientInfos.containsKey(clientId)) {
            clientInfos.put(clientId, clientInfo);
            
            for (Map.Entry<String, ChatClient> entry : clients.entrySet()) {
                try {
                    entry.getValue().clientProfileUpdated(clientInfo);
                } catch (RemoteException e) {
                    LOGGER.log(Level.WARNING, "Failed to notify client about profile update: " + entry.getKey(), e);
                }
            }
        }
    }
    
    @Override
    public void updateClientProfile(String clientId, ClientInfo clientInfo) throws RemoteException {
        clientInfo.setId(clientId);
        updateClientProfile(clientInfo);
    }
    
    @Override
    public Message getMessage(String messageId) throws RemoteException {
        return messagesById.get(messageId);
    }
    
    @Override
    public void addReactionToMessage(String messageId, String userId, String reactionType) throws RemoteException {
        Message message = messagesById.get(messageId);
        if (message != null) {
            message.addReaction(userId, reactionType);
            
            for (Map.Entry<String, ChatClient> entry : clients.entrySet()) {
                try {
                    entry.getValue().messageReactionUpdated(messageId);
                } catch (RemoteException e) {
                    LOGGER.log(Level.WARNING, "Failed to notify client about reaction update: " + entry.getKey(), e);
                }
            }
        }
    }
    
    @Override
    public void removeReactionFromMessage(String messageId, String userId) throws RemoteException {
        Message message = messagesById.get(messageId);
        if (message != null) {
            message.removeReaction(userId);
            
            for (Map.Entry<String, ChatClient> entry : clients.entrySet()) {
                try {
                    entry.getValue().messageReactionUpdated(messageId);
                } catch (RemoteException e) {
                    LOGGER.log(Level.WARNING, "Failed to notify client about reaction update: " + entry.getKey(), e);
                }
            }
        }
    }
    
    @Override
    public ClientInfo getClientInfo(String clientId) throws RemoteException {
        return clientInfos.get(clientId);
    }
    
    public void updateClientPresenceStatus(String clientId, ClientInfo.PresenceStatus status) throws RemoteException {
        ClientInfo clientInfo = clientInfos.get(clientId);
        if (clientInfo != null) {
            clientInfo.setPresenceStatus(status);
            
            for (Map.Entry<String, ChatClient> entry : clients.entrySet()) {
                if (!entry.getKey().equals(clientId)) {
                    try {
                        entry.getValue().clientPresenceUpdated(clientId, status);
                    } catch (RemoteException e) {
                        LOGGER.log(Level.WARNING, "Failed to notify client about presence update: " + entry.getKey(), e);
                    }
                }
            }
        }
    }
    
    @Override
    public void updateClientPresence(String clientId, ClientInfo.PresenceStatus status) throws RemoteException {
        updateClientPresenceStatus(clientId, status);
    }
    
    @Override
    public void setClientStatusMessage(String clientId, String statusMessage) throws RemoteException {
        ClientInfo clientInfo = clientInfos.get(clientId);
        if (clientInfo != null) {
            clientInfo.setStatusMessage(statusMessage);
            
            for (Map.Entry<String, ChatClient> entry : clients.entrySet()) {
                try {
                    entry.getValue().clientProfileUpdated(clientInfo);
                } catch (RemoteException e) {
                    LOGGER.log(Level.WARNING, "Failed to notify client about status message update: " + entry.getKey(), e);
                }
            }
        }
    }
    
    @Override
    public void setClientProfilePicture(String clientId, byte[] profilePicture) throws RemoteException {
        ClientInfo clientInfo = clientInfos.get(clientId);
        if (clientInfo != null) {
            clientInfo.setProfilePicture(profilePicture);
            
            for (Map.Entry<String, ChatClient> entry : clients.entrySet()) {
                try {
                    entry.getValue().clientProfileUpdated(clientInfo);
                } catch (RemoteException e) {
                    LOGGER.log(Level.WARNING, "Failed to notify client about profile picture update: " + entry.getKey(), e);
                }
            }
        }
    }
    
    @Override
    public Map<String, Integer> getUnreadMessageCount(String clientId) throws RemoteException {
        Map<String, Integer> result = new HashMap<>();
        Set<String> unread = unreadMessages.get(clientId);
        
        if (unread != null) {
            Map<String, Integer> countBySender = new HashMap<>();
            
            for (String messageId : unread) {
                Message message = messagesById.get(messageId);
                if (message != null) {
                    String senderId = message.getSenderId();
                    countBySender.put(senderId, countBySender.getOrDefault(senderId, 0) + 1);
                }
            }
            
            return countBySender;
        }
        
        return result;
    }
    
    private void broadcastClientConnected(ClientInfo clientInfo) {
        for (Map.Entry<String, ChatClient> entry : clients.entrySet()) {
            if (!entry.getKey().equals(clientInfo.getId())) {
                try {
                    entry.getValue().clientConnected(clientInfo);
                    
                    ChatClient newClient = clients.get(clientInfo.getId());
                    if (newClient != null) {
                        newClient.clientConnected(clientInfos.get(entry.getKey()));
                    }
                } catch (RemoteException e) {
                    LOGGER.log(Level.WARNING, "Failed to notify client about new connection: " + entry.getKey(), e);
                }
            }
        }
    }
    
    private void broadcastClientDisconnected(String clientId) {
        for (Map.Entry<String, ChatClient> entry : clients.entrySet()) {
            if (!entry.getKey().equals(clientId)) {
                try {
                    entry.getValue().clientDisconnected(clientId);
                } catch (RemoteException e) {
                    LOGGER.log(Level.WARNING, "Failed to notify client about disconnection: " + entry.getKey(), e);
                }
            }
        }
    }
    
    @Override
    public String storeFile(String fileName, String encodedContent, long fileSize, String contentType) throws RemoteException {
        try {
            return fileStorage.storeFile(encodedContent, fileName, contentType);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to store file: " + fileName, e);
            throw new RemoteException("Failed to store file: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getFileContent(String fileId) throws RemoteException {
        try {
            byte[] content = fileStorage.getFileContent(fileId);
            if (content == null) {
                return null;
            }
            return Base64.getEncoder().encodeToString(content);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to get file content: " + fileId, e);
            throw new RemoteException("Failed to get file content: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String[] getFileMetadata(String fileId) throws RemoteException {
        try {
            return fileStorage.getFileMetadata(fileId);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to get file metadata: " + fileId, e);
            throw new RemoteException("Failed to get file metadata: " + e.getMessage(), e);
        }
    }
}