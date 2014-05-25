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

public class Queue<E> {

    static final int DEFAULT_CAPACITY = 4;

    private E[] _items;

    private int _first = 0;

    private int _last = 0;

    private int _size = 0;

    @SuppressWarnings("unchecked")
    public Queue() {
        if (Debug.ENABLED)
            Debug.assertion(Utils.nextPowerOf2(DEFAULT_CAPACITY) == DEFAULT_CAPACITY);

        _items = (E[]) new Object[DEFAULT_CAPACITY];
    }

    public final void add(E item) {
        if (Debug.ENABLED) {
            Debug.assertion(item != null);

            if (item instanceof Buff) {
                Debug.assertion(((Buff) item).remaining() > 0);
                Debug.assertion(((Buff) item).getDuplicates() > 0);
            }
        }

        ensureCapacity();
        _items[_last] = item;
        increaseSize();
    }

    public final void clear() {
        for (int i = 0; i < _size; i++) {
            if (Debug.ENABLED)
                Debug.assertion(_items[getRealIndex(i)] != null);

            _items[getRealIndex(i)] = null;
        }

        _first = 0;
        _last = 0;
        _size = 0;

        if (Debug.ENABLED)
            size();
    }

    public final E get(int index) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < _size);

        return _items[getRealIndex(index)];
    }

    public final boolean isEmpty() {
        return size() == 0;
    }

    public final E peek() {
        if (_size == 0)
            return null;

        return _items[_first];
    }

    public final E poll() {
        if (_size == 0)
            return null;

        E ret = _items[_first];
        _items[_first] = null;
        decreaseSize();
        return ret;
    }

    public final void set(int index, E item) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < _size);

        _items[getRealIndex(index)] = item;
    }

    public final int size() {
        if (Debug.ENABLED)
            if (_size == 0)
                for (int i = 0; i < _items.length; i++)
                    Debug.assertion(_items[i] == null);

        return _size;
    }

    //

    private final int getRealIndex(int index) {
        return (_first + index) & (_items.length - 1);
    }

    private final void increaseSize() {
        _last = (_last + 1) & (_items.length - 1);
        _size++;
    }

    private final void decreaseSize() {
        _first = (_first + 1) & (_items.length - 1);
        _size--;
    }

    @SuppressWarnings("unchecked")
    private final void ensureCapacity() {
        if (_size < _items.length)
            return;

        E[] array = (E[]) new Object[_items.length * 2];

        if (_first < _last)
            Platform.arraycopy(_items, _first, array, 0, _last - _first);
        else {
            Platform.arraycopy(_items, _first, array, 0, _items.length - _first);
            Platform.arraycopy(_items, 0, array, _items.length - _first, _last);
        }

        _first = 0;
        _last = _items.length;
        _items = array;
    }
}