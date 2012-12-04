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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

public class TMapSimple extends TestsHelper {

    private static final boolean LOG = false;

    private static final int CYCLES = Debug.ENABLED ? 10 : 100;

    @Test
    public void run() throws Exception {
        final Workspace workspace = Platform.newTestWorkspace();

        Logger logger = null;

        if (LOG)
            logger = new Logger(workspace, workspace.callbackExecutor());

        final TMap<Integer, String> map = new TMap<Integer, String>(workspace.open(""));
        final CyclicBarrier barrier = new CyclicBarrier(2);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger attempts = new AtomicInteger();

        Thread thread = new Thread() {

            @Override
            public void run() {
                workspace.atomic(new Runnable() {

                    @Override
                    public void run() {
                        map.put(0, "A");
                        map.put(1, "B");

                        Assert.assertEquals("A", map.get(0));
                        Assert.assertEquals("B", map.get(1));
                        Assert.assertEquals(2, map.size());

                        try {
                            barrier.await();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }

                        attempts.incrementAndGet();
                    }
                });
            }
        };

        thread.start();
        barrier.await();

        Assert.assertEquals(null, map.get(0));
        Assert.assertEquals(null, map.get(1));
        Assert.assertEquals(0, map.size());

        map.put(0, "X");
        map.put(1, "Y");

        Assert.assertEquals("X", map.get(0));
        Assert.assertEquals("Y", map.get(1));
        Assert.assertEquals(2, map.size());

        latch.countDown();
        barrier.await();
        thread.join();

        Assert.assertEquals("A", map.get(0));
        Assert.assertEquals("B", map.get(1));
        Assert.assertEquals(2, map.size());
        Assert.assertEquals(2, attempts.get());

        map.put(10, "test");

        Assert.assertEquals(3, map.size());

        try {
            workspace.atomic(new Runnable() {

                @Override
                public void run() {
                    map.remove(0);
                    map.remove(1);
                    Assert.assertEquals(1, map.size());
                    map.remove(10);
                    Assert.assertEquals(0, map.size());
                    ExpectedExceptionThrower.expectException();
                    ExpectedExceptionThrower.throwRuntimeException("");
                }
            });
        } catch (RuntimeException ex) {
        }

        //

        Assert.assertEquals(3, map.size());

        map.remove(0);
        map.remove(1);

        Assert.assertEquals("test", map.get(10));
        Assert.assertEquals(1, map.size());

        final CyclicBarrier barrier1 = new CyclicBarrier(2);
        final CyclicBarrier barrier2 = new CyclicBarrier(2);
        attempts.set(0);

        thread = new Thread() {

            @Override
            public void run() {
                workspace.atomic(new Runnable() {

                    @Override
                    public void run() {
                        map.put(0, "A");

                        try {
                            barrier1.await();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                        Assert.assertEquals("A", map.get(0));
                        attempts.incrementAndGet();

                        try {
                            barrier2.await();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
        };

        thread.start();
        barrier1.await();
        map.put(0, "X");
        barrier2.await();
        barrier1.await();
        barrier2.await();
        thread.join();
        Assert.assertEquals("A", map.get(0));
        Assert.assertEquals(2, attempts.get());
        Assert.assertEquals(2, map.size());

        if (LOG)
            logger.close();

        workspace.close();
    }

    @Test
    public void sizePrivatePublic() {
        final Workspace workspace = Platform.newTestWorkspace();
        final TMap<String, Integer> map = new TMap<String, Integer>(workspace.open(""));

        map.put("A", 0);
        map.put("B", 1);
        map.put("C", 2);

        workspace.atomic(new Runnable() {

            @Override
            public void run() {
                map.remove("A");
                map.remove("B");
                map.remove("C");
                Assert.assertEquals(0, map.size());
            }
        });

        Assert.assertEquals(0, map.size());

        workspace.atomic(new Runnable() {

            @Override
            public void run() {
                map.put("A", 0);
                map.remove("A");
                Assert.assertEquals(0, map.size());
            }
        });

        Assert.assertEquals(0, map.size());
        workspace.close();
    }

    @Test
    public void loop1() {
        final Workspace workspace = Platform.newTestWorkspace();
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>(workspace.open(""));

        for (int i = 0; i < CYCLES; i++) {
            workspace.atomic(new Runnable() {

                @Override
                public void run() {
                    addRemove1(map);
                }
            });
        }

        workspace.close();
    }

    @Test
    public void loop2() {
        final Workspace workspace = Platform.newTestWorkspace();
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>(workspace.open(""));

        for (int i = 0; i < CYCLES; i++) {
            workspace.atomic(new Runnable() {

                @Override
                public void run() {
                    addRemove2(map);
                }
            });
        }

        workspace.close();
    }

    @Test
    public void loop3() {
        final Workspace workspace = Platform.newTestWorkspace();
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>(workspace.open(""));

        for (int i = 0; i < CYCLES; i++) {
            map.clear();

            workspace.atomic(new Runnable() {

                @Override
                public void run() {
                    addRemove3(map);
                }
            });
        }

        workspace.close();
    }

    @Test
    public void loop3_bis() {
        final Workspace workspace = Platform.newTestWorkspace();
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>(workspace.open(""));

        for (int i = 0; i < CYCLES; i++) {
            workspace.atomic(new Runnable() {

                @Override
                public void run() {
                    for (Iterator it = map.entrySet().iterator(); it.hasNext();) {
                        it.next();
                        it.remove();
                    }
                }
            });

            workspace.atomic(new Runnable() {

                @Override
                public void run() {
                    addRemove3(map);
                }
            });
        }

        workspace.close();
    }

    @Test
    public void threads1() {
        final Workspace workspace = Platform.newTestWorkspace();
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>(workspace.open(""));
        int threads = Runtime.getRuntime().availableProcessors();

        new TMapExtended(workspace).run(threads, new Runnable() {

            public void run() {
                TMapSimple.addRemove1(map);
            }
        }, CYCLES / threads);

        workspace.close();
    }

    @Test
    public void threads2() {
        final Workspace workspace = Platform.newTestWorkspace();
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>(workspace.open(""));
        int threads = Runtime.getRuntime().availableProcessors();

        new TMapExtended(workspace).run(threads, new Runnable() {

            public void run() {
                TMapSimple.addRemove2(map);
            }
        }, CYCLES / threads);

        workspace.close();
    }

    @Test
    public void threads3() {
        final Workspace workspace = Platform.newTestWorkspace();
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>(workspace.open(""));
        int threads = Runtime.getRuntime().availableProcessors();

        new TMapExtended(workspace).run(threads, new Runnable() {

            public void run() {
                map.clear();
                TMapSimple.addRemove3(map);
            }
        }, CYCLES / threads);

        workspace.close();
    }

    @Test
    public void threads3_bis() {
        final Workspace workspace = Platform.newTestWorkspace();
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>(workspace.open(""));

        int threads = Runtime.getRuntime().availableProcessors();

        new TMapExtended(workspace).run(threads, new Runnable() {

            public void run() {
                for (Iterator it = map.entrySet().iterator(); it.hasNext();) {
                    it.next();
                    it.remove();
                }

                TMapSimple.addRemove3(map);
            }
        }, CYCLES / threads);

        workspace.close();
    }

    private static void addRemove1(TMap<Integer, Integer> map) {
        final int MAP_SIZE = 80;

        for (int i = 0; i < MAP_SIZE; i++)
            map.put(i, i);

        Assert.assertEquals(MAP_SIZE, map.size());

        for (int i = 0; i < MAP_SIZE; i++)
            Assert.assertTrue(i == map.get(i));

        for (int i = MAP_SIZE - 1; i >= 0; i--) {
            map.remove(i);
            Assert.assertEquals(i, map.size());
        }
    }

    private static void addRemove2(final TMap<Integer, Integer> map) {
        final int MAP_SIZE = 40;

        for (int i = 0; i < MAP_SIZE; i++) {
            final int i_ = i;

            map.atomic(new Runnable() {

                @Override
                public void run() {
                    Assert.assertEquals(i_, map.size());
                    map.put(i_, i_);
                    Assert.assertEquals(i_ + 1, map.size());
                }
            });

            Assert.assertEquals(i + 1, map.size());
        }

        for (int i = MAP_SIZE - 1; i >= 0; i--) {
            final int i_ = i;

            map.atomic(new Runnable() {

                @Override
                public void run() {
                    map.remove(i_);
                    Assert.assertEquals(i_, map.size());
                }
            });

            Assert.assertEquals(i, map.size());
        }
    }

    private static void addRemove3(final TMap<Integer, Integer> map) {
        final HashMap<Integer, Integer> ref = new HashMap<Integer, Integer>();
        final AtomicInteger id = new AtomicInteger();

        check(map, ref);

        for (int i = 0; i < 10; i++) {
            record("Transaction.start();");
            map.workspace().atomic(new Runnable() {

                @Override
                public void run() {

                    for (int t = 0; t < 100; t++) {
                        int size = map.size();
                        boolean insert = size == 0 || Platform.get().randomInt(10) < 8;
                        int index;

                        if (insert) {
                            index = Platform.get().randomInt(size + 1);
                            record("map.add(" + index + ", " + id + ");");
                            record("ref.add(" + index + ", " + id + ");");
                            map.put(index, id.get());
                            ref.put(index, id.getAndIncrement());
                        } else {
                            index = Platform.get().randomInt(size);
                            record("map.remove(" + index + ");");
                            record("ref.remove(" + index + ");");
                            map.remove(index);
                            ref.remove(index);
                        }

                        check(map, ref);
                    }

                    record("Transaction.getCurrent().commit();");
                }
            });

            check(map, ref);
        }
    }

    /**
     * @param message
     */
    private static void record(String message) {
        // System.out.println(message);
    }

    private static <K, V> void check(TMap<K, V> map, HashMap<K, V> ref) {
        Assert.assertEquals(ref.size(), map.size());

        for (Map.Entry<K, V> entry : map.entrySet())
            Assert.assertTrue(ref.get(entry.getKey()).equals(entry.getValue()));

        for (Map.Entry<K, V> entry : ref.entrySet())
            Assert.assertTrue(map.get(entry.getKey()).equals(entry.getValue()));
    }
}
