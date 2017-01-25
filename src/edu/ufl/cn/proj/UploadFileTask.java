package edu.ufl.cn.proj;

import edu.ufl.cn.proj.protocol.FileData;
import edu.ufl.cn.proj.protocol.Message;

import java.io.*;
import java.util.Arrays;

public class UploadFileTask extends AsyncProgressTask<Void, Void, Double, Void> {

    protected final String uploadFileName;
    protected final ThreadSafeSocketReaderWriter socketWriter;
    protected long bytesUploaded;
    protected InputStream reader;
    protected long size;
    protected long lastPublish;
    protected long nextPublish = 1024*1024;
    protected final Message uploadDetails;
    protected FileData fd;

    public UploadFileTask(Message msgWithoutData, String uploadFileName, ThreadSafeSocketReaderWriter socketWriter){
        this.uploadFileName = uploadFileName;
        uploadDetails = msgWithoutData;
        bytesUploaded = 0L;
        lastPublish = 0L;
        this.socketWriter = socketWriter;
    }

    @Override
    protected void start(Void initialValue) {
        try(InputStream is  = new FileInputStream(uploadFileName)){
            File file = new File(uploadFileName);
            size = file.length();
            while(nextPublish * 10 < size/20)
                nextPublish *= 10;
            byte[] buffer = new byte[1024 * 8];
            reader = is;
            String fileName =  uploadFileName;
            int indexOfFName = fileName.lastIndexOf("/");
            if(indexOfFName != -1){
                fileName = fileName.substring(indexOfFName+1);
            }
            indexOfFName = fileName.lastIndexOf("\\");
            if(indexOfFName != -1){
                fileName = fileName.substring(indexOfFName+1);
            }
            int n;
            do{
                n=reader.read(buffer);
                byte[] buff;
                if(n == -1)
                    buff = new byte[0];
                else buff = Arrays.copyOf(buffer,n);

                if(fd == null)
                    fd = FileData.newInstance(fileName,buff,size);
                else fd = fd.newChuck(buff);

                if(n==-1)
                    fd.setEndOfFile();
                Message msg = new Message(uploadDetails.getSource(), uploadDetails.getTarget(), uploadDetails.getOpCode(), fd);
                socketWriter.write(msg);

                bytesUploaded += n;
                if(bytesUploaded >= lastPublish){
                    lastPublish += nextPublish;
                    publishProgress((bytesUploaded/(double)size) * 100);
                }

            }
            while(n !=-1 && !cancelled.get());
            completed.set(true);
        }catch(FileNotFoundException e){
            throw new RuntimeException(e);
        }catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    protected void discardFile(){
        if(this.fd!=null){
            FileData fd = this.fd.newChuck(new byte[1]);
            fd.setDiscard();
            Message msg = new Message(uploadDetails.getSource(), uploadDetails.getTarget(), uploadDetails.getOpCode(), fd);
            try {
                socketWriter.write(msg);
            }catch (IOException ex){
                ex.printStackTrace();
            }
        }
    }

    @Override
    protected void onException(Exception e) {
        discardFile();

    }

    @Override
    protected void processUpdate(Void update) {}

    @Override
    public void updateProgress(Void update){
        throw new UnsupportedOperationException("Async update operation not supported for Upload File Task");
    }

}

