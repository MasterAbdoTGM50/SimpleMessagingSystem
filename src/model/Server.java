package model;

import thread.NodeHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    private ServerSocket serverSocket;
//    private LeaderBroker leaderBroker;

//    public Server(ServerSocket serverSocket, LeaderBroker leaderBroker) {
    public Server(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
//        this.leaderBroker = leaderBroker;
    }

//    public Server(LeaderBroker leaderBroker) {
//        this.leaderBroker = leaderBroker;
//    }

    public Server(int port){
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    public void configureMessage(String text){
//        leaderBroker.configureMessage(text);
//    }

    public void startUp(){
        try{
            System.out.println("Server Started!");
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                System.out.println("Client connected!: " + socket.getRemoteSocketAddress().toString());
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                int choice = bufferedReader.read();
                System.out.println("Choice: " + choice);

//                Thread nodeHandlerThread = new Thread(new NodeHandler(socket, choice));
                Thread nodeHandlerThread = new Thread(new NodeHandler(socket, choice));
                nodeHandlerThread.start();

            }
        }catch (IOException e){

        }
    }

    public static void main(String[] args){
        Server server = new Server(5050);
        server.startUp();
    }

}
