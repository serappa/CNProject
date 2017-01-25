package edu.ufl.cn.proj.protocol;


import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Error extends ChatData implements Serializable {
    protected final int errCode;
    protected final String errMsg;
    public static Error newInstance(int errCode, String errMsg){
        Error instance = new Error(idValue.getAndIncrement(), errCode, errMsg);
        return instance;
    }



    private static AtomicInteger idValue = new AtomicInteger(new Random().nextInt());

    private Error(int id, int errCode, String errMsg) {
        super(id);
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    public static int DUPLICATE_ID=1;
    public static int DESTINATION_UNREACHABLE=2;
    public static int MASQUERADING_AS_OTHER_CLIENT=3;
    public static int VALIDATION_FAILED=4;
    public static int PARTIAL_TARGETS_SENT=5;

    public int getErrCode() {
        return errCode;
    }

    public String getErrMsg() {
        return errMsg;
    }
}
