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

import of4gwt.TObject.Version;
import of4gwt.misc.Debug;
import of4gwt.misc.SparseArrayHelper;

// TODO: replace with UserTObjectSetAndList when hash stored in Version
final class VersionSetAndList {

    private static final int ZERO_REPLACEMENT = -1;

    private static final int REMOVED = -2;

    private int[] _indexes = new int[SparseArrayHelper.DEFAULT_CAPACITY];

    private Version[] _array = new Version[10];

    private int _size;

    public Version get(int index) {
        return _array[index];
    }

    public int size() {
        return _size;
    }

    public boolean contains(Version shared) {
        return indexOf(shared) >= 0;
    }

    public int indexOf(Version shared) {
        if (Debug.ENABLED)
            Debug.assertion(shared.isShared());

        int index = shared.getSharedHashCode_objectfabric() & (_indexes.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(_indexes.length); i >= 0; i--) {
            int arrayIndex = _indexes[index];

            if (arrayIndex == 0)
                break;

            if (getVersion(arrayIndex) == shared)
                return arrayIndex == ZERO_REPLACEMENT ? 0 : arrayIndex;

            index = (index + 1) & (_indexes.length - 1);
        }

        return -1;
    }

    public int add(Version shared) {
        int index;

        while ((index = tryToAdd(shared)) == SparseArrayHelper.REHASH) {
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
                    Debug.assertion(_array[i] != shared);

            if (_size == _array.length)
                _array = Version.extendArray(_array);

            arrayIndex = _size++;
            _array[arrayIndex] = shared;
        } else {
            if (Debug.ENABLED)
                Debug.assertion(index == SparseArrayHelper.ALREADY_PRESENT);

            arrayIndex = SparseArrayHelper.ALREADY_PRESENT;
        }

        if (Debug.ENABLED)
            checkInvariants();

        return arrayIndex;
    }

    public Version poll() {
        Version version = null;

        if (_size > 0) {
            version = _array[_size - 1];
            remove(version);
            _array[--_size] = null;
        }

        if (Debug.ENABLED)
            checkInvariants();

        return version;
    }

    public Version pollPartOfClear() {
        Version version = null;

        if (_size > 0) {
            version = _array[_size - 1];
            removePartOfClear(version);
            _array[--_size] = null;
        }

        if (Debug.ENABLED)
            checkInvariants();

        return version;
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

    private Version getVersion(int arrayIndex) {
        if (Debug.ENABLED)
            Debug.assertion(arrayIndex < _size);

        if (arrayIndex > 0)
            return _array[arrayIndex];

        if (arrayIndex == ZERO_REPLACEMENT)
            return _array[0];

        return null;
    }

    private int tryToAdd(Version shared) {
        int hash = shared.getSharedHashCode_objectfabric();
        int result;

        if ((result = tryToAdd(shared, hash, false)) == SparseArrayHelper.REHASH)
            result = tryToAdd(shared, hash, true);

        return result;
    }

    private int tryToAdd(Version shared, int hash, boolean overrideRemoved) {
        int index = hash & (_indexes.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(_indexes.length); i >= 0; i--) {
            int arrayIndex = _indexes[index];

            if (arrayIndex == 0 || (overrideRemoved && arrayIndex == REMOVED)) {
                _indexes[index] = _size == 0 ? ZERO_REPLACEMENT : _size;
                return index;
            }

            if (getVersion(arrayIndex) == shared)
                return SparseArrayHelper.ALREADY_PRESENT;

            index = (index + 1) & (_indexes.length - 1);
        }

        return SparseArrayHelper.REHASH;
    }

    private int tryToAddForRehash(Version shared, int arrayIndex) {
        int hash = shared.getSharedHashCode_objectfabric();
        int index = hash & (_indexes.length - 1);

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
                    Debug.assertion(indexOf(getVersion(previous[i])) < 0);

                int result = tryToAddForRehash(getVersion(previous[i]), previous[i]);

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

    private int remove(Version shared) {
        int hash = shared.getSharedHashCode_objectfabric();
        int index = hash & (_indexes.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(_indexes.length); i >= 0; i--) {
            if (getVersion(_indexes[index]) == shared) {
                _indexes[index] = REMOVED;
                return index;
            }

            index = (index + 1) & (_indexes.length - 1);
        }

        return -1;
    }

    private void removePartOfClear(Version shared) {
        int hash = shared.getSharedHashCode_objectfabric();
        int index = hash & (_indexes.length - 1);

        for (;;) {
            if (getVersion(_indexes[index]) == shared) {
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
