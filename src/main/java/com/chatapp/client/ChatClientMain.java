package com.chatapp.client;

import com.chatapp.common.ChatService;
import com.chatapp.common.ClientInfo;
import com.chatapp.gui.ClientGUI;

import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatClientMain {
    private static final Logger LOGGER = Logger.getLogger(ChatClientMain.class.getName());
    private static final String SERVICE_NAME = "ChatService";
    
    private ChatClientImpl chatClient;
    private ClientGUI clientGUI;
    
    public boolean connect(String serverHost, int serverPort, String clientName) {
        try {
            Registry registry = LocateRegistry.getRegistry(serverHost, serverPort);
            
            ChatService chatService = (ChatService) registry.lookup(SERVICE_NAME);
            
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            
            ClientInfo clientInfo = new ClientInfo(clientName, hostAddress);
            
            chatClient = new ChatClientImpl(clientInfo, chatService);
            chatClient.connect();
            
            clientGUI = new ClientGUI(chatClient);
            clientGUI.setVisible(true);
            
            Runtime.getRuntime().addShutdownHook(new Thread(this::disconnect));
            
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to connect to chat server", e);
            return false;
        }
    }
    
    public void disconnect() {
        try {
            if (chatClient != null) {
                chatClient.disconnect();
            }
        } catch (RemoteException e) {
            LOGGER.log(Level.WARNING, "Error disconnecting from chat server", e);
        }
    }
    
    public ChatClientImpl getChatClient() {
        return chatClient;
    }
    
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--server")) {
            try {
                Class.forName("com.chatapp.server.ChatServerMain")
                     .getMethod("main", String[].class)
                     .invoke(null, (Object) new String[0]);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to start server", e);
            }
        } else {
            LoginDialog loginDialog = new LoginDialog();
            loginDialog.setVisible(true);
        }
    }
}