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

import org.junit.Assert;
import org.objectfabric.generated.SimpleClass;

public abstract class ConcurrentClient {

    // Client flags

    public static final int USE_TWO_INTEGERS = 1 << 0;

    public static final int USE_ONE_INTEGER_PER_CLIENT = 1 << 1;

    public static final int USE_ABORTS = 1 << 2;

    public static final int NO_WRITE = 1 << 3;

    public static final int CROSS = 1 << 4;

    public static final int TRANSFER = 1 << 5;

    public static final int USE_ALL = (1 << 6) - 1;

    static {
        int value = USE_TWO_INTEGERS;
        value |= USE_ONE_INTEGER_PER_CLIENT;
        value |= USE_ABORTS;
        value |= NO_WRITE;
        value |= CROSS;
        value |= TRANSFER;
        Debug.assertAlways(USE_ALL == value);
    }

    private ConcurrentClient() {
    }

    protected void onStart() {
    }

    @SuppressWarnings("unused")
    public static void loop(final SimpleClass object, final int client, int count, final int flags) {
        for (int i = 0; i < count; i++) {
            final int step = i;

            ExpectedExceptionThrower.executeAndReturnException(new Runnable() {

                @Override
                public void run() {
                    object.workspace().atomic(new Runnable() {

                        @Override
                        public void run() {
                            step(object, client, step, flags);
                        }
                    });
                }
            });

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
    }

    private static void step(SimpleClass object, int client, int step, int flags) {
        if ((flags & NO_WRITE) == 0) {
            if ((flags & TRANSFER) != 0) {
                if (Platform.get().randomDouble() < 0.99)
                    Transfer.between0And1(object);
                else
                    Transfer.to2(object);
            } else if ((flags & USE_ONE_INTEGER_PER_CLIENT) != 0) {
                switch (client) {
                    case 0:
                        object.int0(object.int0() + 1);
                        break;
                    case 1:
                        object.int1(object.int1() + 1);
                        break;
                    case 2:
                        object.int2(object.int2() + 1);
                        break;
                    case 3:
                        object.int3(object.int3() + 1);
                        break;
                    default:
                        Debug.fail();
                }
            } else if (((flags & CROSS) != 0) && Platform.get().randomBoolean()) {
                int temp = object.int0();
                object.int0(object.int1() + 1);
                object.int1(temp);
            } else {
                if (((flags & USE_TWO_INTEGERS) == 0) || step % 2 == 0)
                    object.int0(object.int0() + 1);
                else
                    object.int1(object.int1() + 1);
            }
        }

        if (((flags & USE_ABORTS) != 0) && Platform.get().randomBoolean() && step != 0) {
            ExpectedExceptionThrower.expectException();
            ExpectedExceptionThrower.throwRuntimeException("abort");
        }
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

        if ((flags & TRANSFER) != 0)
            sb.append("TRANSFER, ");

        return sb.toString();
    }

    public static final class Transfer {

        public static final int TOTAL = 1000;

        private static final int COEF = 10;

        public static final void between0And1(SimpleClass object) {
            int transfer;

            if (Platform.get().randomBoolean()) {
                transfer = Platform.get().randomInt(object.int0() / COEF + 1);
                object.int0(object.int0() - transfer);
                object.int1(object.int1() + transfer);
            } else {
                transfer = Platform.get().randomInt(object.int1() / COEF + 1);
                object.int1(object.int1() - transfer);
                object.int0(object.int0() + transfer);
            }

            object.int3(transfer);
            assertTotal(object);
        }

        public static final void to2(SimpleClass object) {
            int transfer;

            if (Platform.get().randomBoolean()) {
                transfer = Platform.get().randomInt(object.int0() / COEF + 1);
                object.int0(object.int0() - transfer);
                object.int2(object.int2() + transfer);
            } else {
                transfer = Platform.get().randomInt(object.int1() / COEF + 1);
                object.int1(object.int1() - transfer);
                object.int2(object.int2() + transfer);
            }

            object.int3(transfer);
            assertTotal(object);
        }

        public static final void assertTotal(final SimpleClass object) {
            object.workspace().atomicRead(new Runnable() {

                @Override
                public void run() {
                    int total = object.int0() + object.int1() + object.int2();
                    Log.write("assertTotal: " + object.int0() + ", " + object.int1() + ", " + object.int2() + " (" + total + "), transfer: " + object.int3());
                    Debug.assertAlways(total == TOTAL);
                    Assert.assertTrue(object.int0() + object.int1() + object.int2() == TOTAL);
                }
            });
        }
    }
}
