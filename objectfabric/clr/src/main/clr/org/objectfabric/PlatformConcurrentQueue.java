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

import java.util.Iterator;

import cli.ObjectFabric.EnumeratorIterator;

@SuppressWarnings("unchecked")
 final class PlatformConcurrentQueue<E> extends cli.ObjectFabric.ConcurrentQueue implements Iterable<E> {

    public final void add(E element) {
        Enqueue(element);
    }

    public void clear() {
        while (poll() != null) {
        }
    }

    public boolean contains(E element) {
        for (E e : this)
            if (e.equals(element))
                return true;

        return false;
    }

    public boolean isEmpty() {
        return IsEmpty();
    }

    public Iterator<E> iterator() {
        return new EnumeratorIterator(GetEnumerator());
    }

    public E peek() {
        return (E) Peek();
    }

    public final E poll() {
        return (E) Poll();
    }

    public final int size() {
        return Size();
    }
}
