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

package of4gwt;

import of4gwt.TObject.UserTObject;
import of4gwt.misc.Debug;
import of4gwt.misc.SparseArrayHelper;

@SuppressWarnings("unchecked")
final class UserTObjectSetAndList<T extends UserTObject> {

    private static final int ZERO_REPLACEMENT = -1;

    private static final int REMOVED = -2;

    private int[] _indexes = new int[SparseArrayHelper.DEFAULT_CAPACITY];

    private UserTObject[] _array = new UserTObject[10];

    private int _size;

    public T get(int index) {
        return (T) _array[index];
    }

    public int size() {
        return _size;
    }

    public boolean contains(UserTObject object) {
        return indexOf(object) >= 0;
    }

    public int indexOf(UserTObject object) {
        int index = object.getSharedHashCode_objectfabric() & (_indexes.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(_indexes.length); i >= 0; i--) {
            int arrayIndex = _indexes[index];

            if (arrayIndex == 0)
                break;

            if (getUserTObject(arrayIndex) == object)
                return arrayIndex == ZERO_REPLACEMENT ? 0 : arrayIndex;

            index = (index + 1) & (_indexes.length - 1);
        }

        return -1;
    }

    public int add(UserTObject object) {
        int index;

        while ((index = tryToAdd(object)) == SparseArrayHelper.REHASH) {
            int[] previous = _indexes;

            for (;;) {
                _indexes = new int[_indexes.length << SparseArrayHelper.TIMES_TWO_SHIFT];

                if (rehash(previous))
                    break;
            }
        }

        int arrayIndex;

        if (index >= 0) {
            if (Debug.ENABLED)
                for (int i = 0; i < _size; i++)
                    Debug.assertion(_array[i] != object);

            if (_size == _array.length)
                _array = UserTObject.extendArray(_array);

            arrayIndex = _size++;
            _array[arrayIndex] = object;
        } else {
            if (Debug.ENABLED)
                Debug.assertion(index == SparseArrayHelper.ALREADY_PRESENT);

            arrayIndex = SparseArrayHelper.ALREADY_PRESENT;
        }

        if (Debug.ENABLED)
            checkInvariants();

        return arrayIndex;
    }

    public T poll() {
        UserTObject object = null;

        if (_size > 0) {
            object = _array[_size - 1];
            remove(object);
            _array[--_size] = null;
        }

        if (Debug.ENABLED)
            checkInvariants();

        return (T) object;
    }

    public T pollPartOfClear() {
        UserTObject object = null;

        if (_size > 0) {
            object = _array[_size - 1];
            removePartOfClear(object);
            _array[--_size] = null;
        }

        if (Debug.ENABLED)
            checkInvariants();

        return (T) object;
    }

    public void clear() {
        for (int i = _size - 1; i >= 0; i--) {
            removePartOfClear(_array[i]);
            _array[i] = null;
        }

        _size = 0;
        
        if (Debug.ENABLED)
            checkInvariants();
    }

    //

    private UserTObject getUserTObject(int arrayIndex) {
        if (Debug.ENABLED)
            Debug.assertion(arrayIndex < _size);

        if (arrayIndex > 0)
            return _array[arrayIndex];

        if (arrayIndex == ZERO_REPLACEMENT)
            return _array[0];

        return null;
    }

    private int tryToAdd(UserTObject object) {
        int result;

        if ((result = tryToAdd(object, false)) == SparseArrayHelper.REHASH)
            result = tryToAdd(object, true);

        return result;
    }

    private int tryToAdd(UserTObject object, boolean overrideRemoved) {
        int index = object.getSharedHashCode_objectfabric() & (_indexes.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(_indexes.length); i >= 0; i--) {
            int arrayIndex = _indexes[index];

            if (arrayIndex == 0 || (overrideRemoved && arrayIndex == REMOVED)) {
                _indexes[index] = _size == 0 ? ZERO_REPLACEMENT : _size;
                return index;
            }

            if (getUserTObject(arrayIndex) == object)
                return SparseArrayHelper.ALREADY_PRESENT;

            index = (index + 1) & (_indexes.length - 1);
        }

        return SparseArrayHelper.REHASH;
    }

    private int tryToAddForRehash(UserTObject object, int arrayIndex) {
        int index = object.getSharedHashCode_objectfabric() & (_indexes.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(_indexes.length); i >= 0; i--) {
            if (_indexes[index] == 0) {
                _indexes[index] = arrayIndex;
                return index;
            }

            index = (index + 1) & (_indexes.length - 1);
        }

        return SparseArrayHelper.REHASH;
    }

    private boolean rehash(int[] previous) {
        for (int i = previous.length - 1; i >= 0; i--) {
            if (previous[i] != 0 && previous[i] != REMOVED) {
                if (Debug.ENABLED)
                    Debug.assertion(indexOf(getUserTObject(previous[i])) < 0);

                int result = tryToAddForRehash(getUserTObject(previous[i]), previous[i]);

                if (result < 0) {
                    if (Debug.ENABLED)
                        Debug.assertion(result == SparseArrayHelper.REHASH);

                    return false;
                }
            }
        }

        if (Debug.ENABLED)
            for (int i = _indexes.length - 1; i >= 0; i--)
                Debug.assertion(_indexes[i] != REMOVED);

        return true;
    }

    private int remove(UserTObject object) {
        int hash = object.getSharedHashCode_objectfabric();
        int index = hash & (_indexes.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(_indexes.length); i >= 0; i--) {
            if (getUserTObject(_indexes[index]) == object) {
                _indexes[index] = REMOVED;
                return index;
            }

            index = (index + 1) & (_indexes.length - 1);
        }

        return -1;
    }

    private void removePartOfClear(UserTObject object) {
        int hash = object.getSharedHashCode_objectfabric();
        int index = hash & (_indexes.length - 1);

        for (;;) {
            if (getUserTObject(_indexes[index]) == object) {
                _indexes[index] = 0;
                break;
            }

            index = (index + 1) & (_indexes.length - 1);
        }
    }

    private void checkInvariants() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        for (int i = 0; i < _size; i++)
            Debug.assertion(_array[i] != null);

        int count = 0;

        for (int i = _indexes.length - 1; i >= 0; i--) {
            if (_indexes[i] != 0 && _indexes[i] != REMOVED) {
                Debug.assertion(_indexes[i] > 0 || _indexes[i] == ZERO_REPLACEMENT);
                count++;
            }
        }

        Debug.assertion(_size == count);

        if (Debug.SLOW_CHECKS)
            for (int i = 0; i < _indexes.length; i++)
                for (int j = 0; j < i; j++)
                    if (i != j)
                        Debug.assertion(_indexes[i] == 0 || _indexes[i] == REMOVED || _indexes[i] != _indexes[j]);
    }
}
