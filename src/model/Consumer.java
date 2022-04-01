package model;

import com.google.gson.Gson;
import enums.Node;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Consumer{

    private Socket consumerSocket;
    private String consumptionId;
    private BufferedReader in;
    private PrintWriter out;

    public Consumer() {
        consumptionId = "";
    }

    public Consumer(String consumptionId) {
        this.consumptionId = consumptionId;
    }

    public void connect(String ip, int port){
        Gson gson = new Gson();
        try {
            consumerSocket = new Socket(ip, port);
            in = new BufferedReader(new InputStreamReader(consumerSocket.getInputStream()));
            out = new PrintWriter(consumerSocket.getOutputStream(), true);
            out.write(Node.CONSUMER.getValue());

            consume();
            List<Message> messages = gson.fromJson(listenForResponse(), List.class);
            messages.forEach(m -> System.out.println(m));
            Thread.sleep(1000 * 30);
//            out.flush();

//            while (consumerSocket.isConnected()){
//
//            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void consume(){
        Gson gson = new Gson();
        Map<String, String> request = new HashMap<>();
        request.put("consumptionId", consumptionId);
        String serializedRequest = gson.toJson(request);
        out.print(serializedRequest);
    }

    public String listenForResponse() throws IOException {
        return in.readLine();
    }

    public static void main(String[] args) {

    }

}
