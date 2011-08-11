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

import java.util.Random;
import java.util.concurrent.Future;

import junit.framework.Assert;

import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.VersionMap.Source;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;

public class ConcurrentClient {

    public static final int WRITE_COUNT = (int) 1e3;

    // Client flags

    public static final int USE_TWO_INTEGERS = 1 << 0;

    public static final int USE_ONE_INTEGER_PER_CLIENT = 1 << 1;

    public static final int USE_ABORTS = 1 << 2;

    public static final int NO_WRITE = 1 << 3;

    public static final int CROSS = 1 << 4;

    public static final int MERGE_BY_SOURCE = 1 << 5;

    public static final int INTERCEPT = 1 << 6;

    public static final int TRANSFER = 1 << 7;

    public static final int USE_ALL = (1 << 8) - 1;

    static {
        int value = USE_TWO_INTEGERS;
        value |= USE_ONE_INTEGER_PER_CLIENT;
        value |= USE_ABORTS;
        value |= NO_WRITE;
        value |= CROSS;
        value |= MERGE_BY_SOURCE;
        value |= INTERCEPT;
        value |= TRANSFER;
        Debug.assertAlways(USE_ALL == value);
    }

    public static String writeClientFlags(int flags) {
        StringBuilder sb = new StringBuilder();

        if ((flags & USE_TWO_INTEGERS) != 0)
            sb.append("USE_TWO_INTEGERS, ");

        if ((flags & USE_ONE_INTEGER_PER_CLIENT) != 0)
            sb.append("USE_ONE_INTEGER_PER_CLIENT, ");

        if ((flags & USE_ABORTS) != 0)
            sb.append("USE_ABORTS, ");

        if ((flags & NO_WRITE) != 0)
            sb.append("NO_WRITE, ");

        if ((flags & CROSS) != 0)
            sb.append("CROSS, ");

        if ((flags & MERGE_BY_SOURCE) != 0)
            sb.append("MERGE_BY_SOURCE, ");

        if ((flags & INTERCEPT) != 0)
            sb.append("INTERCEPT, ");

        if ((flags & TRANSFER) != 0)
            sb.append("TRANSFER, ");

        return sb.toString();
    }

    protected void onStart() {
    }

    @SuppressWarnings("unused")
    public static Future<CommitStatus> loop(SimpleClass object, int number, int count, int flags) {
        Future<CommitStatus> future = null;
        Random rand = new Random(0);
        Transaction.setDefaultTrunk(object.getTrunk());

        boolean two = (flags & USE_TWO_INTEGERS) != 0;
        boolean onePerClient = (flags & USE_ONE_INTEGER_PER_CLIENT) != 0;
        boolean aborts = (flags & USE_ABORTS) != 0;
        boolean cross = (flags & CROSS) != 0;
        boolean transfer = (flags & TRANSFER) != 0;

        Source source = null;
        byte interceptionId = 0;

        for (int i = 0; i < count; i++) {
            Transaction transaction = Transaction.start();

            if ((flags & ConcurrentClient.NO_WRITE) == 0) {
                if (transfer) {
                    if (PlatformAdapter.getRandomDouble() < 0.99)
                        Transfer.between0And1(object);
                    else
                        Transfer.to2(object);
                } else if (onePerClient) {
                    switch (number) {
                        case 0:
                            object.setInt0(object.getInt0() + 1);
                            break;
                        case 1:
                            object.setInt1(object.getInt1() + 1);
                            break;
                        case 2:
                            object.setInt2(object.getInt2() + 1);
                            break;
                        case 3:
                            object.setInt3(object.getInt3() + 1);
                            break;
                        default:
                            Debug.fail();
                    }
                } else if (cross && rand.nextBoolean()) {
                    int temp = object.getInt0();
                    object.setInt0(object.getInt1() + 1);
                    object.setInt1(temp);
                } else {
                    if (!two || i % 2 == 0)
                        object.setInt0(object.getInt0() + 1);
                    else
                        object.setInt1(object.getInt1() + 1);
                }
            }

            if (!aborts || rand.nextBoolean() || i == 0) {
                if ((flags & ConcurrentClient.MERGE_BY_SOURCE) != 0) {
                    transaction.getOrCreateVersionMap().setSource(source);

                    if (source == null)
                        Concurrent._nullSources.put(transaction.getOrCreateVersionMap(), number);

                    CommitStatus result = transaction.commit();

                    if (result == CommitStatus.SUCCESS) {
                        if (rand.nextInt(10) == 0) {
                            Source previous = source;

                            if (rand.nextInt(4) == 0)
                                source = new Source(new TestConnectionVersion(number), interceptionId, false);
                            else
                                source = null;

                            if (previous != source)
                                interceptionId++;
                        }
                    }
                } else
                    future = transaction.commitAsync(null);
            } else
                transaction.abort();

            // PlatformConsole.readLine();

            // Assert no memory leak

            if (i == count / 2 || i == count * 9 / 10) {
                TestsHelper.assertMemory("at i=" + i);

                /*
                 * Otherwise sometimes threads run one after the other and there is no
                 * transaction aborted, which breaks the asserts.
                 */
                try {
                    Thread.sleep(1);
                } catch (java.lang.InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        return future;
    }

    static final class TestConnectionVersion extends ConnectionBase.Version {

        private final int _value;

        public TestConnectionVersion(int value) {
            super(null, ConnectionBase.FIELD_COUNT);
            setUnion(new Reference(null, false), true);
            _value = value;
        }

        public int getValue() {
            return _value;
        }
    }

    public static final class Transfer {

        public static final int TOTAL = 1000;

        private static final int COEF = 10;

        public static final void between0And1(SimpleClass object) {
            int transfer;

            if (PlatformAdapter.getRandomBoolean()) {
                transfer = PlatformAdapter.getRandomInt(object.getInt0() / COEF + 1);
                object.setInt0(object.getInt0() - transfer);
                object.setInt1(object.getInt1() + transfer);
            } else {
                transfer = PlatformAdapter.getRandomInt(object.getInt1() / COEF + 1);
                object.setInt1(object.getInt1() - transfer);
                object.setInt0(object.getInt0() + transfer);
            }

            object.setInt3(transfer);
            assertTotal(object, false);
        }

        public static final void to2(SimpleClass object) {
            int transfer;

            if (PlatformAdapter.getRandomBoolean()) {
                transfer = PlatformAdapter.getRandomInt(object.getInt0() / COEF + 1);
                object.setInt0(object.getInt0() - transfer);
                object.setInt2(object.getInt2() + transfer);
            } else {
                transfer = PlatformAdapter.getRandomInt(object.getInt1() / COEF + 1);
                object.setInt1(object.getInt1() - transfer);
                object.setInt2(object.getInt2() + transfer);
            }

            object.setInt3(transfer);
            assertTotal(object, false);
        }

        public static final void assertTotal(SimpleClass object, boolean transact) {
            if (transact)
                Transaction.start();

            int total = object.getInt0() + object.getInt1() + object.getInt2();
            Log.write("assertTotal: " + object.getInt0() + ", " + object.getInt1() + ", " + object.getInt2() + " (" + total + "), transfer: " + object.getInt3());
            Debug.assertAlways(total == TOTAL);
            Assert.assertTrue(object.getInt0() + object.getInt1() + object.getInt2() == TOTAL);

            if (transact)
                Transaction.getCurrent().abort();
        }
    }
}
