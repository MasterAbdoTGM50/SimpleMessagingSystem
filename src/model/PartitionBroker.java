package model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import enums.Node;
import exception.InvalidOperationException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;

public class PartitionBroker {

    private List<Message> messages;
    private Socket partitionSocket;
    private ObjectInputStream objectInputStream;
    private ObjectOutputStream objectOutputStream;
    private Object lock1 = new Object();

    public void connect(String ip, int port) {
        try {
            partitionSocket = new Socket(ip, port);
            objectOutputStream = new ObjectOutputStream(partitionSocket.getOutputStream());
            objectInputStream = new ObjectInputStream(partitionSocket.getInputStream());
            objectOutputStream.writeInt(Node.PARTITION.getValue());
            objectOutputStream.flush();
            messages = new ArrayList<>();
            System.out.println("Partition Connected!");
            Gson gson = new Gson();
            while (!partitionSocket.isClosed()) {
                while (isNonEmptyBuffer()) {
                    String request = listenForRequests();
                    JsonObject requestJson = gson.fromJson(request, JsonObject.class);
                    if (requestJson.has("messages")) {
                        synchronized (lock1) {
                            Message message = gson.fromJson(requestJson.get("messages"), Message.class);
                            messages.add(message);
                        }
                    } else if (requestJson.has("more")) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("messages", messages);
                        String responseAsString = gson.toJson(response);
                        objectOutputStream.writeUTF(responseAsString);
                        objectOutputStream.flush();
                    } else if (requestJson.has("sizeOfMessages")) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("size", messages.size());
                        String responseAsString = gson.toJson(response);
                        objectOutputStream.writeUTF(responseAsString);
                        objectOutputStream.flush();
                    } else {
                        throw new InvalidOperationException("Invalid request operation");
                    }
                }
            }
        } catch (IOException | InvalidOperationException e) {
            e.printStackTrace();
        }
    }


    public String listenForRequests() throws IOException {
        return objectInputStream.readUTF();
    }

    public boolean isNonEmptyBuffer() {
        try {
            return objectInputStream.available() != 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }


    public PartitionBroker() {
        messages = new ArrayList<>();
    }

    public static void main(String[] args) {

        PartitionBroker partitionBroker = new PartitionBroker();
        partitionBroker.connect("localhost", 5050);

    }
}
