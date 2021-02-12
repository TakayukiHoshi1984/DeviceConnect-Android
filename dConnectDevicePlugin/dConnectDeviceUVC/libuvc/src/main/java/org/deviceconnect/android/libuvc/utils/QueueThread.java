package org.deviceconnect.android.libuvc.utils;

import java.util.LinkedList;
import java.util.Queue;

public abstract class QueueThread<T> extends Thread {
    private final Queue<T> mQueue = new LinkedList<>();

    public synchronized int size() {
        return mQueue.size();
    }

    public synchronized void add(T data) {
        mQueue.offer(data);
        notifyAll();
    }

    public synchronized T get() throws InterruptedException {
        while (mQueue.peek() == null) {
            wait();
        }
        return mQueue.remove();
    }

    public synchronized void close() {
        interrupt();

        try {
            join(500);
        } catch (InterruptedException e) {
            // ignore.
        }
    }
}
