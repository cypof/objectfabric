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

package of4gwt.misc;

import java.util.Collection;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * Replaces ArrayList, to avoid using getClass() and other reflective methods, as those
 * methods fail on .NET due to the dll merge.
 * <nl>
 * Warning: item equality is identity.
 */
@SuppressWarnings("unchecked")
public class List<E> {

    private static final int DEFAULT_CAPACITY = 4;

    private E[] _items;

    private int _size = 0;

    public List() {
        if (Debug.ENABLED)
            Debug.assertion(Utils.nextPowerOf2(DEFAULT_CAPACITY) == DEFAULT_CAPACITY);

        _items = (E[]) new Object[DEFAULT_CAPACITY];
    }

    public final boolean add(E item) {
        ensureCapacity();
        _items[_size++] = item;
        return true;
    }

    public final void addAll(E[] array) {
        for (int i = 0; i < array.length; i++) {
            ensureCapacity();
            _items[_size++] = array[i];
        }
    }

    public final void add(int index, E item) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < _size);

        ensureCapacity();
        PlatformAdapter.arraycopy(_items, index, _items, index + 1, _size - index);
        _items[index] = item;
        _size++;
    }

    public final E get(int index) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < _size);

        return _items[index];
    }

    public final E getLast() {
        return _items[_items.length - 1];
    }

    public final E set(int index, E value) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < _size);

        E temp = _items[index];
        _items[index] = value;
        return temp;
    }

    public final void clear() {
        for (int i = _size - 1; i >= 0; i--)
            _items[i] = null;

        if (Debug.ENABLED)
            for (int i = 0; i < _items.length; i++)
                Debug.assertion(_items[i] == null);

        _size = 0;
    }

    public final boolean contains(Object item) {
        for (int i = _size - 1; i >= 0; i--)
            if (_items[i] == item)
                return true;

        return false;
    }

    public final int indexOf(Object item) {
        for (int i = _size - 1; i >= 0; i--)
            if (_items[i] == item)
                return i;

        return -1;
    }

    public final boolean remove(Object item) {
        for (int i = _size - 1; i >= 0; i--) {
            if (_items[i] == item) {
                int numMoved = _size - i - 1;

                if (numMoved > 0)
                    PlatformAdapter.arraycopy(_items, i + 1, _items, i, numMoved);

                _items[--_size] = null; // GC
                return true;
            }
        }

        return false;
    }

    public final E remove(int index) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < _size);

        E oldValue = _items[index];
        int numMoved = _size - index - 1;

        if (numMoved > 0)
            PlatformAdapter.arraycopy(_items, index + 1, _items, index, numMoved);

        _items[--_size] = null; // GC
        return oldValue;
    }

    public final E removeLast() {
        _size--;
        E value = _items[_size];
        _items[_size] = null; // GC
        return value;
    }

    public final int size() {
        return _size;
    }

    public final Object[] toArray() {
        Object[] array = new Object[_size];
        PlatformAdapter.arraycopy(_items, 0, array, 0, _size);
        return array;
    }

    public final void copyToFixed(E[] array) {
        PlatformAdapter.arraycopy(_items, 0, array, 0, _size);
    }

    public final E[] copyToWithResizeAndNullEnd(E[] array) {
        if (_size <= array.length) {
            copyToFixed(array);

            if (_size < array.length) {
                // null-terminate (C.f. ArrayList)
                array[_size] = null;
            }

            return array;
        }

        return (E[]) PlatformAdapter.copyWithTypedResize(_items, _size, array);
    }

    private final void ensureCapacity() {
        if (_size < _items.length)
            return;

        _items = (E[]) Utils.extend(_items);
    }

    public static final class FullImpl<E> extends List<E> implements java.util.List<E> {

        public boolean isEmpty() {
            throw new UnsupportedOperationException();
        }

        public java.util.Iterator<E> iterator() {
            return new Iterator(this);
        }

        public <T> T[] toArray(T[] a) {
            throw new UnsupportedOperationException();
        }

        public boolean containsAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        public boolean addAll(Collection<? extends E> c) {
            throw new UnsupportedOperationException();
        }

        public boolean addAll(int index, Collection<? extends E> c) {
            throw new UnsupportedOperationException();
        }

        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        public int lastIndexOf(Object o) {
            throw new UnsupportedOperationException();
        }

        public ListIterator<E> listIterator() {
            throw new UnsupportedOperationException();
        }

        public ListIterator<E> listIterator(int index) {
            throw new UnsupportedOperationException();
        }

        public java.util.List<E> subList(int fromIndex, int toIndex) {
            throw new UnsupportedOperationException();
        }
    }

    private static class Iterator implements java.util.Iterator {

        private final List _list;

        private int _index;

        public Iterator(List list) {
            _list = list;
        }

        public boolean hasNext() {
            return _index != _list._size;
        }

        public Object next() {
            if (_index >= _list._size)
                throw new NoSuchElementException();

            return _list._items[_index++];
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}