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

package com.objectfabric;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;

import org.junit.Test;

import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.generated.PersistenceClass;
import com.objectfabric.generated.PersistenceObjectModel;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformFile;
import com.objectfabric.misc.PlatformThread;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.misc.TransparentExecutor;

public class JDBMStoreTest extends TestsHelper {

    private static final String PATH = "temp/db.db";

    private static final int CYCLES = 10;

    private static final int WRITES = 10000;

    public static void main(String[] args) throws Exception {
        JDBMStoreTest test = new JDBMStoreTest();

        for (int i = 0; i < 100; i++) {
            test.before();
            test.test();
            test.after();
        }
    }

    @Test
    public void test() {
        if (Debug.ENABLED || Stats.ENABLED)
            Log.write("Warning: Debug.ENABLED || Stats.ENABLED");

        PersistenceObjectModel.register();
        PlatformFile.mkdir(JdbmTest.TEMP);
        PlatformFile.deleteFileIfExists(PATH);
        PlatformFile.deleteFileIfExists(PATH + FileStore.LOG_EXTENSION);

        SeparateClassLoader test = new SeparateClassLoader(TestWrite.class.getName());
        test.run();

        SeparateClassLoader read = new SeparateClassLoader(TestRead.class.getName());
        read.run();
    }

    public static final class TestWrite {

        private final FileStore _store;

        private final LazyMap<String, PersistenceClass> _map;

        private TestWrite() {
            _store = new FileStore(PATH);
            Transaction trunk = Site.getLocal().createTrunk(_store);
            Transaction.setDefaultTrunk(trunk);

            _map = new LazyMap<String, PersistenceClass>();
            Assert.assertNull(_store.getRoot());
            _store.setRoot(_map);
        }

        public static void main(String[] args) {
            PersistenceObjectModel.register();
            TestWrite test = new TestWrite();
            int perf = 0;

            for (int i = 0; i < CYCLES; i++) {
                if (PlatformAdapter.getRandomBoolean())
                    perf += test.writeBatch(i);
                else
                    perf += test.writeAsync(i);
            }

            Log.write("Average: " + (perf / CYCLES) + " objects/s");
            test._store.close();

            if (Stats.ENABLED)
                Stats.getInstance().writeAndReset();

            PlatformAdapter.shutdown();
        }

        private int writeBatch(int cycle) {
            long start = System.nanoTime();

            for (int i = 0; i < WRITES; i++)
                createObject(cycle * WRITES + i);

            _store.flush();

            long end = System.nanoTime();
            double seconds = (end - start) / 1e9;
            int perf = (int) (WRITES / seconds);
            Log.write("Write batch: " + seconds + " s (" + perf + " objects/s)");
            return perf;
        }

        private int writeAsync(int cycle) {
            long start = System.nanoTime();
            final Counter latency = new Counter();
            final Counter maxLatency = new Counter();

            for (int i = 0; i < WRITES; i++) {
                final long transactionStart = System.nanoTime();
                Transaction transaction = Transaction.start();
                createObject(cycle * WRITES + i);

                transaction.commitAsync(new AsyncCallback<CommitStatus>() {

                    public void onSuccess(CommitStatus result) {
                        long end = System.nanoTime();
                        double ms = (end - transactionStart) / 1e6;
                        latency.Value += ms;

                        if (ms > maxLatency.Value)
                            maxLatency.Value = ms;
                    }

                    public void onFailure(Throwable t) {
                        throw new IllegalStateException();
                    }
                }, new AsyncOptions() {

                    @Override
                    public Executor getExecutor() {
                        return TransparentExecutor.getInstance();
                    }
                });

                // Keeps latency low

                if ((i % 1000) == 0)
                    _store.flush();
            }

            _store.flush();

            long end = System.nanoTime();
            double seconds = (end - start) / 1e9;
            int perf = (int) (WRITES / seconds);
            Log.write("Write async: " + seconds + " s (" + perf + " objects/s), Latency:" + (latency.Value / WRITES) + " ms (max: " + maxLatency.Value + " ms)");
            return perf;
        }

        private void createObject(int i) {
            PersistenceClass object = new PersistenceClass();
            object.setInt(i);
            object.setText("456");
            _map.put("object" + i, object);
        }
    }

    public static final class TestRead {

        @SuppressWarnings("null")
        public static void main(String[] args) throws Exception {
            PersistenceObjectModel.register();

            FileStore store = new FileStore(PATH);
            Transaction trunk = Site.getLocal().createTrunk(store);
            Transaction.setDefaultTrunk(trunk);
            @SuppressWarnings("unchecked")
            final LazyMap<String, PersistenceClass> map = (LazyMap) store.getRoot();

            long start = System.nanoTime();
            final AtomicInteger pending = new AtomicInteger();
            final Counter maxLatency = new Counter();
            final Counter latency = new Counter();
            Future<PersistenceClass> future = null;

            for (int i = 0; i < CYCLES * WRITES; i++) {
                final int i_ = i;
                final long getStart = System.nanoTime();

                future = map.getAsync("object" + i, new AsyncCallback<PersistenceClass>() {

                    public void onSuccess(PersistenceClass object) {
                        Assert.assertTrue(object.getInt() == i_);

                        long end = System.nanoTime();
                        double ms = (end - getStart) / 1e6;
                        latency.Value += ms;

                        if (ms > maxLatency.Value)
                            maxLatency.Value = ms;

                        pending.decrementAndGet();
                    }

                    public void onFailure(Throwable t) {
                    }
                }, new AsyncOptions() {

                    @Override
                    public Executor getExecutor() {
                        return TransparentExecutor.getInstance();
                    }
                });

                pending.incrementAndGet();

                while (pending.get() > 3)
                    PlatformThread.sleep(1);
            }

            future.get();
            long end = System.nanoTime();
            double seconds = (end - start) / 1e9;
            Log.write("Read: " + seconds + " s (" + (CYCLES * WRITES / seconds) + " objects/s), Latency:" + (latency.Value / (CYCLES * WRITES)) + " ms (max: " + maxLatency.Value + " ms)");
            store.close();

            if (Stats.ENABLED)
                Stats.getInstance().writeAndReset();

            PlatformAdapter.shutdown();
        }
    }

    private static final class Counter {

        public double Value;
    }
}
