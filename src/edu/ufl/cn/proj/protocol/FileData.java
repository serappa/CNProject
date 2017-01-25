package edu.ufl.cn.proj.protocol;

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class FileData extends ChatData implements Serializable{
    private final String fileName;
    private final FileChunk chunk;
    private long size;
    private final AtomicBoolean endOfFile;
    private final AtomicBoolean discard;
    private int chunkId = -1;
    public FileChunk getChunk() {
        return chunk;
    }

    public String getFileName() {
        return fileName;
    }

    public static FileData newInstance(String fileName, byte[] fileData, long size){
        FileData instance = new FileData(idValue.getAndIncrement(),fileName, fileData, size, 0);

        return instance;
    }

    public FileData newChuck(byte[] fileData){
        return new FileData(this.id, fileName, fileData, size, chunkId);
    }

    public boolean isEndOfFile() {
        return endOfFile.get();
    }

    public void setEndOfFile() {
        endOfFile.set(true);
    }

    public long getSize() {
        return size;
    }

    public boolean isDiscard() {
        return discard.get();
    }

    public void setDiscard() {
        discard.set(true);
    }

    private static AtomicInteger idValue = new AtomicInteger(new Random().nextInt());


    private FileData(int id, String fileName,byte[] data, long size, int chunkId) {
        super(id);
        this.fileName = fileName;
        this.chunkId = chunkId+1;
        this.chunk = new FileChunk(data, this.chunkId);
        this.endOfFile = new AtomicBoolean(false);
        this.discard =  new AtomicBoolean(false);
        this.size = size;
    }
}
