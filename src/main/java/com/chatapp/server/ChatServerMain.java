package com.chatapp.server;

import com.chatapp.common.ChatService;
import com.chatapp.gui.ServerGUI;

import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatServerMain {
    private static final Logger LOGGER = Logger.getLogger(ChatServerMain.class.getName());
    private static final int RMI_PORT = 1099;
    private static final String SERVICE_NAME = "ChatService";
    
    private Registry registry;
    private ChatServerImpl chatServer;
    private ServerGUI serverGUI;
    
    public void startServer() {
        try {
            chatServer = new ChatServerImpl();
            
            try {
                registry = LocateRegistry.createRegistry(RMI_PORT);
                LOGGER.info("RMI Registry created on port " + RMI_PORT);
            } catch (Exception e) {
                LOGGER.info("RMI Registry already exists, getting existing registry");
                registry = LocateRegistry.getRegistry(RMI_PORT);
            }
            
            registry.rebind(SERVICE_NAME, chatServer);
            
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            LOGGER.info("Chat server started at " + hostAddress + ":" + RMI_PORT);
            LOGGER.info("Service name: " + SERVICE_NAME);
            
            serverGUI = new ServerGUI(hostAddress, RMI_PORT);
            serverGUI.setVisible(true);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start chat server", e);
        }
    }
    
    public void stopServer() {
        try {
            if (registry != null && chatServer != null) {
                registry.unbind(SERVICE_NAME);
                LOGGER.info("Chat server stopped");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to stop chat server", e);
        }
    }
    
    public ChatService getChatServer() {
        return chatServer;
    }
    
    public static void main(String[] args) {
        ChatServerMain serverMain = new ChatServerMain();
        serverMain.startServer();
        
        Runtime.getRuntime().addShutdownHook(new Thread(serverMain::stopServer));
    }
} 