package edu.ufl.cn.proj.protocol;

import java.io.Serializable;
import java.util.Comparator;

public class FileChunk implements Serializable,Comparable<FileChunk>{
    private final byte[] data;
    private final int id;


    FileChunk(byte[] data, int id) {
        this.data = data;
        this.id = id;
    }

    public byte[] getData() {
        return data;
    }
    public int getId() {
        return id;
    }

    @Override
    public int compareTo(FileChunk o) {
        return id - o.id;
    }
}
