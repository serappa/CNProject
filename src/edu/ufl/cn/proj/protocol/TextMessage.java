package edu.ufl.cn.proj.protocol;

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class TextMessage extends ChatData implements Serializable {
    private final String message;
    public String getMessage() {
        return message;
    }

    public static TextMessage newInstance(String message){
        TextMessage instance = new TextMessage(idValue.getAndIncrement(), message);
        return instance;
    }
    private static AtomicInteger idValue = new AtomicInteger(new Random().nextInt());


    private TextMessage(int id, String message) {
        super(id);
        this.message = message;
    }
}
