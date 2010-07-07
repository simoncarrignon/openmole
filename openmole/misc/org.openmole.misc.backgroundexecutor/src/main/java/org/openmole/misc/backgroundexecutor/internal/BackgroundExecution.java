package org.openmole.misc.backgroundexecutor.internal;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.openmole.misc.executorservice.ExecutorType;

import org.openmole.misc.backgroundexecutor.IBackgroundExecution;

public class BackgroundExecution<T> implements IBackgroundExecution<T> {

    Callable<T> callable;
    
    Throwable exception = null;
    boolean finished = false;
    boolean started = false;
 
    T result;
    
    transient Future future;

    public BackgroundExecution(Callable<T> callable) {
        super();
        this.callable = callable;
    }

    @Override
    public synchronized void start(ExecutorType type) {
        if (isStarted()) {
            return;
        }
        future = Activator.getExecutorService().getExecutorService(type).submit(new Runnable() {

            @Override
            public void run() {
                Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, Throwable thrwbl) {
                       exception = thrwbl;
                       finished = true;
                    }
                });

                try {
                    result = callable.call();
                } catch (Throwable e) {
                    exception = e;
                } finally {
                    finished = true;
                }
            }
        });

        started = true;
    }

    @Override
    public synchronized boolean isStarted() {
        return started;
    }

    @Override
    public synchronized boolean hasFailed() {
        return exception != null;
    }

    @Override
    public synchronized Throwable getFailureCause() {
        return exception;
    }

    @Override
    public synchronized boolean isSucessFull() {
        return finished && !hasFailed();
    }

    @Override
    public synchronized boolean isFinished() {
        return finished;
    }

    @Override
    public synchronized void interrupt() {
        if(future != null) {
            future.cancel(true);
        }
    }

    @Override
    public synchronized boolean isSucessFullStartIfNecessaryExceptionIfFailed(ExecutorType type) throws ExecutionException {
        if (hasFailed()) {
            Throwable t = getFailureCause();
            throw new ExecutionException(t);
        }

        if (!isStarted()) {
            start(type);
        }

        return isSucessFull();
    }

    @Override
    public T getResult() {
        return result;
    }
}
