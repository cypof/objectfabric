/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.objectfabric;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class ThreadPool {

    private static final Executor _executor;

    private static final ScheduledExecutorService _scheduler;

    static {
        Executor instance;

        if (Debug.ONE_THREAD_PER_POOL)
            instance = Executors.newFixedThreadPool(1, new Factory());
        else
            instance = Executors.newCachedThreadPool(new Factory());

        if (Debug.ENABLED) {
            if (Debug.DISABLE_THREAD_POOL) {
                instance = new Executor() {

                    @Override
                    public void execute(Runnable runnable) {
                        direct(runnable);
                    }
                };
            } else
                instance = new Wrapper(instance);
        }

        _executor = instance;

        _scheduler = Executors.newScheduledThreadPool(1, new ThreadFactory() {

            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("ObjectFabric Scheduler");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public static Executor getInstance() {
        return _executor;
    }

    public static void scheduleOnce(Runnable command, int ms) {
        _scheduler.schedule(command, ms, TimeUnit.MILLISECONDS);
    }

    public static void scheduleEvery(Runnable command, int ms) {
        _scheduler.scheduleAtFixedRate(command, ms, ms, TimeUnit.MILLISECONDS);
    }

    public static void flush() {
        if (Debug.ENABLED)
            ((Wrapper) _executor).flush();
        else {
            ThreadPoolExecutor executor = (ThreadPoolExecutor) _executor;

            while (executor.getQueue().size() > 0 || executor.getActiveCount() > 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    public static void shutdown() {
        flush();

        if (Debug.ENABLED)
            ((Wrapper) _executor).shutdown();
        else {
            if (_executor instanceof ExecutorService)
                ((ExecutorService) _executor).shutdown();
        }

        _scheduler.shutdownNow();
    }

    static int getLargestPoolSize() {
        if (Debug.ONE_THREAD_PER_POOL)
            return 1;

        Executor instance = _executor;

        if (instance instanceof Wrapper)
            instance = ((Wrapper) instance)._parent;

        return ((ThreadPoolExecutor) instance).getLargestPoolSize();
    }

    private static void direct(Runnable runnable) {
        Object key;

        if (Debug.ENABLED)
            ThreadAssert.suspend(key = new Object());

        runnable.run();

        if (Debug.ENABLED)
            ThreadAssert.resume(key);
    }

    private static final class Factory implements ThreadFactory {

        public Thread newThread(Runnable r) {
            String process = "";

            if (Debug.ENABLED && Helper.instance().ProcessName.length() != 0)
                process = Helper.instance().ProcessName + " ";

            Thread thread = new Thread(r);
            thread.setName(process + "OF ThreadPool " + thread.getName());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class Wrapper implements Executor {

        private final Executor _parent;

        private final AtomicInteger _active = new AtomicInteger();

        Wrapper(Executor executor) {
            if (!Debug.ENABLED)
                throw new RuntimeException();

            _parent = executor;
        }

        public void execute(final Runnable runnable) {
            _active.incrementAndGet();

            _parent.execute(new Runnable() {

                public void run() {
                    runnable.run();

                    if (Debug.THREADS) {
                        if (!ThreadAssert.isCurrentEmpty()) {
                            ThreadAssert.assertCurrentIsEmpty();
                            runnable.run();
                        }
                    }

                    _active.decrementAndGet();
                }
            });
        }

        void flush() {
            while (_active.get() > 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        void shutdown() {
            if (_parent instanceof ExecutorService)
                ((ExecutorService) _parent).shutdown();
        }
    }
}
