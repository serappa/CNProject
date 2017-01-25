package edu.ufl.cn.proj.protocol;

import java.io.Serializable;

public class Message implements Serializable{
    private final String source;
    private final String target;
    private final OperationCode opCode;
    private final ChatData data;

    public Message(String source, String target, OperationCode opCode, ChatData data){
        this.source = source;
        this.target = target;
        this.opCode = opCode;
        this.data = data;
    }

    private Message(){
        this.source = null;
        this.target = null;
        this.opCode = null;
        this.data = null;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public OperationCode getOpCode() {
        return opCode;
    }

    public ChatData getData() {
        return data;
    }

    public  TextMessage dataAsTextMessage(){
        if(data instanceof TextMessage)
            return (TextMessage) data;
        else return null;
    }

    public FileData dataAsFileData(){
        if(data instanceof FileData)
            return (FileData)data;
        else return null;
    }

    public Error dataAsError(){
        if(data instanceof Error)
            return (Error) data;
        else return null;
    }

}
