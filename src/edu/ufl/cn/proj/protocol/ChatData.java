package edu.ufl.cn.proj.protocol;

import java.io.Serializable;

public abstract class ChatData implements Serializable{
    protected final int id;

    public ChatData(int id){
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{")
                .append("\"id\":").append(id)
//                .append("\"id\":").append(id).append(",")
//                .append("\"type\":\"").append(type).append("\",")
//                .append("\"data\":\"").append(data).append("\"")
                .append("}");
        return sb.toString();
    }
}
