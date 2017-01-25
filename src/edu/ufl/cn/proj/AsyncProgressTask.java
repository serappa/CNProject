package edu.ufl.cn.proj;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Borrowed the concept of AsyncTask from Android.
 * Abstract class that runs a task in a background thread backed by a executor service and allows asynchronous updated to a task
 */
public abstract class AsyncProgressTask<I,U,P,R> {

    public AsyncProgressTask() {
        completed = new AtomicBoolean(false);
        cancelled = new AtomicBoolean(false);
        started = new AtomicBoolean(false);
        updateQueue = new ArrayBlockingQueue<>(1000);
    }

    protected volatile R result;
    protected Future taskFuture;
    protected AtomicBoolean completed;
    protected AtomicBoolean cancelled;
    protected AtomicBoolean started;
    protected ExecutorService executor;
    protected BlockingQueue<U> updateQueue;


    /**
     * Performs initialization of the task, any non-abstract class should implement this method
     * @param initialValue initialization value
     */
    protected abstract void start(I initialValue);

    /**
     * Performs updates to the task, any non-abstract class should implement this method.
     * If the task wants to publish the updates to the task it should call publishProgress(P progress) method to publish the update progresss
     * @param update parameter describing the update
     */
    protected abstract void processUpdate(U update);

    /**
     * Asynchronously notify before the start of the task's execution. Sub-classes should override this method if they are interested in the event.
     */
    protected void onPreExecute(){}

    /**
     * Asynchronously notify the updates on the task. Sub-classes should override this method if they are interested in the event.
     * @param progress indicating the progress
     */
    protected void onProgressUpdate(P progress){}

    /**
     * Asynchronously notify the completion of the task. Sub-classes should override this method if they are interested in the event.
     * @param result indication the result of the task
     */
    protected void onComplete(R result){}

    /**
     * Asynchronously notify when any exception in the task occurs. Sub-classes should override this method if they are interested in the event.
     * @param e Exception that occurred
     */
    protected void onException(Exception e){}

    protected void onCancelled(){}

    /**
     * Waiting call to get the result of the task. The executing thread is blocked until the task is complete
     * @return Result of the task
     * @throws InterruptedException
     */
    public final synchronized R get() throws InterruptedException{
        while(!completed.get())
            wait();
        return result;
    }

    /**
     * Indicates whether task is complete
     * @return whether the task is complete
     */
    public final boolean isComplete(){
        return completed.get();
    }

    /**
     * Publishes the progress of the task
     * @param progress Progress value
     */
    protected final void publishProgress(P progress){
        onProgressUpdate(progress);
    }


    /**
     * Update the progress of the task
     * @param update progress of the task
     */
    public void updateProgress(U update){
        updateQueue.add(update);
    }

    public void cancel(){
        cancelled.set(true);
        taskFuture.cancel(true);
        onCancelled();
    }

    public synchronized void execute(ExecutorService es, I initValue){
        this.executor = es;
        if(started.getAndSet(true))
            throw new IllegalArgumentException("Task already started");
        taskFuture = executor.submit(()->{
            try {
                if (!cancelled.get()) {
                    onPreExecute();start(initValue);
                }
                while (!cancelled.get() && !completed.get()) {
                    U newUpdate = updateQueue.take();
                    processUpdate(newUpdate);
                }
                if(completed.get())
                    onComplete(result);
                try {
                    notifyAll();
                }catch(IllegalMonitorStateException exx) {//NO op}
                }
            }catch (InterruptedException ex){
                System.out.println("Task interrupted");
                cancel();
            }catch(Exception e){
                onException(e);
                System.out.println("Exception occured in Task: "+e.getMessage()+ " cancelling the task");
                cancel();
            }
        });

    }




}
