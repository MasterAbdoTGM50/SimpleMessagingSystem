package thread;

import com.google.gson.Gson;
import enums.Node;
import model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class NodeHandler implements Runnable{

    private LeaderBroker leaderBroker;
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private int nodeChoice;

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
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.out = new PrintWriter(clientSocket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        try {
            handleOperations();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }

    }

    public void handleOperations() throws IOException {
        if (nodeChoice == Node.LEADER.getValue()){

        }else if (nodeChoice == Node.CONSUMER.getValue()){
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
            String response = in.readLine();
            Message message = gson.fromJson(response, Message.class);
            leaderBroker.configureMessage(message.getText());
        }
    }

    public void handleConsumerOperations() throws IOException {
        Gson gson = new Gson();
        while (clientSocket.isConnected()){
            String clientRequest = in.readLine();
            Consumption consumption = gson.fromJson(clientRequest, Consumption.class);
            leaderBroker.serveConsumerConsumption(consumption);
        }
    }

    public int getNodeChoice() {
        return nodeChoice;
    }

    public void setNodeChoice(int nodeChoice) {
        this.nodeChoice = nodeChoice;
    }
}
