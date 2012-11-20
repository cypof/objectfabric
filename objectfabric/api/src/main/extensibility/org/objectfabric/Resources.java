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

final class Resources {

    private static final int ZERO_REPLACEMENT = -1;

    static final int DEFAULT_CAPACITY = 10;

    private int[] _indexes;

    private Resource[] _array;

    private int _size;

    Resources() {
        _indexes = new int[OpenMap.CAPACITY];
        _array = new Resource[DEFAULT_CAPACITY];
    }

    final Resource get(int index) {
        return _array[index];
    }

    final int size() {
        return _size;
    }

    final boolean contains(Resource resource) {
        return indexOf(resource) >= 0;
    }

    final int indexOf(Resource resource) {
        int index = resource.hash() & (_indexes.length - 1);

        for (int i = OpenMap.attemptsStart(_indexes.length); i >= 0; i--) {
            int arrayIndex = _indexes[index];

            if (arrayIndex == 0)
                break;

            if (getFromArrayIndex(arrayIndex) == resource)
                return arrayIndex == ZERO_REPLACEMENT ? 0 : arrayIndex;

            index = (index + 1) & (_indexes.length - 1);
        }

        return -1;
    }

    final int add(Resource object) {
        int index;

        while ((index = tryToAdd(object)) == OpenMap.REHASH) {
            int[] previous = _indexes;

            for (;;) {
                _indexes = new int[_indexes.length << OpenMap.TIMES_TWO_SHIFT];

                if (rehash(previous))
                    break;
            }
        }

        if (index >= 0) {
            if (Debug.ENABLED) {
                for (int i = 0; i < index; i++)
                    Debug.assertion(_array[i] != object);

                for (int i = index; i < _array.length; i++)
                    Debug.assertion(_array[i] == null);
            }

            if (index == _array.length)
                _array = Helper.extend(_array);

            _array[index] = object;
        } else {
            if (Debug.ENABLED)
                Debug.assertion(get(-index - 1) == object);
        }

        if (Debug.ENABLED)
            checkInvariants(false);

        return index;
    }

    final Resource pollPartOfClear() {
        Resource object = null;

        if (_size > 0) {
            object = _array[_size - 1];
            removePartOfClear(object);
            _array[--_size] = null;
        }

        if (Debug.ENABLED)
            checkInvariants(true);

        return object;
    }

    //

    private final Resource getFromArrayIndex(int arrayIndex) {
        if (Debug.ENABLED)
            Debug.assertion(arrayIndex < _size);

        if (arrayIndex > 0)
            return _array[arrayIndex];

        if (arrayIndex == ZERO_REPLACEMENT)
            return _array[0];

        return null;
    }

    private final int tryToAdd(Resource resource) {
        int index = resource.hash() & (_indexes.length - 1);

        for (int i = OpenMap.attemptsStart(_indexes.length); i >= 0; i--) {
            int arrayIndex = _indexes[index];

            if (arrayIndex == 0) {
                _indexes[index] = _size == 0 ? ZERO_REPLACEMENT : _size;
                return _size++;
            }

            if (getFromArrayIndex(arrayIndex) == resource)
                return -(arrayIndex == ZERO_REPLACEMENT ? 0 : arrayIndex) - 1;

            index = (index + 1) & (_indexes.length - 1);
        }

        return OpenMap.REHASH;
    }

    private final int tryToAddForRehash(Resource resource, int arrayIndex) {
        int index = resource.hash() & (_indexes.length - 1);

        for (int i = OpenMap.attemptsStart(_indexes.length); i >= 0; i--) {
            if (_indexes[index] == 0) {
                _indexes[index] = arrayIndex;
                return index;
            }

            index = (index + 1) & (_indexes.length - 1);
        }

        return OpenMap.REHASH;
    }

    private final boolean rehash(int[] previous) {
        for (int i = previous.length - 1; i >= 0; i--) {
            if (previous[i] != 0) {
                if (Debug.ENABLED)
                    Debug.assertion(indexOf(getFromArrayIndex(previous[i])) < 0);

                int result = tryToAddForRehash(getFromArrayIndex(previous[i]), previous[i]);

                if (result < 0) {
                    if (Debug.ENABLED)
                        Debug.assertion(result == OpenMap.REHASH);

                    return false;
                }
            }
        }

        return true;
    }

    private final void removePartOfClear(Resource resource) {
        int index = resource.hash() & (_indexes.length - 1);

        for (;;) {
            if (getFromArrayIndex(_indexes[index]) == resource) {
                _indexes[index] = 0;
                break;
            }

            index = (index + 1) & (_indexes.length - 1);
        }
    }

    // Debug

    private final void checkInvariants(boolean clearing) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        for (int i = 0; i < _array.length; i++)
            Debug.assertion((_array[i] != null) == (i < _size));

        int count = 0;

        for (int i = _indexes.length - 1; i >= 0; i--) {
            if (_indexes[i] != 0) {
                Debug.assertion(_indexes[i] > 0 || _indexes[i] == ZERO_REPLACEMENT);
                count++;
            }
        }

        for (int i = 0; i < _size; i++)
            if (indexOf(_array[i]) != i)
                if (!clearing)
                    Debug.assertion(indexOf(_array[i]) == i);

        Debug.assertion(_size == count);

        if (Debug.SLOW_CHECKS) {
            for (int i = 0; i < _array.length; i++)
                for (int j = 0; j < i; j++)
                    Debug.assertion(_array[i] == null || _array[i] != _array[j]);

            for (int i = 0; i < _indexes.length; i++)
                for (int j = 0; j < i; j++)
                    Debug.assertion(_indexes[i] == 0 || _indexes[i] != _indexes[j]);
        }
    }
}
