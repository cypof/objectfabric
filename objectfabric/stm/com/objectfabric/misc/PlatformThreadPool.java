/**
 * Copyright (c) ObjectFabric Inc. All rights reserved.
 *
 * This file is part of ObjectFabric (objectfabric.com).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.objectfabric.misc;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.objectfabric.Transaction;

/**
 * Platform classes allow other implementations to be used for ports like GWT and .NET.
 * The .NET specific implementations make it possible to remove Java components like
 * Reflection and Security from the ObjectFabric dll.
 */
public final class PlatformThreadPool {

    private static final ExecutorService _instance;

    private static final ScheduledExecutorService _scheduler;

    static {
        ExecutorService instance = Executors.newCachedThreadPool(new ThreadFactory() {

            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                String process = Debug.ProcessName.length() > 0 ? Debug.ProcessName + " " : "";
                thread.setName(process + "ObjectFabric ThreadPool " + thread.getName());
                thread.setDaemon(true);
                return thread;
            }
        });

        if (Debug.ENABLED)
            instance = new Wrapper(instance);

        _instance = instance;

        _scheduler = Executors.newScheduledThreadPool(1, new ThreadFactory() {

            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("ObjectFabric Scheduler");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private PlatformThreadPool() {
    }

    public static final Executor getInstance() {
        return _instance;
    }

    public static Future schedule(Runnable command, int ms) {
        return _scheduler.schedule(command, ms, TimeUnit.MILLISECONDS);
    }

    static void flush() {
        if (!Debug.TESTING)
            throw new RuntimeException();

        if (Debug.ENABLED)
            ((Wrapper) _instance).flush();
        else {
            ThreadPoolExecutor executor = (ThreadPoolExecutor) _instance;

            while (executor.getQueue().size() > 0 || executor.getActiveCount() > 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    static void shutdown() {
        flush();
        _instance.shutdown();
    }

    private static final class Wrapper implements ExecutorService {

        private final ExecutorService _executor;

        private final AtomicInteger _active = new AtomicInteger();

        public Wrapper(ExecutorService executor) {
            if (!Debug.ENABLED)
                throw new RuntimeException();

            _executor = executor;
        }

        public void execute(final Runnable runnable) {
            _active.incrementAndGet();

            _executor.execute(new Runnable() {

                public void run() {
                    Debug.assertion(Transaction.getCurrent() == null);

                    runnable.run();

                    Debug.assertion(Transaction.getCurrent() == null);

                    if (Debug.THREADS)
                        ThreadAssert.assertCurrentIsEmpty();

                    _active.decrementAndGet();
                }
            });
        }

        public void flush() {
            while (_active.get() > 0)
                PlatformThread.sleep(1);
        }

        public void shutdown() {
            _executor.shutdown();
        }

        //

        public List<Runnable> shutdownNow() {
            // TODO Auto-generated method stub
            return null;
        }

        public boolean isShutdown() {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean isTerminated() {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            // TODO Auto-generated method stub
            return false;
        }

        public <T> Future<T> submit(Callable<T> task) {
            // TODO Auto-generated method stub
            return null;
        }

        public <T> Future<T> submit(Runnable task, T result) {
            // TODO Auto-generated method stub
            return null;
        }

        public Future<?> submit(Runnable task) {
            // TODO Auto-generated method stub
            return null;
        }

        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            // TODO Auto-generated method stub
            return null;
        }

        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
            // TODO Auto-generated method stub
            return null;
        }

        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            // TODO Auto-generated method stub
            return null;
        }

        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            // TODO Auto-generated method stub
            return null;
        }
    }
}
