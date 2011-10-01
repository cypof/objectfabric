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

import com.objectfabric.CompileTimeSettings;
import com.objectfabric.Privileged;
import com.objectfabric.Stats;

public final class Debug extends Privileged {

    public static final boolean ENABLED = false;

    public static final boolean TESTING = false;

    public static final boolean SLOW_CHECKS = ENABLED && false;

    public static final boolean THREADS = ENABLED && false;

    public static final boolean ONE_NIO_THREAD = ENABLED && false;

    public static final boolean THREADS_LOG = ENABLED && false;

    public static final boolean STM_LOG = ENABLED && false;

    public static final boolean STACKS = ENABLED && true && PlatformAdapter.PLATFORM != CompileTimeSettings.PLATFORM_GWT;

    public static final boolean COMMUNICATIONS = ENABLED && true;

    public static final boolean COMMUNICATIONS_LOG = ENABLED && false;

    public static final boolean COMMUNICATIONS_LOG_ALL = ENABLED && false;

    public static final boolean COMMUNICATIONS_LOG_HTTP = ENABLED && false;

    public static final boolean COMMUNICATIONS_LOG_TLS = ENABLED && false;

    public static final boolean COMMUNICATIONS_DISABLE_TIMEOUTS = ENABLED && false;

    public static final boolean PERSISTENCE_LOG = ENABLED && false;

    // TODO: create thread to GC continuously
    public static final boolean HIGH_GC = ENABLED && false;

    public static final boolean DGC = ENABLED && true;

    public static final boolean DGC_LOG = ENABLED && true;

    public static final boolean RANDOMIZE_TRANSFER_LENGTHS = ENABLED && true;

    public static final int RANDOMIZED_TRANSFER_LIMIT = 5000;

    //

    public static String ProcessName = "";

    public static boolean AssertNoConflict;

    private Debug() {
    }

    public interface Assertion {

        boolean run();
    }

    public static void assertion(Assertion value) {
        if (!value.run()) {
            assertion(false);
            value.run();
        }
    }

    public static void assertion(boolean value) {
        if (!ENABLED)
            throw new IllegalStateException();

        assertAlways(value);
    }

    public static void assertAlways(boolean value) {
        if (!value) {
            // throw new AssertionError();
            new AssertionError().printStackTrace();
        }
    }

    public static void fail() {
        assertion(false);
    }

    public static void failAlways() {
        assertAlways(false);
    }

    //

    public static void expectException() {
        Privileged.expectException();
    }

    //

    public static void disableEqualsOrHashCheck() {
        Privileged.disableEqualsOrHashCheck();
    }

    public static void enableEqualsOrHashCheck() {
        Privileged.enableEqualsOrHashCheck();
    }

    // Used to ensure Debug is disabled when packaging

    public static void main(String[] args) {
        if (ENABLED || TESTING || Stats.ENABLED)
            throw new RuntimeException("Debug is enabled");
    }
}
