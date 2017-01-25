package edu.ufl.cn.proj.protocol;

import java.io.Serializable;

public enum OperationCode implements Serializable{
    INIT,ACK,ERR,UNICAST_MESSAGE,UNICAST_FILE,BROADCAST_FILE,BROADCAST_MESSAGE,BLOCKCAST_FILE,BLOCKCAST_MESSAGE
}
