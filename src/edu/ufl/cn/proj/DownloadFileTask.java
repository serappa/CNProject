package edu.ufl.cn.proj;

import edu.ufl.cn.proj.protocol.FileData;

import java.io.*;



public class DownloadFileTask extends AsyncProgressTask<FileData, FileData, Double, Void> {

    protected final String source;
    protected final String downloadFileName;
    protected long bytesWritten;
    protected long size;
    protected long lastPublish;
    protected long nextPublish=1024*1024;
    protected FileData fileData;

    private FileOutputStream fileWriter;
    public DownloadFileTask(String source, String downloadFileName){
        this.source = source;
        this.downloadFileName = downloadFileName;
        bytesWritten = 0L;
        lastPublish = 0L;
    }

    @Override
    protected void start(FileData initialValue) {
        try{
            fileWriter  = new FileOutputStream(downloadFileName);
            size = initialValue.getSize();
            fileData = initialValue;
            while(nextPublish * 10 < size/20)
                nextPublish *= 10;
            if(!initialValue.isDiscard()){
                fileWriter.write(initialValue.getChunk().getData());
                bytesWritten += initialValue.getChunk().getData().length;
                if(initialValue.isEndOfFile()) {
                    finalizeFileWrite();
                }
            } else {
                deleteFile();
            }
        }catch(FileNotFoundException e){
            throw new RuntimeException(e);
        }catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void processUpdate(FileData update) {
        try{
            if(!update.isDiscard()){
                if(update.getChunk().getData().length>0) {
                    fileWriter.write(update.getChunk().getData());
                    bytesWritten += update.getChunk().getData().length;
                    if(bytesWritten >= lastPublish){
                        lastPublish += nextPublish;
                        publishProgress((bytesWritten/(double)size) * 100);
                    }
                }
                if(update.isEndOfFile()) {
                    finalizeFileWrite();
                    completed.set(true);
                }
            } else {
                onCancelled();
            }
        }catch (IOException e){
            throw new RuntimeException(e);
        }


    }
    protected void deleteFile() throws IOException{
        fileWriter.flush();
        fileWriter.close();
        File file = new File(downloadFileName);
        file.delete();

    }

    private void finalizeFileWrite() throws IOException{
        fileWriter.flush();
        fileWriter.close();
    }
}
