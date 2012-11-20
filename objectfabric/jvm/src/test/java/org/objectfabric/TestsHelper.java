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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public class TestsHelper {

    public static final byte[] UID_1 = { 17, 17, 17, 17, -116, 68, 36, 18, -45, 82, 19, 94, 95, 97, 37, 1 };

    public static final byte[] UID_2 = { 34, 34, 34, 34, -85, -74, 51, 14, 57, 59, -121, 87, -56, -121, 35, 2 };

    public static final byte[] UID_3 = { 51, 51, 51, 51, -33, -23, -31, 20, 67, 87, 0, -46, -71, -16, -125, 3 };

    static {
        JVMPlatform.loadClass();

        Assert.assertTrue(new UID(UID_1).compare(new UID(UID_2)) < 0);
        Assert.assertTrue(new UID(UID_2).compare(new UID(UID_3)) < 0);
    }

    private static final int MAX_MEMORY = (int) 10e6;

    private static volatile long _memory;

    @Before
    public void before() {
        if (!skipMemory())
            _memory = getMemory();
    }

    public static void assertMemory(String message) {
        long memory = getMemory();
        Log.write("Memory (" + message + "): " + memory / 1024 + " KB");
        memoryWarning(memory);
    }

    private static void memoryWarning(long memory) {
        if (memory > MAX_MEMORY)
            Log.write("WARNING: " + memory / 1024 + " KB memory used, leak?");
    }

    protected boolean skipMemory() {
        return false;
    }

    @After
    public void after() {
        if (Debug.ENABLED)
            Helper.instance().assertClassLoaderIdle();

        if (Stats.ENABLED)
            Stats.Instance.writeAndReset();

        if (!skipMemory()) {
            long memory = getMemory();
            Log.write("Memory delta: " + _memory / 1024 + " KB -> " + memory / 1024 + " KB");
            memoryWarning(memory);
        }
    }

    private static long getMemory() {
        long used = 0;

        /*
         * Retries a few times because memory could get allocated by another thread
         * between GC and reads.
         */
        for (int i = 0; i < 10; i++) {
            System.gc();

            long total = Runtime.getRuntime().totalMemory();
            long free = Runtime.getRuntime().freeMemory();
            used = total - free;

            if (used < MAX_MEMORY)
                return used;
        }

        return used;
    }

    public static void setConflictRandom() {
        Helper.instance().ConflictRandom = true;
    }

    public static void disableExpectedExceptionThrowerCounter() {
        ExpectedExceptionThrower.disableCounter();
    }

    public static final void throwRuntimeException(String message) {
        ExpectedExceptionThrower.throwRuntimeException(message);
    }

    static void setUID1(Workspace workspace) {
        workspace.addURIHandler(new TestLocation() {

            @Override
            void start(WorkspaceLoad load) {
                long tick = Tick.get(Peer.get(new UID(UID_1)).index(), 1);
                load.onResponse(tick, Platform.get().newUID(), (byte) 0);
            }
        });
    }

    static void setUID2(Workspace workspace) {
        workspace.addURIHandler(new TestLocation() {

            @Override
            void start(WorkspaceLoad load) {
                long tick = Tick.get(Peer.get(new UID(UID_2)).index(), 1);
                load.onResponse(tick, Platform.get().newUID(), (byte) 0);
            }
        });
    }

    static void setUID3(Workspace workspace) {
        workspace.addURIHandler(new TestLocation() {

            @Override
            void start(WorkspaceLoad load) {
                long tick = Tick.get(Peer.get(new UID(UID_3)).index(), 1);
                load.onResponse(tick, Platform.get().newUID(), (byte) 0);
            }
        });
    }

    private static class TestLocation extends Origin implements URIHandler {

        TestLocation() {
            super(false);
        }

        @Override
        public URI handle(Address address, String path) {
            return null;
        }

        @Override
        View newView(URI _) {
            return new View(TestLocation.this) {

                @Override
                void getKnown(URI uri) {
                }

                @Override
                void onKnown(URI uri, long[] ticks) {
                }

                @Override
                void getBlock(URI uri, long tick) {
                }

                @Override
                void onBlock(URI uri, long tick, Buff[] buffs, long[] removals, boolean requested) {
                }
            };
        }
    }
}
