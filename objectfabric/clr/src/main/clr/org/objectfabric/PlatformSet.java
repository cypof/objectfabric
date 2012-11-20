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

import cli.ObjectFabric.CLRSet;
import cli.ObjectFabric.EnumeratorIterator;

/**
 * Platform classes allow other implementations to be used for ports like GWT and .NET.
 * The .NET specific implementations make it possible to remove Java components like
 * Reflection and Security from the ObjectFabric dll.
 */
public final class PlatformSet<E> extends CLRSet implements Iterable<E> {

    public boolean add(E element) {
        return Add(element);
    }

    public boolean contains(E element) {
        return Contains(element);
    }

    public boolean isEmpty() {
        return get_Count() == 0;
    }

    @SuppressWarnings("unchecked")
    public Iterator<E> iterator() {
        return new EnumeratorIterator(GetEnumerator());
    }

    public boolean remove(E element) {
        return Remove(element);
    }

    public int size() {
        return get_Count();
    }
}
