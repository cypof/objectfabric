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

import org.objectfabric.ThreadAssert.SingleThreaded;

/**
 * Allows a thread to be suspended, a stack is then used to store current state until work
 * is resumed.
 */
@SingleThreaded
abstract class Continuation {

    private final List<Object> _stack;

    Continuation(List<Object> stack) {
        if (stack == null)
            throw new IllegalArgumentException();

        _stack = stack;
    }

    final List<Object> getInterruptionStack() {
        return _stack;
    }

    //

    public final boolean interrupted() {
        return _stack.size() > 0;
    }

    public final void interrupt(Object state) {
        interruptImpl(state);
    }

    public final Object resume() {
        return resumeImpl();
    }

    /*
     * Use wrappers for primitives to remove Java boxing classes and avoid .cctor problem
     * with the .NET version.
     */

    final void interruptBoolean(boolean state) {
        BooleanBox box = new BooleanBox();
        box.Value = state;
        interruptImpl(box);
    }

    final boolean resumeBoolean() {
        BooleanBox box = (BooleanBox) resumeImpl();
        return box.Value;
    }

    static final class BooleanBox {

        boolean Value;
    }

    //

    final void interruptByte(byte state) {
        ByteBox box = new ByteBox();
        box.Value = state;
        interruptImpl(box);
    }

    final byte resumeByte() {
        ByteBox box = (ByteBox) resumeImpl();
        return box.Value;
    }

    static final class ByteBox {

        byte Value;
    }

    //

    final void interruptInt(int state) {
        IntBox box = new IntBox();
        box.Value = state;
        interruptImpl(box);
    }

    final int resumeInt() {
        IntBox box = (IntBox) resumeImpl();
        return box.Value;
    }

    static final class IntBox {

        int Value;
    }

    //

    final void interruptLong(long state) {
        LongBox box = new LongBox();
        box.Value = state;
        interruptImpl(box);
    }

    final long resumeLong() {
        LongBox box = (LongBox) resumeImpl();
        return box.Value;
    }

    private static final class LongBox {

        long Value;
    }

    //

    private final void interruptImpl(Object state) {
        _stack.add(state);

        if (Debug.STACKS && Platform.get().value() != Platform.GWT)
            _stack.add(Platform.get().getCurrentStack());
    }

    private final Object resumeImpl() {
        if (Debug.STACKS && Platform.get().value() != Platform.GWT)
            Platform.get().assertCurrentStack(_stack.removeLast());

        return _stack.removeLast();
    }

    // Debug

    final List<Object> getThreadContextObjects() {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        List<Object> list = new List<Object>();
        OverrideAssert.add(this);
        addThreadContextObjects(list);
        OverrideAssert.end(this);
        return list;
    }

    void addThreadContextObjects(List<Object> list) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        OverrideAssert.set(this);
        list.add(this);
    }
}
