package thread;

import com.google.gson.Gson;
import enums.Node;
import exception.InvalidOperationException;
import model.*;

import java.io.*;
import java.net.Socket;

public class NodeHandler implements Runnable {

    private LeaderBroker leaderBroker;
    private Socket clientSocket;
    private int nodeChoice;
    private ObjectOutputStream objectOutputStream;
    private ObjectInputStream objectInputStream;

    public NodeHandler(LeaderBroker leaderBroker, Socket clientSocket) {
        try {
            this.leaderBroker = leaderBroker;
            this.clientSocket = clientSocket;
            this.objectOutputStream = new ObjectOutputStream(this.clientSocket.getOutputStream());
            this.objectInputStream = new ObjectInputStream(this.clientSocket.getInputStream());
            this.nodeChoice = objectInputStream.readInt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            handleOperations();
        } catch (InvalidOperationException e) {
            close();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void close() {
        try {
            clientSocket.close();
            objectOutputStream.close();
            objectInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void handleOperations() throws IOException {
        if (nodeChoice == Node.CONSUMER.getValue()) {
            handleConsumerOperations();
        } else if (nodeChoice == Node.PRODUCER.getValue()) {
            handleProducerOperations();
        } else if (nodeChoice == Node.PARTITION.getValue()) {
            handlePartitionOperations();
        } else {
            throw new InvalidOperationException("Invalid Operation choice was received");
        }
    }

    public void handleProducerOperations() throws IOException {
        Gson gson = new Gson();
        while (clientSocket.isConnected()) {
            while (objectInputStream.available() != 0) {
                String response = objectInputStream.readUTF();
                Message message = gson.fromJson(response, Message.class);
                leaderBroker.configureMessage(message.getText());
            }
        }
    }

    public void handleConsumerOperations() throws IOException {
        Gson gson = new Gson();
        while (!clientSocket.isClosed()) {
            while (objectInputStream.available() != 0) {
                String clientRequest = objectInputStream.readUTF();
                Consumption consumption = gson.fromJson(clientRequest, Consumption.class);
                leaderBroker.serveConsumerConsumption(consumption, objectOutputStream);
            }
        }
    }

    public synchronized void handlePartitionOperations() throws IOException {
        if (leaderBroker.getNumberOfPartitions() < leaderBroker.getNumberOfAcceptablePartitions()) {
            leaderBroker.addPartitionBroker(clientSocket, objectOutputStream, objectInputStream);
            return;
        }

        if (leaderBroker.getNumberOfQueuePartitions() == LeaderBroker.PARTITION_QUEUE_CAPACITY) {
            clientSocket.close();
            return;
        } else {
            leaderBroker.addToPartitionQueue(clientSocket, objectOutputStream, objectInputStream);
        }

        new Thread(() -> {
            while (!clientSocket.isClosed()) {

            }
            leaderBroker.removeDisconnectedSocket(clientSocket);
        }).start();

    }
}
