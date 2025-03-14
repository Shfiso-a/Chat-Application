package com.chatapp;

import com.chatapp.client.ChatClientMain;
import com.chatapp.server.ChatServerMain;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--server")) {
            ChatServerMain.main(args);
        } else {
            ChatClientMain.main(args);
        }
    }
} 