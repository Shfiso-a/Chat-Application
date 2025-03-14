package com.chatapp.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ChatClient extends Remote {
    
    void receiveMessage(Message message) throws RemoteException;
    
    void clientConnected(ClientInfo clientInfo) throws RemoteException;
    
    void clientDisconnected(String clientId) throws RemoteException;
    
    ClientInfo getClientInfo() throws RemoteException;
    
    void receiveMessageStatusUpdate(String messageId, Message.MessageStatus status) throws RemoteException;
    
    void clientProfileUpdated(ClientInfo clientInfo) throws RemoteException;
    
    void messageReactionUpdated(String messageId) throws RemoteException;
    
    void clientPresenceUpdated(String clientId, ClientInfo.PresenceStatus status) throws RemoteException;
    
    void receiveVoiceCall(String callerId) throws RemoteException;
    
    void receiveVideoCall(String callerId) throws RemoteException;
    
    void callAccepted(String clientId, String callId) throws RemoteException;
    
    void callRejected(String clientId, String reason) throws RemoteException;
}