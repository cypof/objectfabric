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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;

import org.junit.Test;

public abstract class ExtendedTester {

    public interface ExtendedTest {

        int flags();

        int threadCount();

        void run(Runnable runnable);
    }

    public static final int CYCLES = 100;

    public static final int TEST_IN_PRIVATE_TRANSACTION = 1 << 0;

    public static final int REUSE_BY_CLEAR = 1 << 1;

    public static final int REUSE_BY_REMOVES = 1 << 2;

    public static final int PROXY_1 = 1 << 3;

    public static final int PROXY_2 = 1 << 4;

    public static final int PROXY_3 = 1 << 5;

    public static final int ALL = 1 << 6;

    final Workspace Workspace;

    ExtendedTester(Workspace workspace) {
        Workspace = workspace;
    }

    protected abstract ExtendedTest getTest(int threads, int flags);

    @Test
    public void run() {
        test(getTest(1, 0), CYCLES);
        test(getTest(1, TEST_IN_PRIVATE_TRANSACTION), CYCLES);
        test(getTest(1, REUSE_BY_CLEAR), CYCLES);
        test(getTest(1, REUSE_BY_REMOVES), CYCLES);
        test(getTest(1, PROXY_1), CYCLES);
        test(getTest(1, PROXY_2), CYCLES);
        test(getTest(1, PROXY_3), CYCLES);
    }

    @Test
    public void run2Threads() {
        test(getTest(2, TEST_IN_PRIVATE_TRANSACTION), CYCLES);
        test(getTest(2, REUSE_BY_CLEAR), CYCLES);
        test(getTest(2, REUSE_BY_REMOVES), CYCLES);
        test(getTest(2, PROXY_1), CYCLES);
        test(getTest(2, PROXY_2), CYCLES);
        test(getTest(2, PROXY_3), CYCLES);
    }

    @Test
    public void runMixed() {
        for (int i = 0; i < ALL; i++)
            test(getTest(4, i), 10);
    }

    private int todo;

    public static void run(final ExtendedTest test) {
        // if ((test.flags() & TEST_IN_PRIVATE_TRANSACTION) != 0) {
        // workspace.atomic(new Runnable() {
        //
        // @Override
        // public void run() {
        // test.run(runnable);
        // }
        // });
        // } else
        // test.run(runnable);
    }

    public static void threadAfter(ExtendedTest test) {
        // if ((test.flags() & TEST_IN_PRIVATE_TRANSACTION) != 0)
        // Transaction.getCurrent().commit();
    }

    protected static boolean transactionIsPrivate(ExtendedTest test) {
        if ((test.flags() & TEST_IN_PRIVATE_TRANSACTION) != 0)
            return true;

        if ((test.flags() & (PROXY_1 | PROXY_2 | PROXY_3)) != 0)
            return true;

        if (test.threadCount() > 1)
            return true;

        return false;
    }

    public Object getCachedOrProxy(int flags, final Object cached, Object wrapper) {
        boolean cleared = false;

        if ((flags & REUSE_BY_CLEAR) != 0) {
            clear(cached);
            cleared = true;
        } else if ((flags & REUSE_BY_REMOVES) != 0) {
            if (Debug.ENABLED) {
                // TODO: remove
                if (cached instanceof Map)
                    ((Map) cached).size();
            }

            Runnable runnable = new Runnable() {

                @Override
                public void run() {
                    if (Debug.ENABLED)
                        if (cached instanceof Map)
                            ((Map) cached).size();

                    Iterator it = cached instanceof Collection ? ((Collection) cached).iterator() : ((Map) cached).entrySet().iterator();

                    while (it.hasNext()) {
                        it.next();
                        it.remove();
                    }

                    if (Debug.ENABLED)
                        if (cached instanceof Map)
                            Debug.assertion(((Map) cached).size() == 0);
                }
            };

            if ((flags & TEST_IN_PRIVATE_TRANSACTION) == 0)
                Workspace.atomic(runnable);
            else
                runnable.run();

            if (Debug.ENABLED)
                if (cached instanceof Map)
                    Debug.assertion(((Map) cached).size() == 0);

            cleared = true;
        }

        if ((flags & (PROXY_1 | PROXY_2 | PROXY_3)) != 0) {
            if (!cleared)
                clear(cached);

            return wrapper;
        }

        if (cleared)
            return cached;

        return null;
    }

    private static void clear(Object cached) {
        if (cached instanceof Collection)
            ((Collection) cached).clear();
        else
            ((Map) cached).clear();
    }

    public void test(ExtendedTest test, int runs) {
        test(test, runs, true);
    }

    public void test(final ExtendedTest test, int runs, boolean beforeAndAfter) {
        final ArrayList<Method> methods = new ArrayList<Method>();
        Method before = null, after = null;
        Class c = test.getClass();

        while (c != null) {
            for (Method method : c.getMethods()) {
                for (Annotation annotation : method.getAnnotations()) {
                    if (annotation instanceof org.junit.Test)
                        methods.add(method);

                    if (beforeAndAfter) {
                        if (annotation instanceof org.junit.Before)
                            before = method;

                        if (annotation instanceof org.junit.After)
                            after = method;
                    }
                }
            }

            c = c.getSuperclass();
        }

        try {
            if (test.threadCount() == 1) {
                if (before != null)
                    before.invoke(test, new Object[0]);

                for (Method method : methods)
                    invoke(method, test);

                if (after != null)
                    after.invoke(test, new Object[0]);
            } else {
                if (before != null)
                    before.invoke(test, new Object[0]);

                final ConcurrentHashMap<Thread, int[]> statsByThread = new ConcurrentHashMap<Thread, int[]>();

                run(test.threadCount(), new Runnable() {

                    public void run() {
                        int index = Platform.get().randomInt(methods.size());
                        Method method = methods.get(index);

                        invoke(method, test);

                        int[] stats = statsByThread.get(Thread.currentThread());

                        if (stats == null)
                            statsByThread.put(Thread.currentThread(), stats = new int[methods.size()]);

                        stats[index]++;
                    }
                }, runs);

                for (Entry<Thread, int[]> entry : statsByThread.entrySet()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(entry.getKey().getName() + ": ");

                    for (int i = 0; i < entry.getValue().length; i++)
                        sb.append(methods.get(i).getName() + ": " + entry.getValue()[i] + ", ");

                    Log.write(sb.toString());
                }

                if (after != null)
                    after.invoke(test, new Object[0]);
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void invoke(final Method method, final ExtendedTest test) {
        test.run(new Runnable() {

            @Override
            public void run() {
                try {
                    method.invoke(test, new Object[0]);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }
        });
    }

    public final void run(int threads, final Runnable runnable, final int runs) {
        final CyclicBarrier barrier = new CyclicBarrier(threads);

        ArrayList<Thread> list = new ArrayList<Thread>();

        for (int t = 0; t < threads; t++) {
            String process = "";

            if (Debug.ENABLED)
                process = Helper.instance().ProcessName;

            process = process.length() > 0 ? process + " " : "";

            Thread thread = new Thread(process + "Thread " + t) {

                @Override
                public void run() {
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    for (int i = 0; i < runs; i++)
                        Workspace.atomic(runnable);
                }
            };

            list.add(thread);
            thread.start();
        }

        for (Thread thread : list) {
            try {
                thread.join();
            } catch (InterruptedException e) {
            }
        }
    }
}