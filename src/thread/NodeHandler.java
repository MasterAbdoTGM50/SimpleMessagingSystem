package thread;

import com.google.gson.Gson;
import enums.Node;
import model.*;

import java.io.*;
import java.net.Socket;

public class NodeHandler implements Runnable{

    private LeaderBroker leaderBroker;
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private int nodeChoice;
    private ObjectOutputStream objectOutputStream;
    private ObjectInputStream objectInputStream;

    public NodeHandler(Socket clientSocket, int nodeChoice) {
        try {
            this.clientSocket = clientSocket;
            this.nodeChoice = nodeChoice;
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.out = new PrintWriter(clientSocket.getOutputStream());
            leaderBroker = new LeaderBroker();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public NodeHandler(LeaderBroker leaderBroker, Socket clientSocket, int nodeChoice) {
        try {
            this.leaderBroker = leaderBroker;
            this.clientSocket = clientSocket;
            this.nodeChoice = nodeChoice;
            this.in = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
            this.out = new PrintWriter(this.clientSocket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public NodeHandler(LeaderBroker leaderBroker, Socket clientSocket) {
        try {
            this.leaderBroker = leaderBroker;
            this.clientSocket = clientSocket;
//            this.in = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
//            this.out = new PrintWriter(this.clientSocket.getOutputStream());
            this.objectOutputStream = new ObjectOutputStream(this.clientSocket.getOutputStream());
//            this.nodeChoice = in.read();
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
        System.out.println("Choice: " + this.nodeChoice);
        if (nodeChoice == Node.LEADER.getValue()){

        }else if (nodeChoice == Node.CONSUMER.getValue()){
            handleConsumerOperations();
        } else if (nodeChoice == Node.PRODUCER.getValue()){
            System.out.println("Handle Producer");
            handleProducerOperations();
        }else {
            throw new RuntimeException();
        }
    }

    public void handleProducerOperations() throws IOException, InterruptedException {
        Gson gson = new Gson();
        while (clientSocket.isConnected()){
            while (objectInputStream.available() != 0) {
                String response = objectInputStream.readUTF();
                System.out.println("response: " + response);
                Message message = gson.fromJson(response, Message.class);
                leaderBroker.configureMessage(message.getText());
                System.out.println("MESSAGE: " + message.getText());
            }
        }
    }

    public void handleConsumerOperations() throws IOException {
        Gson gson = new Gson();
        while (clientSocket.isConnected()){
            while (objectInputStream.available() != 0) {
                String clientRequest = objectInputStream.readUTF();
                System.out.println("Client request: " + clientRequest);
                Consumption consumption = gson.fromJson(clientRequest, Consumption.class);
                System.out.println("consumption: " + consumption.getConsumptionId());
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
