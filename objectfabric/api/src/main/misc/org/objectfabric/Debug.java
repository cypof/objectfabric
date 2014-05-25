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

public final class Debug {

    public static final boolean ENABLED = false;

    public static final boolean SLOW_CHECKS = ENABLED && true;

    public static final boolean THREADS = ENABLED && false;

    public static final boolean THREADS_LOG = ENABLED && false;

    public static final boolean DISABLE_THREAD_POOL = ENABLED && false;

    public static final boolean ONE_THREAD_PER_POOL = ENABLED && false;

    public static final boolean THREAD_LOG_BLOCKING = ENABLED && false;

    public static final boolean STM_LOG = ENABLED && false;

    public static final boolean TICKS_LOG = ENABLED && true;

    public static final boolean STACKS = ENABLED && true;

    public static final boolean COMMUNICATIONS = ENABLED && true;

    public static final boolean COMMUNICATIONS_LOG = ENABLED && false;

    public static final boolean COMMUNICATIONS_LOG_ALL = ENABLED && false;

    public static final boolean COMMUNICATIONS_LOG_HTTP = ENABLED && false;

    public static final boolean COMMUNICATIONS_LOG_TLS = ENABLED && true;

    public static final boolean COMMUNICATIONS_DISABLE_TIMERS = ENABLED && false;

    public static final boolean COMMUNICATIONS_LOG_TIMEOUTS = ENABLED && false;

    public static final boolean PERSISTENCE_LOG = ENABLED && true;

    // TODO: create thread to GC continuously
    public static final boolean HIGH_GC = ENABLED && false;

    public static final boolean RANDOMIZE_TRANSFER_LENGTHS = ENABLED && true;

    public static final int RANDOMIZED_TRANSFER_LIMIT = 5000;

    public static final boolean RANDOMIZE_FILE_LOAD_ORDER = ENABLED && true;

    //

    // TODO remove
    static void logEqualsOrHash() {
        if (ENABLED)
            Log.write("Called equals or hashCode on a user extendable object.");
    }

    private Debug() {
    }

    interface Assertion {

        boolean run();
    }

    public static void assertion(Assertion value) {
        if (!value.run()) {
            assertion(false);
            value.run();
        }
    }

    public static void assertion(boolean value) {
        assertion(value, null);
    }

    public static void assertion(boolean value, String message) {
        if (!ENABLED)
            throw new IllegalStateException();

        assertAlways(value, message);
    }

    public static void assertAlways(boolean value) {
        assertAlways(value, null);
    }

    public static void assertAlways(boolean value, String message) {
        if (!value) {
            AssertionError error = new AssertionError(message);
            Log.write(Platform.get().getStackAsString(error));
            error.toString();
            // throw error;
        }
    }

    public static void fail() {
        fail("");
    }

    public static void fail(String message) {
        assertion(false, message);
    }

    public static void failAlways() {
        assertAlways(false);
    }

    //

    public static void log(String message) {
        if (!ENABLED)
            throw new IllegalStateException();

        Log.write(message);
    }

    //

    public static void expectException() {
        ExpectedExceptionThrower.expectException();
    }

    // Used to ensure Debug is disabled when packaging

    public static void main(String[] args) {
        if (ENABLED || Stats.ENABLED)
            throw new RuntimeException("Debug is enabled");
    }
}
