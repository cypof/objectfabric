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

package com.objectfabric.misc;

public class QueueOfInt {

    private int[] _items;

    private int _first = 0;

    private int _last = 0;

    private int _size = 0;

    public QueueOfInt() {
        if (Debug.ENABLED)
            Debug.assertion(Utils.nextPowerOf2(Queue.DEFAULT_CAPACITY) == Queue.DEFAULT_CAPACITY);

        _items = new int[Queue.DEFAULT_CAPACITY];
    }

    public final void clear() {
        _first = 0;
        _last = 0;
        _size = 0;
    }

    public final int peek() {
        if (_size == 0)
            throw new RuntimeException();

        return _items[_first];
    }

    public final int poll() {
        if (_size == 0)
            throw new RuntimeException();

        int ret = _items[_first];
        decreaseSize();
        return ret;
    }

    public final boolean add(int item) {
        ensureCapacity();
        _items[_last] = item;
        increaseSize();
        return true;
    }

    public final Object last() {
        if (_size == 0)
            return null;

        return _items[(_last + _items.length - 1) & (_items.length - 1)];
    }

    public final int get(int index) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < _size);

        return _items[getRealIndex(index)];
    }

    public final void set(int index, int value) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < _size);

        _items[getRealIndex(index)] = value;
    }

    public final int size() {
        return _size;
    }

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

    private final void ensureCapacity() {
        if (_size < _items.length)
            return;

        int[] array = new int[_items.length * 2];

        if (_first < _last)
            PlatformAdapter.arraycopy(_items, _first, array, 0, _last - _first);
        else {
            PlatformAdapter.arraycopy(_items, _first, array, 0, _items.length - _first);
            PlatformAdapter.arraycopy(_items, 0, array, _items.length - _first, _last);
        }

        _first = 0;
        _last = _items.length;
        _items = array;
    }
}