package edu.ufl.cn.proj;

import edu.ufl.cn.proj.protocol.Message;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadSafeSocketReaderWriter{
    private final Socket socket;
    private final Lock rLock;
    private String id;
    private final ObjectOutputStream serializer;
    private final ObjectInputStream deserializer;
    private OnCloseCallback onCloseCallback;
    private OnReceivedCallback onReceivedCallback;
    private final AtomicBoolean stopped;
    private final AtomicBoolean started;
    private final AtomicBoolean writeStarted;
    private final Thread writeThread;
    private final BlockingQueue<Message> writeQueue;
    public interface OnCloseCallback{
        void onClose(Socket closedSocket, String id);
    }

    public interface OnReceivedCallback{
        void onReceived(Message message, String id);
    }

    public ThreadSafeSocketReaderWriter(Socket socket)  throws IOException{
        this.socket = socket;
        rLock = new ReentrantLock();
        serializer = new ObjectOutputStream(socket.getOutputStream());
        deserializer = new ObjectInputStream(socket.getInputStream());
        stopped = new AtomicBoolean(false);
        started = new AtomicBoolean(false);
        writeQueue = new ArrayBlockingQueue<>(1000,true);
        writeStarted = new AtomicBoolean(false);
        writeThread = new Thread(()->writeToSocket());
    }

    private void writeToSocket(){
        try {
            while(!stopped.get()) {
                Message msg = writeQueue.take();
                serializer.writeObject(msg);
                serializer.flush();
            }
        }catch (InterruptedException e) {
            //Do nothing here, just log the close event perhaps
        }catch(IOException e){
            if(onCloseCallback != null)
                onCloseCallback.onClose(socket, id);
            stopped.set(true);
        }
    }

    public void setId(String id){
        this.id = id;
    }

    public void registerOnCloseCallback(OnCloseCallback callback){
        this.onCloseCallback = callback;
    }

    public void startListening(OnReceivedCallback callback) {
        this.onReceivedCallback = callback;
        new Thread(()->{
            started.set(true);
            while(!stopped.get()){
                try {
                    Message msg = readObject();
                    onReceivedCallback.onReceived(msg, id);
                }catch (Exception e){
                    //Swallow the exception as we cannot do anything here
                    //May be a good idea to log it
                    //e.printStackTrace();
                }
            }
        }).start();
    }


    public void write(Message msg) throws IOException{
        if(!writeStarted.getAndSet(true))
            writeThread.start();
        try {
            writeQueue.put(msg);
        } catch (InterruptedException e) {
            throw new IOException("Write thread interrupted");
        }
    }

    private Message readObject()throws IOException, UnsupportedOperationException {
        Message msg = null;
        try{
            rLock.lock();
            msg = (Message) deserializer.readObject();
        } catch (IOException e){
            if(onCloseCallback != null)
                onCloseCallback.onClose(socket, id);
            stopped.set(true);
            throw e;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            rLock.unlock();
        }
        return msg;
    }

    public Message read() throws IOException, UnsupportedOperationException{
        if(started.get())
            throw new UnsupportedOperationException("read() only supported before listener thread is started");

        return readObject();
    }

    public void close() throws IOException{
        stopped.set(true);
        socket.close();
        writeThread.interrupt();
        onCloseCallback = null;
        onReceivedCallback = null;
    }

}
