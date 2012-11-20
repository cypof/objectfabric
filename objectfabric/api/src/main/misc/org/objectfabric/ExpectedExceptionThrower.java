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

import java.util.NoSuchElementException;

import org.objectfabric.TObject.Transaction;

/**
 * Handy for setting a filter on Exception Breakpoints so they don't stop on exceptions
 * thrown from here. Also contains blocks with finally as Eclipse stops on finally when
 * exceptions are re-thrown.
 */
abstract class ExpectedExceptionThrower {

    private static final PlatformThreadLocal<Integer> _expectedExceptions;

    private static volatile boolean _disabled;

    static {
        if (Debug.ENABLED)
            _expectedExceptions = new PlatformThreadLocal<Integer>();
        else
            _expectedExceptions = null;
    }

    private ExpectedExceptionThrower() {
    }

    // Cannot be expected

    public static void throwClosedObjectException() {
        throw new ClosedException();
    }

    // Expected

    public static boolean isCounterDisabled() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        return _disabled;
    }

    public static void disableCounter() {
        if (Debug.ENABLED)
            _disabled = true;
    }

    public static void enableCounter() {
        if (Debug.ENABLED)
            _disabled = false;
    }

    public static void expectException() {
        if (Debug.ENABLED) {
            if (!_disabled) {
                Integer value = _expectedExceptions.get();
                _expectedExceptions.set(value != null ? value + 1 : 1);
            }
        }
    }

    private static void checkExpected() {
        if (Debug.ENABLED) {
            if (!_disabled) {
                Integer value = _expectedExceptions.get();
                Debug.assertion(value != null && value > 0);

                if (value != null)
                    _expectedExceptions.set(value - 1);
            }
        }
    }

    public static void throwUnsupportedOperationException() {
        checkExpected();
        throw new UnsupportedOperationException();
    }

    public static void throwNoSuchElementException() {
        checkExpected();
        throw new NoSuchElementException();
    }

    public static void throwNullPointerException() {
        checkExpected();
        throw new NullPointerException();
    }

    public static void throwIndexOutOfBoundsException() {
        checkExpected();
        throw new IndexOutOfBoundsException();
    }

    public static void throwRuntimeException(String message) {
        checkExpected();
        throw new RuntimeException(message);
    }

    //

    public static void throwAbortException() {
        throw new AbortException();
    }

    public static void throwRuntimeException(Exception e) {
        throw new RuntimeException(e);
    }

    //

    public static void executeRead(Workspace workspace, Transaction transaction, Runnable runnable) {
        workspace.setTransaction(transaction);

        try {
            runnable.run();
        } finally {
            workspace.setTransaction(transaction.parent());
            TransactionManager.abort(transaction);
        }
    }

    public static boolean executeTransaction(Workspace workspace, Transaction transaction, Runnable runnable) {
        workspace.setTransaction(transaction);
        boolean ok = false;

        try {
            runnable.run();
            ok = true;
        } finally {
            workspace.setTransaction(transaction.parent());

            if (!ok)
                TransactionManager.abort(transaction);
        }

        return TransactionManager.commit(transaction);
    }

    // Debug

    public static Exception executeAndReturnException(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}