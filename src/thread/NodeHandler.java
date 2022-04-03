package thread;

import com.google.gson.Gson;
import enums.Node;
import model.*;

import java.io.*;
import java.net.Socket;

public class NodeHandler implements Runnable{

    private LeaderBroker leaderBroker;
    private Socket clientSocket;
    private int nodeChoice;
    private ObjectOutputStream objectOutputStream;
    private ObjectInputStream objectInputStream;

    public NodeHandler(Socket clientSocket, int nodeChoice) {
        this.clientSocket = clientSocket;
        this.nodeChoice = nodeChoice;
        leaderBroker = new LeaderBroker();
    }

    public NodeHandler(LeaderBroker leaderBroker, Socket clientSocket, int nodeChoice) {
        this.leaderBroker = leaderBroker;
        this.clientSocket = clientSocket;
        this.nodeChoice = nodeChoice;
    }

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
        } catch (IOException | RuntimeException | InterruptedException e) {
            e.printStackTrace();
        }

    }

    public void handleOperations() throws IOException, InterruptedException {
        if (nodeChoice == Node.CONSUMER.getValue()){
            handleConsumerOperations();
        } else if (nodeChoice == Node.PRODUCER.getValue()){
            handleProducerOperations();
        }else {
            throw new RuntimeException();
        }
    }

    public void handleProducerOperations() throws IOException {
        Gson gson = new Gson();
        while (clientSocket.isConnected()){
            while (objectInputStream.available() != 0) {
                String response = objectInputStream.readUTF();
                Message message = gson.fromJson(response, Message.class);
                leaderBroker.configureMessage(message.getText());
            }
        }
    }

    public void handleConsumerOperations() throws IOException {
        Gson gson = new Gson();
        while (clientSocket.isConnected()){
            while (objectInputStream.available() != 0) {
                String clientRequest = objectInputStream.readUTF();
                Consumption consumption = gson.fromJson(clientRequest, Consumption.class);
                leaderBroker.serveConsumerConsumption(consumption, objectOutputStream);
            }
        }
    }

    public int getNodeChoice() {
        return nodeChoice;
    }

    public void setNodeChoice(int nodeChoice) {
        this.nodeChoice = nodeChoice;
    }
}
