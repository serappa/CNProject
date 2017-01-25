package edu.ufl.cn.proj.protocol;

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public class Login extends ChatData implements Serializable{
    private final String login;
    private Login(int id, String login) {
        super(id);
        this.login = login;
    }

    public static Login newInstance(String login){
        Login instance = new Login(idValue.getAndIncrement(), login);
        return instance;
    }



    private static AtomicInteger idValue = new AtomicInteger(new Random().nextInt());

    public String getLogin() {
        return login;
    }
}
