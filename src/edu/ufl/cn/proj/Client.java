package edu.ufl.cn.proj;

import edu.ufl.cn.proj.protocol.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client implements Runnable, ThreadSafeSocketReaderWriter.OnReceivedCallback, ThreadSafeSocketReaderWriter.OnCloseCallback{
    private final String srvAddr;
    private final int srvPort;
    private final String clientName;
    private Socket conn;
    private ThreadSafeSocketReaderWriter socketRW;
    private final ExecutorService executors;
    private static final int numExecutors = 25;
    private String str;
    private Map<String, DownloadFileTask> downloadFileTaskMap;
    private Set<UploadFileTask> uploadFileTasks;

    private Client(String srvAddr, String srvPort, String clientName){
        this.srvAddr = srvAddr;
        this.clientName = clientName;
        int port = Integer.parseInt(srvPort);
        if(port<0 || port>65536)
            throw new IllegalArgumentException("Wrong port range");
        this.srvPort = port;
        this.executors = Executors.newFixedThreadPool(numExecutors);

        str = "Command usage: ";
        str+="\tunicast <To-clientName> <message>"+System.lineSeparator();
        str+="\tunicast_file <To-clientName> <fileLocation>"+System.lineSeparator();
        str+="\tbroadcast <message>"+System.lineSeparator();
        str+="\tbroadcast_file <message>"+System.lineSeparator();
        str+="\tblockcast <blocked-clientName> <message>"+System.lineSeparator();
        str+="\tblockcast_file <blocked-clientName> <fileLocation>"+System.lineSeparator();
        downloadFileTaskMap = new ConcurrentHashMap<>();
        uploadFileTasks = new HashSet<>();
    }

    @Override
    public void onClose(Socket closedSocket, String id) {
        writeLineToConsole("Connection closed by server");
        gracefulExit();
        System.exit(1);
    }

    private class ClientFileDownloadTask extends DownloadFileTask{
        public ClientFileDownloadTask(String source, String downloadFileName) {
            super(source, downloadFileName);
        }

        @Override
        protected void onComplete(Void result) {
            StringBuilder sb = new StringBuilder();
            sb.append("File with id: ").append(fileData.getId()).append(" from ").append(source).append(" saved as ").append(downloadFileName);
            downloadFileTaskMap.remove(uniqueFileId(source, fileData.getId()));
            writeLineToConsole(sb.toString());
        }

        @Override
        protected void onProgressUpdate(Double progress) {
            StringBuilder sb = new StringBuilder();
            DecimalFormat df = new DecimalFormat("#.##");
            sb.append("File with id: ").append(fileData.getId()).append(" from ").append(source).append(" downloading ").append(df.format(progress)).append("% complete");
            writeLineToConsole(sb.toString());
        }

        @Override
        protected void onException(Exception e) {
            StringBuilder sb = new StringBuilder();
            sb.append("File with id: ").append(fileData.getId()).append(" from ").append(source).append(" download failed with error. Error: ").append(e.getMessage());
            //downloadFileTaskMap.remove(uniqueFileId(source, fileData.getId()));
            writeLineToConsole(sb.toString());
        }

        @Override
        protected void onCancelled() {
            try{
                downloadFileTaskMap.remove(uniqueFileId(source, fileData.getId()));
                deleteFile();
                StringBuilder sb = new StringBuilder();
                sb.append("File with id: ").append(fileData.getId()).append(" from ").append(source).append(" cancelled.");
                writeLineToConsole(sb.toString());
            }catch(IOException e) {//swallow}
            }

        }
    }

    private class ClientFileUploadTask extends UploadFileTask{
        public ClientFileUploadTask(Message msgWithoutData, String uploadFileName, ThreadSafeSocketReaderWriter socketWriter) {
            super(msgWithoutData, uploadFileName, socketWriter);
        }
        @Override
        protected void onComplete(Void result) {

            StringBuilder sb = new StringBuilder();
            sb.append("File with id: ").append(fd.getId()).append(" successfully sent");
            uploadFileTasks.remove(this);
            writeLineToConsole(sb.toString());
        }

        @Override
        protected void onProgressUpdate(Double progress) {
            StringBuilder sb = new StringBuilder();
            DecimalFormat df = new DecimalFormat("#.##");
            sb.append("File with id: ").append(fd.getId()).append(" sending, ").append(df.format(progress)).append("% complete");
            writeLineToConsole(sb.toString());
        }

        @Override
        protected void onException(Exception e) {
            super.onException(e);
            uploadFileTasks.remove(this);
            StringBuilder sb = new StringBuilder();
            if(fd!=null)
                sb.append("File with id: ").append(fd.getId()).append(" sending failed with error. Error: ").append(e.getMessage());
            else sb.append("File name: ").append(uploadFileName).append(" sending failed with error. Error: ").append(e.getMessage());
            writeLineToConsole(sb.toString());
        }

        @Override
        protected void onCancelled() {
        }
    }

    public static void main(String args[]){
        if(args.length<2){
            System.out.println("java Client  <server-address> <port#> [client-name]");
        }else{
            try{
                String cName = args.length >= 3 ? args[2] : UUID.randomUUID().toString().substring(0,6);
                Client client = new Client(args[0], args[1], cName);
                client.connect();
                Thread clientCmdLsnr = new Thread(client);
                clientCmdLsnr.start();
            }catch(IllegalArgumentException e){
                System.out.println("Wrong number format for port#, make sure port# is integer and ranges from 0 to 65536.");
                System.out.println("OR Client Name already being used.");
                System.exit(1);
            }catch (SocketTimeoutException e){
                System.out.print("Connection to the server timed-out");
            }catch (IOException e){
                System.out.println("Failed to connect to server");
                e.printStackTrace();
            }
        }
    }

    public void connect() throws IOException{
        conn = new Socket(srvAddr, srvPort);
        writeLineToConsole("Connected to server");
        socketRW = new ThreadSafeSocketReaderWriter(conn);
        Message msg = new Message(clientName, srvAddr, OperationCode.INIT, Login.newInstance(clientName));
        socketRW.write(msg);
        Message resp = socketRW.read();
        if(resp.getOpCode().equals(OperationCode.ERR)) {
            throw new IllegalArgumentException("Failed to Connect to Server, err: " + resp.dataAsError().getErrMsg());
        }
        socketRW.setId(clientName);
        socketRW.registerOnCloseCallback(this);
        socketRW.startListening(this);
    }

    public synchronized void writeLineToConsole(String message){
        System.out.println(clientName+">> "+message);
    }

    private void sendTextMessage(String target, OperationCode opCode, String[] message){
        try{
            StringBuilder textMsg = new StringBuilder();
            for(int i=0; i<message.length;i++) {
                if(i!=0)
                    textMsg.append(" ");
                textMsg.append(message[i]);
            }

            Message msg = new Message(clientName, target, opCode,
                    TextMessage.newInstance(textMsg.toString()));
            writeLineToConsole("Sending message with id: "+msg.getData().getId());
            socketRW.write(msg);
        } catch (IOException e){
            onClose(conn, clientName);
        }

    }

    private void sendFile(String target, OperationCode opCode, String fileLocation){
        Message msgWithoutData = new Message(clientName, target, opCode, null);
        ClientFileUploadTask task = new ClientFileUploadTask(msgWithoutData, fileLocation, socketRW);
        uploadFileTasks.add(task);
        task.execute(executors, null);
    }

    private String uniqueFileId(String source, int id){
        return source+":"+id;
    }

    private synchronized String ensureUniqueFileName(String fileName){
        File file = new File(clientName);
        if(!file.exists()){
            file.mkdirs();
        }
        String uniqueFileName = fileName;
        for(int i=1;new File(clientName+"/"+uniqueFileName).exists();i++){
            if(fileName.contains(".")){
                int indexOfPeroid = fileName.lastIndexOf(".");
                uniqueFileName = fileName.substring(0, indexOfPeroid)+"("+ i +")."+fileName.substring(indexOfPeroid+1,fileName.length());
            } else {
                uniqueFileName = fileName+"("+ i +")";
            }
        }
        return clientName+"/"+uniqueFileName;
    }


    private void downloadFile(Message msg){
        String downloadId = uniqueFileId(msg.getSource(), msg.getData().getId());
        DownloadFileTask task = downloadFileTaskMap.get(downloadId);
        String fileName = null;
        if(task == null && msg.dataAsFileData().getChunk().getId() == 1){
            fileName = ensureUniqueFileName(msg.dataAsFileData().getFileName());
            task = new ClientFileDownloadTask(msg.getSource(), fileName);
            downloadFileTaskMap.put(downloadId, task);
            StringBuilder sb = new StringBuilder();
            switch(msg.getOpCode()){
                case BLOCKCAST_FILE:
                    sb.append("<Blockcast:").append(msg.getTarget()).append("><");
                    break;
                case BROADCAST_FILE:
                    sb.append("<Broadcast><");
                    break;
                case UNICAST_FILE:
                    sb.append("<");
                    break;
            }
            sb.append(msg.getSource()).append(">:").append(" File download started: ")
                    .append(msg.dataAsFileData().getId()).append(":")
                    .append(fileName);
            writeLineToConsole(sb.toString());
            task.execute(executors, msg.dataAsFileData());
        } else task.updateProgress(msg.dataAsFileData());
    }

    @Override
    public void run() {
        writeLineToConsole("Command listener started");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while(true){
            try{
                String line = br.readLine();
                String[] command = line.trim().replace(" +"," ").split(" ");
                if(command.length>0)
                    switch(command[0]){
                        case "unicast":
                            if(command.length > 2){
                                String target = command[1];
                                sendTextMessage(target, OperationCode.UNICAST_MESSAGE,
                                        Arrays.copyOfRange(command,2,command.length));
                            } else showUsage();
                            break;
                        case "unicast_file":
                            if(command.length > 2){
                                String target = command[1];
                                sendFile(target,OperationCode.UNICAST_FILE, command[2]);
                            } else showUsage();
                            break;
                        case "broadcast":
                            if(command.length > 1){
                                sendTextMessage(null, OperationCode.BROADCAST_MESSAGE,
                                        Arrays.copyOfRange(command,1,command.length));
                            } else showUsage();
                            break;
                        case "broadcast_file":
                            if(command.length > 1){
                                sendFile(null,OperationCode.BROADCAST_FILE, command[1]);
                            } else showUsage();
                            break;
                        case "blockcast_file":
                            if(command.length > 2){
                                String target = command[1];
                                sendFile(target, OperationCode.BLOCKCAST_FILE, command[2]);
                            } else showUsage();
                            break;
                        case "blockcast":
                            if(command.length > 2){
                                String target = command[1];
                                sendTextMessage(target, OperationCode.BLOCKCAST_MESSAGE,
                                        Arrays.copyOfRange(command,2,command.length));
                            } else showUsage();
                            break;
                        case "exit":
                            gracefulExit();
                            break;
                        default:
                            writeLineToConsole("Unknown Command: "+ line);
                            showUsage();
                    }
                else showUsage();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    private synchronized void gracefulExit() {
        for(UploadFileTask task: uploadFileTasks)
            task.cancel();
        for(DownloadFileTask task: downloadFileTaskMap.values())
            task.cancel();
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.exit(1);

    }

    public void showUsage(){
        writeLineToConsole(str);
    }

    @Override
    public void onReceived(Message message, String id) {
        TextMessage tm;
        StringBuilder sb;
        switch (message.getOpCode()){
            case ACK:
                if(message.dataAsTextMessage()!=null){
                    tm = message.dataAsTextMessage();
                    sb = new StringBuilder();
                    sb.append("Text message with id: ").append(tm.getId()).append(" successfully sent.");
                    writeLineToConsole(sb.toString());
                }
                if(message.dataAsFileData()!=null){
                    tm = message.dataAsTextMessage();
                    sb = new StringBuilder();
                    sb.append("File with id: ").append(tm.getId()).append(" successfully uploaded.");
                    writeLineToConsole(sb.toString());
                }
                break;
            case UNICAST_MESSAGE:
                tm = message.dataAsTextMessage();
                sb = new StringBuilder();
                sb.append("<").append(message.getSource()).append(">:").append(tm.getMessage());
                writeLineToConsole(sb.toString());
                break;
            case UNICAST_FILE:
                downloadFile(message);
                break;
            case BROADCAST_MESSAGE:
                tm = message.dataAsTextMessage();
                sb = new StringBuilder();
                sb.append("<Broadcast><").append(message.getSource()).append(">:").append(tm.getMessage());
                writeLineToConsole(sb.toString());
                break;
            case BROADCAST_FILE:
                downloadFile(message);
                break;
            case BLOCKCAST_MESSAGE:
                tm = message.dataAsTextMessage();
                sb = new StringBuilder();
                sb.append("<Blockcast:").append(message.getTarget()).append("><").append(message.getSource()).append(">:").append(tm.getMessage());
                writeLineToConsole(sb.toString());
                break;
            case BLOCKCAST_FILE:
                downloadFile(message);
                break;
            case ERR:
                sb = new StringBuilder();
                sb.append("Error message received by server. Message: ").append(message.dataAsError().getErrMsg());
                writeLineToConsole(sb.toString());
                break;
        }
    }
}
