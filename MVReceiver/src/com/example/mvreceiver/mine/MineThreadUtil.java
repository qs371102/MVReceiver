package com.example.mvreceiver.mine;

import android.os.Handler;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

final class MineThreadUtils {
	MineThreadUtils() {
    }

    static void executeUninterruptibly(MineThreadUtils.BlockingOperation operation) {
        boolean wasInterrupted = false;

        while(true) {
            try {
                operation.run();
                break;
            } catch (InterruptedException var3) {
                wasInterrupted = true;
            }
        }

        if(wasInterrupted) {
            Thread.currentThread().interrupt();
        }

    }

    static void joinUninterruptibly(final Thread thread) {
        executeUninterruptibly(new MineThreadUtils.BlockingOperation() {
            public void run() throws InterruptedException {
                thread.join();
            }
        });
    }

    static void awaitUninterruptibly(final CountDownLatch latch) {
        executeUninterruptibly(new MineThreadUtils.BlockingOperation() {
            public void run() throws InterruptedException {
                latch.await();
            }
        });
    }

    static <V> V invokeUninterruptibly(Handler handler, final Callable<V> callable) {
        class Result {
            public V value;

            Result() {
            }
        }

        final Result result = new Result();
        final CountDownLatch barrier = new CountDownLatch(1);
        handler.post(new Runnable() {
            public void run() {
                try {
                    result.value = callable.call();
                } catch (Exception var2) {
                    throw new RuntimeException("Callable threw exception: " + var2);
                }

                barrier.countDown();
            }
        });
        awaitUninterruptibly(barrier);
        return result.value;
    }

    static void invokeUninterruptibly(Handler handler, final Runnable runner) {
        final CountDownLatch barrier = new CountDownLatch(1);
        handler.post(new Runnable() {
            public void run() {
                runner.run();
                barrier.countDown();
            }
        });
        awaitUninterruptibly(barrier);
    }

    interface BlockingOperation {
        void run() throws InterruptedException;
    }

    static class ThreadChecker {
        private Thread thread = Thread.currentThread();

        ThreadChecker() {
        }

        void checkIsOnValidThread() {
            if(this.thread == null) {
                this.thread = Thread.currentThread();
            }

            if(Thread.currentThread() != this.thread) {
                throw new IllegalStateException("Wrong thread");
            }
        }

        void detachThread() {
            this.thread = null;
        }
    }
}
