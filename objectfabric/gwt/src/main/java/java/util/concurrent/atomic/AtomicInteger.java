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

package java.util.concurrent.atomic;

public class AtomicInteger {

    private int _value;

    public AtomicInteger() {
    }

    public AtomicInteger(int value) {
        _value = value;
    }

    public int addAndGet(int delta) {
        _value += delta;
        return _value;
    }

    public boolean compareAndSet(int expect, int update) {
        if (_value != expect)
            return false;

        _value = update;
        return true;
    }

    public int decrementAndGet() {
        return --_value;
    }

    public int get() {
        return _value;
    }

    public int getAndIncrement() {
        return _value++;
    }

    public int incrementAndGet() {
        return ++_value;
    }

    public void set(int value) {
        _value = value;
    }
}
