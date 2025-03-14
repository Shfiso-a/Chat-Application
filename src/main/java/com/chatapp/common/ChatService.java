package com.chatapp.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface ChatService extends Remote {
    
    String registerClient(ChatClient client) throws RemoteException;
    
    void unregisterClient(String clientId) throws RemoteException;
    
    void sendMessage(Message message, String senderId, String recipientId) throws RemoteException;
    
    List<ClientInfo> getOnlineClients() throws RemoteException;
    
    List<Message> getChatHistory(String clientId) throws RemoteException;
    
    void updateMessageStatus(String messageId, Message.MessageStatus status) throws RemoteException;
    
    void updateClientProfile(String clientId, ClientInfo clientInfo) throws RemoteException;
    
    Message getMessage(String messageId) throws RemoteException;
    
    void addReactionToMessage(String messageId, String userId, String reactionType) throws RemoteException;
    
    void removeReactionFromMessage(String messageId, String userId) throws RemoteException;
    
    ClientInfo getClientInfo(String clientId) throws RemoteException;
    
    void updateClientPresence(String clientId, ClientInfo.PresenceStatus status) throws RemoteException;
    
    void setClientStatusMessage(String clientId, String statusMessage) throws RemoteException;
    
    void setClientProfilePicture(String clientId, byte[] profilePicture) throws RemoteException;
    
    Map<String, Integer> getUnreadMessageCount(String clientId) throws RemoteException;
    
    String storeFile(String fileName, String encodedContent, long fileSize, String contentType) throws RemoteException;
    
    String getFileContent(String fileId) throws RemoteException;
    
    String[] getFileMetadata(String fileId) throws RemoteException;
}