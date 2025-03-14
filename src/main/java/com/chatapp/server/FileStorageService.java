package com.chatapp.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileStorageService {
    private static final Logger LOGGER = Logger.getLogger(FileStorageService.class.getName());
    
    private final String storageDirectory;
    
    public FileStorageService(String storageDirectory) {
        this.storageDirectory = storageDirectory;
        
        File directory = new File(storageDirectory);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                LOGGER.log(Level.SEVERE, "Failed to create storage directory: " + storageDirectory);
                throw new RuntimeException("Failed to create storage directory");
            }
        }
        
        LOGGER.info("File storage service initialized with directory: " + storageDirectory);
    }
    
    public String storeFile(String fileData, String fileName, String contentType) throws IOException {
        String fileId = UUID.randomUUID().toString();
        
        String subDir = fileId.substring(0, 2);
        File directory = new File(storageDirectory + File.separator + subDir);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                LOGGER.log(Level.SEVERE, "Failed to create subdirectory: " + subDir);
                throw new IOException("Failed to create subdirectory");
            }
        }
        
        byte[] decodedData = Base64.getDecoder().decode(fileData);
        
        String metadataPath = storageDirectory + File.separator + subDir + File.separator + fileId + ".meta";
        String metadata = "fileName=" + fileName + "\n" +
                          "contentType=" + contentType + "\n" +
                          "size=" + decodedData.length;
        Files.write(Paths.get(metadataPath), metadata.getBytes());
        
        String filePath = storageDirectory + File.separator + subDir + File.separator + fileId;
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(decodedData);
        }
        
        LOGGER.info("File stored: " + fileName + " with ID: " + fileId);
        return fileId;
    }
    
    public byte[] getFileContent(String fileId) throws IOException {
        String subDir = fileId.substring(0, 2);
        String filePath = storageDirectory + File.separator + subDir + File.separator + fileId;
        
        File file = new File(filePath);
        if (!file.exists()) {
            LOGGER.warning("File not found: " + fileId);
            return null;
        }
        
        return Files.readAllBytes(file.toPath());
    }
    
    public String[] getFileMetadata(String fileId) throws IOException {
        String subDir = fileId.substring(0, 2);
        String metadataPath = storageDirectory + File.separator + subDir + File.separator + fileId + ".meta";
        
        File metadataFile = new File(metadataPath);
        if (!metadataFile.exists()) {
            LOGGER.warning("Metadata not found for file: " + fileId);
            return null;
        }
        
        List<String> lines = Files.readAllLines(Paths.get(metadataPath));
        String fileName = "";
        String contentType = "";
        String size = "0";
        
        for (String line : lines) {
            if (line.startsWith("fileName=")) {
                fileName = line.substring("fileName=".length());
            } else if (line.startsWith("contentType=")) {
                contentType = line.substring("contentType=".length());
            } else if (line.startsWith("size=")) {
                size = line.substring("size=".length());
            }
        }
        
        return new String[] { fileName, contentType, size };
    }
    
    public boolean fileExists(String fileId) {
        if (fileId == null || fileId.length() < 2) {
            return false;
        }
        
        String subDir = fileId.substring(0, 2);
        String filePath = storageDirectory + File.separator + subDir + File.separator + fileId;
        
        File file = new File(filePath);
        return file.exists();
    }
    
    public String getOriginalFileName(String fileId) throws IOException {
        String[] metadata = getFileMetadata(fileId);
        return metadata != null ? metadata[0] : null;
    }
    
    public String getContentType(String fileId) throws IOException {
        String[] metadata = getFileMetadata(fileId);
        return metadata != null ? metadata[1] : null;
    }
    
    public long getFileSize(String fileId) throws IOException {
        String[] metadata = getFileMetadata(fileId);
        return metadata != null ? Long.parseLong(metadata[2]) : -1;
    }
}