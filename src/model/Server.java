package model;

import thread.NodeHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class Server {

    private ServerSocket serverSocket;

    public Server(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    public Server(int port) {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addNewPartitionBrokersThroughInput(Scanner scanner, LeaderBroker leaderBroker) {
        int numberOfNewPartitions;
        try {
            System.out.print("Enter number of partition brokers that you want to add:");
            numberOfNewPartitions = scanner.nextInt();
            leaderBroker.addPartitionBrokers(numberOfNewPartitions);
            System.out.println("-------------------------------------------------------------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startUp() {
        try {
            System.out.println("Server Started!");
            LeaderBroker leaderBroker = new LeaderBroker();
            leaderBroker.setNumberOfAcceptablePartitions(4);
            Thread partitionAdditionThread = new Thread(() -> {
                while (true) {
                    Scanner scanner = new Scanner(System.in);
                    addNewPartitionBrokersThroughInput(scanner, leaderBroker);
                }
            });
            partitionAdditionThread.start();

            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                System.out.println("Client connected!: " + socket.getRemoteSocketAddress().toString());
                Thread nodeHandlerThread = new Thread(new NodeHandler(leaderBroker, socket));
                nodeHandlerThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Server server = new Server(5050);
        server.startUp();
    }
}
