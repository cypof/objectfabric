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

/**
 * Replaces ArrayList, to avoid using getClass() and other reflective methods, as
 * reflection is removed on .NET during the dll merge.
 * <nl>
 * Warning: item equality is identity.
 */
@SuppressWarnings("unchecked")
final class List<E> {

    static final int CAPACITY = 4;

    private E[] _items;

    private int _size = 0;

    List() {
        _items = (E[]) new Object[CAPACITY];
    }

    final boolean add(E item) {
        ensureCapacity();
        _items[_size++] = item;
        return true;
    }

    final void addAll(E[] array) {
        for (int i = 0; i < array.length; i++) {
            ensureCapacity();
            _items[_size++] = array[i];
        }
    }

    final void addAll(List<E> list) {
        for (int i = 0; i < list.size(); i++) {
            ensureCapacity();
            _items[_size++] = list.get(i);
        }
    }

    final void add(int index, E item) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index <= _size);

        ensureCapacity();
        Platform.arraycopy(_items, index, _items, index + 1, _size - index);
        _items[index] = item;
        _size++;
    }

    final E get(int index) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < _size);

        return _items[index];
    }

    final E last() {
        return _items[_items.length - 1];
    }

    final E set(int index, E value) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < _size);

        E temp = _items[index];
        _items[index] = value;
        return temp;
    }

    final void clear() {
        for (int i = _size - 1; i >= 0; i--)
            _items[i] = null;

        if (Debug.ENABLED)
            for (int i = 0; i < _items.length; i++)
                Debug.assertion(_items[i] == null);

        _size = 0;
    }

    final boolean contains(Object item) {
        for (int i = _size - 1; i >= 0; i--)
            if (_items[i] == item)
                return true;

        return false;
    }

    final int indexOf(Object item) {
        for (int i = _size - 1; i >= 0; i--)
            if (_items[i] == item)
                return i;

        return -1;
    }

    final E remove(int index) {
        E item = _items[index];
        int numMoved = _size - index - 1;

        if (numMoved > 0)
            Platform.arraycopy(_items, index + 1, _items, index, numMoved);

        _items[--_size] = null; // GC
        return item;
    }

    final boolean remove(Object item) {
        for (int i = _size - 1; i >= 0; i--) {
            if (_items[i] == item) {
                int numMoved = _size - i - 1;

                if (numMoved > 0)
                    Platform.arraycopy(_items, i + 1, _items, i, numMoved);

                _items[--_size] = null; // GC
                return true;
            }
        }

        return false;
    }

    final E removeLast() {
        _size--;
        E value = _items[_size];
        _items[_size] = null; // GC
        return value;
    }

    final int size() {
        return _size;
    }

    final Object[] toArray() {
        Object[] array = new Object[_size];
        Platform.arraycopy(_items, 0, array, 0, _size);
        return array;
    }

    final void copyToFixed(E[] array) {
        Platform.arraycopy(_items, 0, array, 0, _size);
    }

    final E[] copyToWithResizeAndNullEnd(E[] array) {
        if (_size <= array.length) {
            copyToFixed(array);

            if (_size < array.length) {
                // null-terminate (C.f. ArrayList)
                array[_size] = null;
            }

            return array;
        }

        return (E[]) Platform.get().copyWithTypedResize(_items, _size, array);
    }

    private final void ensureCapacity() {
        if (_size == _items.length) {
            E[] temp = (E[]) new Object[_items.length << OpenMap.TIMES_TWO_SHIFT];
            Platform.arraycopy(_items, 0, temp, 0, _items.length);
            _items = temp;
        }
    }
}