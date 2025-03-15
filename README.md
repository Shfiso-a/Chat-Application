# Telegram-Style Java Chat Application

A Java-based chat application that allows direct connections between computers through IP addresses. The application uses Java RMI for communication.


## Requirements

- Java 11 or higher

## Building the Application

To build the application, run the following command in the project directory:

```bash
mvn clean package
```

## Running the Application

### Starting the Server

```bash
java -jar ChatApp-1.0-SNAPSHOT-with-dependencies.jar --server
```

### Starting the Client

```bash
java -jar ChatApp-1.0-SNAPSHOT-with-dependencies.jar
```

## TODO
file sharing has some bugs

voice player has some bug

messages ( inculding profile ) gets reset everytime so we need fix this

