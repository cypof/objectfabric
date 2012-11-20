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

package java.util.concurrent;

import java.util.Iterator;

import org.objectfabric.Queue;

public class ConcurrentLinkedQueue<E> extends Queue<E> implements Iterable<E> {

    public boolean contains(Object o) {
        for (int i = 0; i < size(); i++)
            if (o.equals(get(i)))
                return true;

        return false;
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>() {

            int _index;

            @Override
            public boolean hasNext() {
                return _index < size();
            }

            @Override
            public E next() {
                return get(_index++);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
