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

public class AtomicReference<T> {

    private T _value;

    public T get() {
        return _value;
    }

    public void set(T value) {
        _value = value;
    }

    public boolean compareAndSet(T expect, T update) {
        if (_value != expect)
            return false;

        _value = update;
        return true;
    }
}
