package edu.ufl.cn.proj;

import edu.ufl.cn.proj.protocol.*;
import edu.ufl.cn.proj.protocol.Error;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.*;

public class Server implements Runnable, ThreadSafeSocketReaderWriter.OnCloseCallback, ThreadSafeSocketReaderWriter.OnReceivedCallback{
    private final int listenPort;
    private ServerSocket listenSocket;
    private final ScheduledExecutorService newClientExecs;
    private ConcurrentHashMap<String, ThreadSafeSocketReaderWriter> clientList;
    private ConcurrentHashMap<String, ConcurrentHashMap<Integer,Message>> fileStreams;
    private static final String SERVER_NAME = "Server";
    private static final int newClientThreads = 10;
    private Server(String portNum){
        int port = Integer.parseInt(portNum);
        if(port<0 || port>65536)
            throw new IllegalArgumentException("Wrong port range");
        listenPort = port;
        newClientExecs = Executors.newScheduledThreadPool(newClientThreads);
        clientList = new ConcurrentHashMap<>();
//        fileExecs = Executors.newFixedThreadPool(fileProcessors);
//        broadcastExecs = Executors.newFixedThreadPool(broadCastProcessors);
//        lightWeightExecs  = Executors.newFixedThreadPool(lightWeightProcessors);
        fileStreams = new ConcurrentHashMap<>();

    }

    public static void main(String[] args) {
        if(args.length<1) {
            System.out.println("java Server <port#>");
        } else {
            try{
                Server srv = new Server(args[0]);
                Thread srvLstn = new Thread(srv);
                srvLstn.start();
            }catch(IllegalArgumentException e){
                System.out.println("Wrong number format for port#, make sure port# is integer and ranges from 0 to 65536");
                System.exit(1);
            }
        }


    }

    @Override
    public void run() {
        try{
            listenSocket = new ServerSocket(listenPort);
            writeLineToConsole("Server listening on port#: "+listenPort);
        }catch(IOException e){
            writeLineToConsole("Failed to listen on port#: " + listenPort +" shutting down the server.");
            kill(1);
        }
        try{
            while(true){
                final Socket newClientSocket = listenSocket.accept();
                newClientExecs.submit(()->{
                    try {
                        final ThreadSafeSocketReaderWriter clientRW = new ThreadSafeSocketReaderWriter(newClientSocket);
                        Message msg = clientRW.read();
                        String clientId = null;
                        boolean loginSuccess = false;
                        try{
                            if(msg.getData() instanceof Login){
                                clientId =((Login) msg.getData()).getLogin();
                                if(msg.getSource().equals(clientId)) {
                                    newClientLogin(clientId, clientRW);
                                    clientRW.write(new Message(SERVER_NAME, clientId, OperationCode.ACK,msg.getData()));
                                    clientRW.setId(clientId);
                                    clientRW.registerOnCloseCallback(this);
                                    clientRW.startListening(this);
                                    loginSuccess = true;
                                }
                            }
                            if(!loginSuccess){
                                newClientExecs.schedule(()->{
                                    try{clientRW.close();}catch (IOException e){writeLineToConsole("Failed to close connection");}
                                }, 2, TimeUnit.SECONDS);
                            }
                        } catch(RuntimeException r){
                            System.out.println("Failed to setup initial connection, Illegal ClientId");
                            clientRW.write(new Message(SERVER_NAME, clientId, OperationCode.ERR, Error.newInstance(Error.DUPLICATE_ID, "client with same Id already logged in ")));
                            newClientExecs.schedule(()->{
                                try{clientRW.close();}catch (IOException e){writeLineToConsole("Failed to close connection");}
                            }, 2, TimeUnit.SECONDS);
                        }

                    }catch(IOException e){
                        e.printStackTrace();
                        writeLineToConsole("Failed to setup initial connection");
                    }
                });
            }
        }catch(IOException e){
            writeLineToConsole("Failed to listen on new incoming connections, stopping server..");
            kill(1);
        }



    }

    private void newClientLogin(String clientId, ThreadSafeSocketReaderWriter clientRW){
        if(clientList.get(clientId)!=null || clientId.equalsIgnoreCase(SERVER_NAME)){
            //Client with the same name already registered
            throw new IllegalArgumentException("Client Id already registered");
        }
        clientList.put(clientId, clientRW);
        fileStreams.put(clientId, new ConcurrentHashMap<>());

    }

    private void kill(int code){
        System.exit(code);
    }

    public synchronized void writeLineToConsole(String message){
        System.out.println(SERVER_NAME+">> "+message);
    }

    @Override
    public void onClose(Socket closedSocket, String id) {
        clientList.remove(id);
        Map<Integer, Message> openStreams = fileStreams.get(id);
        for(Message fileMessage: openStreams.values()){
            FileData fileData = fileMessage.dataAsFileData().newChuck(new byte[0]);
            fileData.setDiscard();
            Message discardMsg = new Message(fileMessage.getSource(), fileMessage.getTarget(),
                    fileMessage.getOpCode(), fileData);
            switch(fileMessage.getOpCode()){
                case BROADCAST_FILE:
                    sendBroadcastMessage(discardMsg);
                    break;
                case BLOCKCAST_FILE:
                    sendBlockcastMessage(discardMsg);
                    break;
                case UNICAST_FILE:
                    sendUnicastMessage(discardMsg);
                    break;
            }
        }
        String str = new StringBuilder().append("Client:").append(id).append(" disconnected").toString();
        writeLineToConsole(str);
        //May be we can notify other clients of logout
        sendBroadcastMessage(new Message(SERVER_NAME, null, OperationCode.BROADCAST_MESSAGE, TextMessage.newInstance(str)));
    }

    private void sendUnicastMessage(Message msg){
        ThreadSafeSocketReaderWriter targetSocket = clientList.get(msg.getTarget());
        ThreadSafeSocketReaderWriter sourceSocket = clientList.get(msg.getSource());
        try {
            targetSocket.write(msg);
            Message ack = new Message(SERVER_NAME, msg.getSource(), OperationCode.ACK, msg.getData());
            
            if(msg.dataAsFileData()!=null){
                fileStreamProcess(msg);
            }
            
            //Makes sure to sent ACK for file only after last chunk of file is sent
            if(msg.dataAsFileData() == null ||
                    msg.dataAsFileData().isDiscard() || msg.dataAsFileData().isEndOfFile()){
                sourceSocket.write(ack);
            }
        } catch (IOException e) {
            writeLineToConsole("Failed to write to socket");
        }
    }

    private boolean isIncludedInBroadcast(String source, String target, Message msg){
        if(source.equals(target))
            return false;
        if((msg.getOpCode() == OperationCode.BLOCKCAST_FILE || msg.getOpCode() == OperationCode.BLOCKCAST_MESSAGE)
            && msg.getTarget().equals(target))
            return false;
        return true;
    }

    private void sendBlockcastMessage(Message msg){
        sendMessageToTargets(msg);
    }
    private void sendBroadcastMessage(Message msg){
        sendMessageToTargets(msg);
    }
    private void sendMessageToTargets(Message msg){
        ThreadSafeSocketReaderWriter sourceSocket = clientList.get(msg.getSource());
        try {
            if(msg.dataAsFileData()!=null){
                fileStreamProcess(msg);
            }
            boolean allSent = true;
            for(Map.Entry<String, ThreadSafeSocketReaderWriter> entry : clientList.entrySet()){
                try {
                    if(isIncludedInBroadcast(msg.getSource(), entry.getKey(), msg)){
                        ThreadSafeSocketReaderWriter targetSocket = entry.getValue();
                        if(targetSocket != null)
                            targetSocket.write(msg);
                    }
                } catch(IOException e){
                    allSent = false;
                }
            }
            //Makes sure to sent ACK for file only after last chunk of file is sent
            if(msg.dataAsFileData()==null ||
                    msg.dataAsFileData().isDiscard() || msg.dataAsFileData().isEndOfFile()){
                if(allSent){
                    Message ack = new Message(SERVER_NAME, msg.getSource(), OperationCode.ACK, msg.getData());
                    if(sourceSocket!= null) //Can be null if server is sending broadcast message
                        sourceSocket.write(ack);
                } else {
                    Message err = new Message(SERVER_NAME, msg.getSource(),
                            OperationCode.ERR, Error.newInstance(Error.PARTIAL_TARGETS_SENT, "Sent to partial targets"));
                    if(sourceSocket!= null)//Can be null if server is sending broadcast message
                        sourceSocket.write(err);
                }
            }
        } catch (IOException e) {
            writeLineToConsole("Failed to write to socket");
        }
    }

    private void fileStreamProcess(Message msg) {
        ConcurrentHashMap<Integer, Message> fStreamSource = fileStreams.get(msg.getSource());
        if(fStreamSource != null){ // can be null if the client is disconnected
            if(msg.dataAsFileData().isDiscard() || msg.dataAsFileData().isEndOfFile())
                fStreamSource.remove(msg.dataAsFileData().getId());
            else fStreamSource.put(msg.dataAsFileData().getId(), msg);
        }
    }

    @Override
    public void onReceived(Message message, String id) {
        final ThreadSafeSocketReaderWriter rw = clientList.get(id);
        try {
            message = validate(message, id);
            final Message msg = message;
            switch (message.getOpCode()){
                case UNICAST_MESSAGE:
                    sendUnicastMessage(msg);
                    break;
                case BROADCAST_MESSAGE:
                    sendBroadcastMessage(msg);
                    break;
                case BLOCKCAST_MESSAGE:
                    sendBlockcastMessage(msg);
                    break;
                case BLOCKCAST_FILE:
                    sendBlockcastMessage(msg);
                    break;
                case UNICAST_FILE:
                    sendUnicastMessage(msg);
                    break;
                case BROADCAST_FILE:
                    sendBroadcastMessage(msg);
                    break;
            }
        } catch(IllegalArgumentException e){
            if(e.getMessage().startsWith("Masquerading"))
                message = new Message(SERVER_NAME, id, OperationCode.ERR,
                        Error.newInstance(Error.MASQUERADING_AS_OTHER_CLIENT, e.getMessage()));
            else message = new Message(SERVER_NAME, id, OperationCode.ERR,
                    Error.newInstance(Error.VALIDATION_FAILED, e.getMessage()));
            final Message errMsg = message;
            if(rw != null) {
                try {rw.write(errMsg);} catch (IOException ioe) {writeLineToConsole("Failed to send error message");}
            }
        }

    }

    private Message validate(Message message, String id) {
        //If message source missing fill it in
        if(message.getSource() == null){
            message = new Message(id, message.getTarget(), message.getOpCode(), message.getData());
        }
        //Validate if target is present when required.
        if(isTargetAddrRequired(message.getOpCode()) &&
                (message.getTarget() == null || clientList.get(message.getTarget())==null))
            throw new IllegalArgumentException("Missing or invalid target Address when required");
        if(isOperationBlockedForClients(message.getOpCode()))
            throw new IllegalArgumentException("Invalid Operation");
        if(!message.getSource().equals(id))
            throw new IllegalArgumentException("Masquerading as other Client");
        if(message.getData() == null)
            throw new IllegalArgumentException("Missing data");
        if(clientList.get(id) == null)
            throw new IllegalArgumentException("Already closed the connection to client");

        return message;
    }

    private boolean isTargetAddrRequired(OperationCode op){
        return (op == OperationCode.UNICAST_FILE || op == OperationCode.UNICAST_MESSAGE ||
                    op == OperationCode.BLOCKCAST_MESSAGE || op ==OperationCode.BLOCKCAST_FILE);
    }

    private boolean isOperationBlockedForClients(OperationCode op){
        return !(op == OperationCode.UNICAST_FILE || op == OperationCode.UNICAST_MESSAGE ||
                op == OperationCode.BLOCKCAST_MESSAGE || op == OperationCode.BLOCKCAST_FILE ||
                op == OperationCode.BROADCAST_MESSAGE || op == OperationCode.BROADCAST_FILE);
    }
}
