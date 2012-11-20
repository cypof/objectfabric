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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.objectfabric.TObject.Version;
import org.objectfabric.Workspace.Granularity;

@Ignore
public class SplitRemote {

    private int todo;

    /**
     * If small number, there might be no conflict between threads so use <= instead of <
     * when asserting 'successes'.
     */
    public static final int DEFAULT_WRITE_COUNT = (int) 1e3;

    private volatile TMap<String, String> _map;

    private final HashMap<String, Integer> _last = new HashMap<String, Integer>();

    private final AtomicInteger _changeCallbackLast = new AtomicInteger();

    static {
        JVMPlatform.loadClass();
    }

    protected AtomicInteger getChangeCallbackLast() {
        return _changeCallbackLast;
    }

    @Test
    public void run1() {
        run(1, DEFAULT_WRITE_COUNT, Granularity.COALESCE);
    }

    @Test
    public void run2() {
        run(2, DEFAULT_WRITE_COUNT, Granularity.COALESCE);
    }

    @Test
    public void run3() {
        run(2, DEFAULT_WRITE_COUNT, Granularity.ALL);
    }

    void run(final int threadCount, final int writeCount, final Granularity granularity) {
        if (Stats.ENABLED)
            Stats.Instance.reset();

        Workspace workspace = Platform.get().newTestWorkspace(granularity);
        _map = new TMap<String, String>(workspace.resolve(""));

        TestNotifier notifier = new TestNotifier(workspace);
        workspace.forceChangeNotifier(notifier);

        _map.addListener(new KeyListener<String>() {

            @Override
            public void onPut(String key) {
                String[] parts = key.split(":");
                String client = parts[0];
                boolean remote = "remote".equals(parts[1]);
                int counter = Integer.parseInt(parts[2]);

                Assert.assertFalse(remote);
                int last = _last.get(client);
                Assert.assertTrue(counter == last || counter == last + 1);
                _last.put(client, counter);
            }

            @Override
            public void onRemove(String key) {
            }

            @Override
            public void onClear() {
            }
        });

        Log.write("");
        Log.write("Starting " + threadCount + " threads, " + writeCount + " writes, listener: " + granularity);

        ArrayList<SplitRemoteClient> threads = new ArrayList<SplitRemoteClient>();

        CyclicBarrier barrier = new CyclicBarrier(threadCount + 1);

        for (int i = 0; i < threadCount; i++) {
            _last.put("" + i, 0);
            SplitRemoteClient client = new SplitRemoteClient(_map, i, writeCount, barrier);
            threads.add(client);
            client.start();
        }

        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        long start = System.nanoTime();

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (java.lang.InterruptedException e) {
            }
        }

        long time = System.nanoTime() - start;
        workspace.flushNotifications();

        for (int i = 0; i < threadCount; i++)
            Assert.assertEquals((int) _last.get("" + i), threads.get(i)._counter);

        double writePerSec = (threads.size() * writeCount) * 1e9 / time;
        System.out.println((int) writePerSec + " writes/s");

        _last.clear();
        workspace.close();
    }

    static final class TestNotifier extends Notifier {

        HashSet<String> _keys = new HashSet<String>();

        TestNotifier(Workspace workspace) {
            super(workspace);
        }

        @Override
        Action onVisitingMap(int mapIndex) {
            Action action = super.onVisitingMap(mapIndex);

            if (snapshot().getVersionMaps()[mapIndex].isRemote())
                action = Action.SKIP;

            return action;
        }

        @Override
        void onVisitingVersion(Version version) {
            super.onVisitingVersion(version);

            TKeyedVersion keys = (TKeyedVersion) version;

            for (int i = 0; i < keys.getEntries().length; i++) {
                if (keys.getEntries()[i] != null)
                    _keys.add((String) keys.getEntries()[i].getKey());
            }
        }
    }
}
