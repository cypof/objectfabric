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

package com.objectfabric;

import java.util.Arrays;

import com.objectfabric.misc.Bits;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SparseArrayHelper;
import com.objectfabric.misc.Utils;

/**
 * Store removals and inserts instead of moving elements to be able to raise events
 * corresponding to updates and to avoid re-sending shifted elements in the distributed
 * case.
 */
@SuppressWarnings("unchecked")
final class TListVersion extends TArrayVersion {

    private static final int DEFAULT_TEMP_CAPACITY = 4;

    private int[] _removals, _inserts;

    private int _removalsCount, _insertsCount;

    // TODO Keep track of adds separately (don't record as inserts)

    private boolean _cleared;

    /*
     * This cache is not only for performance but to make sure snapshots versions sizes
     * shadow shared version's.
     */
    private int _size = -1;

    /*
     * Publish can be slow for this class, so assign an id to each version and if the id
     * did not change since last publish attempt, skip it. This should help the second
     * attempt succeeding by making it much faster and avoid starvation of publishing
     * thread.
     */
    private int _versionId; // TODO test case for onPublish

    /*
     * Past writes must be copied when offset by inserts and removals to shadow shared
     * version which can be offset anytime.
     */
    private Object[] _copied;

    private int _copiedStart, _versionIdOnCopy;

    public TListVersion(TObject.Version shared) {
        super(shared, -1);
    }

    public int[] getRemovals() {
        if (Debug.ENABLED)
            if (_removals == null)
                Debug.assertion(_removalsCount == 0);

        return _removals;
    }

    public int getRemovalsCount() {
        return _removalsCount;
    }

    public int[] getInserts() {
        if (Debug.ENABLED)
            if (_inserts == null)
                Debug.assertion(_insertsCount == 0);

        return _inserts;
    }

    public int getInsertsCount() {
        return _insertsCount;
    }

    public boolean getCleared() {
        return _cleared;
    }

    public void setCleared(boolean value) {
        _cleared = value;
    }

    //

    public Object[] getCopied() {
        return _copied;
    }

    public int getCopiedStart() {
        return _copiedStart;
    }

    public void clearCollection() {
        setBits(null);
        setValues(null);

        _removals = null;
        _inserts = null;
        _removalsCount = 0;
        _insertsCount = 0;
        _cleared = true;
        _copied = null;

        if (Debug.ENABLED)
            Debug.assertion(!sizeValid());
    }

    public int offset(int index) {
        if (_inserts != null) {
            // Optim for adds, they are always at the end
            if (_inserts[0] < index) {
                int count = Math.abs(binarySearch(_inserts, _insertsCount, index));
                index -= count;
            }
        }

        return offsetAdd(_removals, _removalsCount, index);
    }

    private static int offsetAdd(int[] array, int length, int index) {
        if (array != null) {
            // Optim for adds, they are always at the end
            if (array[0] <= index) {
                int count = Math.abs(binarySearch(array, length, index));
                index += count;
            }
        }

        return index;
    }

    @SuppressWarnings("null")
    public void remove(int index) {
        /*
         * Move subsequent writes.
         */
        if (getBits() != null) {
            int[] droppedIndexes = null;
            Object[] dropped = null;
            int droppedCount = 0;

            for (int i = getBits().length - 1; i >= 0; i--) {
                if (getBits()[i] != null) {
                    boolean values = getValues() != null && getValues()[i] != null;

                    int start = getBits()[i].IntIndex << Bits.BITS_PER_UNIT_SHIFT;
                    int end = start + Bits.BITS_PER_UNIT - 1;

                    if (index <= end) {
                        if (index > start) {
                            int bitIndex = index & Bits.BIT_INDEX_MASK;
                            getBits()[i].Value = Bits.remove(getBits()[i].Value, bitIndex);

                            if (values) {
                                int length = getValues()[i].length - (index - start + 1);
                                PlatformAdapter.arraycopy(getValues()[i], index - start + 1, getValues()[i], index - start, length);
                            }
                        } else {
                            if (index != start && Bits.get(getBits()[i].Value, 0)) {
                                if (droppedIndexes == null)
                                    droppedIndexes = new int[DEFAULT_TEMP_CAPACITY];

                                if (values && dropped == null)
                                    dropped = new Object[droppedIndexes.length];

                                if (droppedCount == droppedIndexes.length) {
                                    droppedIndexes = Utils.extend(droppedIndexes);

                                    if (values)
                                        dropped = Utils.extend(dropped);
                                }

                                droppedIndexes[droppedCount] = start;

                                if (values)
                                    dropped[droppedCount] = getValues()[i][0];

                                droppedCount++;
                            }

                            getBits()[i].Value >>>= 1;

                            if (values)
                                PlatformAdapter.arraycopy(getValues()[i], 1, getValues()[i], 0, getValues()[i].length - 1);
                        }

                        if (values)
                            getValues()[i][getValues()[i].length - 1] = null;
                    }
                }
            }

            for (int i = 0; i < droppedCount; i++) {
                if (Debug.ENABLED)
                    Debug.assertion(!getBit(droppedIndexes[i] - 1));

                setBit(droppedIndexes[i] - 1);
            }

            if (dropped != null)
                for (int i = 0; i < droppedCount; i++)
                    set(droppedIndexes[i] - 1, dropped[i]);
        }

        addRemoval(index);
    }

    private void addRemoval(int index) {
        /*
         * Offset inserts and remove if present.
         */
        if (_inserts != null) {
            int t = binarySearch(_inserts, _insertsCount, index);

            if (t >= 0) {
                // Not found, offset following
                for (int i = _insertsCount - 1; i >= t; i--)
                    _inserts[i]--;

                index -= t;
            } else {
                // Remove
                if (_insertsCount > 1) {
                    for (int i = -t; i < _insertsCount; i++)
                        _inserts[i - 1] = _inserts[i] - 1;
                } else
                    _inserts = null;

                _insertsCount--;

                if (Debug.ENABLED)
                    checkInvariants();

                return;
            }
        }

        if (Debug.ENABLED)
            Debug.assertion(!getCleared());

        /*
         * Otherwise add new removal.
         */
        if (_removals == null)
            _removals = new int[SparseArrayHelper.DEFAULT_CAPACITY];
        else if (_removalsCount == _removals.length)
            _removals = Utils.extend(_removals);

        int t = Math.abs(binarySearch(_removals, _removalsCount, index));

        for (int i = _removalsCount; i > t; i--)
            _removals[i] = _removals[i - 1] - 1;

        _removals[t] = index;
        _removalsCount++;

        if (Debug.ENABLED)
            checkInvariants();
    }

    void writeRemoval(int index) {
        if (_removals == null)
            _removals = new int[SparseArrayHelper.DEFAULT_CAPACITY];
        else if (_removalsCount == _removals.length)
            _removals = Utils.extend(_removals);

        _removals[_removalsCount++] = index;
    }

    @SuppressWarnings("null")
    public void insert(int index) {
        boolean marked = false;

        /*
         * Move subsequent writes.
         */
        if (getBits() != null) {
            int[] droppedIndexes = null;
            Object[] dropped = null;
            int droppedCount = 0;

            for (int i = getBits().length - 1; i >= 0; i--) {
                if (getBits()[i] != null) {
                    boolean values = getValues() != null && getValues()[i] != null;

                    int start = getBits()[i].IntIndex << Bits.BITS_PER_UNIT_SHIFT;
                    int end = start + Bits.BITS_PER_UNIT - 1;

                    if (index <= end) {
                        if (Bits.get(getBits()[i].Value, Bits.BITS_PER_UNIT - 1)) {
                            if (droppedIndexes == null)
                                droppedIndexes = new int[DEFAULT_TEMP_CAPACITY];

                            if (values && dropped == null)
                                dropped = new Object[droppedIndexes.length];

                            if (droppedCount == droppedIndexes.length) {
                                droppedIndexes = Utils.extend(droppedIndexes);

                                if (values)
                                    dropped = Utils.extend(dropped);
                            }

                            droppedIndexes[droppedCount] = end;

                            if (values)
                                dropped[droppedCount] = getValues()[i][getValues()[i].length - 1];

                            droppedCount++;
                        }

                        if (index > start) {
                            int bitIndex = index & Bits.BIT_INDEX_MASK;

                            for (int b = Bits.BITS_PER_UNIT - 1; b > bitIndex; b--) {
                                boolean value = Bits.get(getBits()[i].Value, b - 1);
                                getBits()[i].Value = Bits.set(getBits()[i].Value, b, value);
                            }

                            getBits()[i].Value = Bits.set(getBits()[i].Value, bitIndex);
                            marked = true;

                            if (values) {
                                int length = getValues()[i].length - (index - start + 1);
                                PlatformAdapter.arraycopy(getValues()[i], index - start, getValues()[i], index - start + 1, length);
                            }
                        } else {
                            getBits()[i].Value <<= 1;

                            if (index == start) {
                                getBits()[i].Value = Bits.set(getBits()[i].Value, 0);
                                marked = true;
                            }

                            if (getBits()[i].Value == 0)
                                getValues()[i] = null;
                            else if (values) {
                                PlatformAdapter.arraycopy(getValues()[i], 0, getValues()[i], 1, getValues()[i].length - 1);
                                getValues()[i][0] = null; // For invariants
                            }
                        }
                    }
                }
            }

            for (int i = 0; i < droppedCount; i++) {
                if (Debug.ENABLED)
                    Debug.assertion(!getBit(droppedIndexes[i] + 1));

                setBit(droppedIndexes[i] + 1);
            }

            if (dropped != null)
                for (int i = 0; i < droppedCount; i++)
                    set(droppedIndexes[i] + 1, dropped[i]);
        }

        if (!marked)
            setBit(index);

        addInsert(index);
    }

    private void addInsert(int index) {
        /*
         * Remove removal if present.
         */
        if (_removals != null) {
            int removalIndex = -1;
            int insertsIndex = -1;

            if (_inserts != null) {
                // Optim for adds, they are always at the end
                if (_inserts[0] < index) {
                    insertsIndex = binarySearch(_inserts, _insertsCount, index);

                    if (insertsIndex > 0)
                        removalIndex = index - insertsIndex;
                }
            }

            if (removalIndex >= 0) {
                int t = binarySearch(_removals, _removalsCount, removalIndex);

                if (t < 0) {
                    if (_removalsCount > 1) {
                        for (int i = -t - 1; i < _removalsCount - 1; i++)
                            _removals[i] = _removals[i + 1] + 1;
                    } else
                        _removals = null;

                    _removalsCount--;

                    for (int i = insertsIndex; i < _insertsCount; i++)
                        _inserts[i]++;

                    if (Debug.ENABLED)
                        checkInvariants();

                    return;
                }
            }
        }

        if (_inserts == null)
            _inserts = new int[SparseArrayHelper.DEFAULT_CAPACITY];
        else if (_insertsCount == _inserts.length)
            _inserts = Utils.extend(_inserts);

        // Optim for adds, they are always at the end

        if (_insertsCount > 0 && index > _inserts[_insertsCount - 1])
            _inserts[_insertsCount++] = index;
        else {
            int t = binarySearch(_inserts, _insertsCount, index);

            if (t < 0)
                t = -t - 1; // To offset also existing

            for (int i = _insertsCount - 1; i >= t; i--)
                _inserts[i + 1] = _inserts[i] + 1;

            _inserts[t] = index;
            _insertsCount++;
        }

        if (Debug.ENABLED)
            checkInvariants();
    }

    void writeInsert(int index) {
        if (_inserts == null)
            _inserts = new int[SparseArrayHelper.DEFAULT_CAPACITY];
        else if (_insertsCount == _inserts.length)
            _inserts = Utils.extend(_inserts);

        _inserts[_insertsCount++] = index;
    }

    static int binarySearch(int[] array, int length, int value) {
        int mid = 0, low = 0, high = length;

        while (low <= high) {
            mid = (low + high) >>> 1;

            if (mid == length)
                return mid;

            int midVal = array[mid];

            if (midVal < value)
                low = mid + 1;
            else if (midVal > value)
                high = mid - 1;
            // This is to go to last occurrence when several equal
            else if (mid + 1 < length && array[mid + 1] == midVal)
                low = mid + 1;
            else
                return -(mid + 1);
        }

        return low;
    }

    //

    public int getSizeDelta() {
        return _insertsCount - _removalsCount;
    }

    public boolean sizeValid() {
        return _size >= 0;
    }

    public int size() {
        if (Debug.ENABLED)
            Debug.assertion(sizeValid());

        return _size;
    }

    public void setSize(int value) {
        _size = value;
    }

    @Override
    public boolean onPublishing(Snapshot newSnapshot, int mapIndex) {
        if (Debug.ENABLED) {
            boolean retry = Helper.getInstance().getRetryCount() > 0;
            Debug.assertion(!sizeValid() || retry);
            Debug.assertion(getCopied() == null || retry);
        }

        updateFromSnapshot(newSnapshot, mapIndex, false);
        return true;
    }

    @Override
    public Version onPastChanged(Snapshot newSnapshot, int mapIndex) {
        if (Debug.ENABLED)
            Debug.assertion(sizeValid());

        TListVersion result = (TListVersion) cloneThis(false);
        result.updateFromSnapshot(newSnapshot, mapIndex, true);
        return result;
    }

    private void updateFromSnapshot(Snapshot snapshot, int mapIndex, boolean clone) {
        TListVersion version = null;

        for (int i = mapIndex - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
            version = (TListVersion) TransactionSets.getVersionFromSharedVersion(snapshot.getWrites()[i], getShared());

            if (version != null)
                break;
        }

        int previousSize = 0;

        if (!getCleared()) {
            if (version != null)
                previousSize = version.size();
            else
                previousSize = ((TListSharedVersion) getShared()).size();
        }

        _size = previousSize + getSizeDelta();

        //

        if (version != null)
            _versionId = version._versionId + 1;
        else
            _versionId = 1;

        //

        if (!getCleared()) {
            if (_removalsCount > 0 || _insertsCount > 0) {
                int copiedStart;

                if (_removalsCount > 0 && _insertsCount > 0)
                    copiedStart = Math.min(_removals[0], _inserts[0] + 1);
                else if (_removalsCount > 0)
                    copiedStart = _removals[0];
                else {
                    if (Debug.ENABLED)
                        Debug.assertion(_insertsCount > 0);

                    copiedStart = _inserts[0] + 1;
                }

                int copiedCount = _size - copiedStart;

                if (copiedCount <= 0 || copiedStart > previousSize)
                    _copied = null;
                else {
                    boolean skip = version != null && version._versionId == _versionIdOnCopy;

                    if (skip) {
                        if (Debug.ENABLED)
                            Debug.assertion(copiedStart == _copiedStart && _copied.length == copiedCount);
                    } else {
                        _copied = new Object[copiedCount];

                        for (int i = 0; i < copiedCount; i++)
                            _copied[i] = TList.get(copiedStart + i, snapshot, mapIndex, this);

                        _copiedStart = copiedStart;
                        _versionIdOnCopy = version != null ? version._versionId : 0;
                    }
                }
            }
        }

        if (Debug.ENABLED) {
            if (_copied != null)
                Debug.assertion(_copiedStart + _copied.length == _size);

            checkInvariants();
        }
    }

    @Override
    public TObject.Version merge(TObject.Version target, TObject.Version next, int flags) {
        TListVersion source = (TListVersion) next;

        if (Debug.ENABLED) {
            Debug.assertion(this == target);
            Debug.assertion(!isShared());

            target.checkInvariants();
            source.checkInvariants();

            if ((flags & MERGE_FLAG_PRIVATE) != 0)
                Debug.assertion(_versionId == 0 && source._versionId == 0);
        }

        if ((flags & MERGE_FLAG_CLONE) != 0) {
            if (Debug.ENABLED) {
                Debug.assertion(_removals == null);
                Debug.assertion(_removalsCount == 0);
                Debug.assertion(_copied == null);
                Debug.assertion(_inserts == null);
                Debug.assertion(_insertsCount == 0);
                Debug.assertion(_size == -1);
                Debug.assertion(_versionId == 0);
                Debug.assertion(!_cleared);
            }

            setBits(source.getBits());
            setValues(source.getValues());

            _removals = source._removals;
            _removalsCount = source._removalsCount;
            _inserts = source._inserts;
            _insertsCount = source._insertsCount;
            _size = source._size;
            _versionId = source._versionId;
            _copied = source._copied;
            _cleared = source._cleared;

            return this;
        }

        if (source.getCleared()) {
            if (Debug.ENABLED) {
                Debug.assertion(source._removals == null);
                Debug.assertion(source._removalsCount == 0);
                Debug.assertion(source._copied == null);
            }

            setBits(source.getBits());
            setValues(source.getValues());

            _removals = null;
            _removalsCount = 0;
            _inserts = source._inserts;
            _insertsCount = source._insertsCount;
            _size = source._size;
            _versionId = source._versionId;
            _copied = null;
            _cleared = true;

            if (Debug.ENABLED)
                checkInvariants();

            return this;
        }

        TListVersion merged = this;
        TListVersion toRead = this;

        if (source._removals != null || source._inserts != null) {
            merged = (TListVersion) cloneThis(false);

            if ((flags & (MERGE_FLAG_PRIVATE | MERGE_FLAG_BY_COPY)) != 0) {
                toRead = merged;
                merged = this;
            }

            if (Debug.ENABLED) {
                Debug.assertion(merged.getBits() == toRead.getBits());
                Debug.assertion(merged.getValues() == toRead.getValues());
                toRead.checkInvariants();
                merged.checkInvariants();
            }

            if (toRead.getBits() != null && source.offsets(toRead.getBits())) {
                merged.setBits(null);
                merged.setValues(null);
                merged.reindexRemovalsAndInsertsAsPrevious(source);
                TListVersion reindexedSource = merged;

                for (int i = toRead.getBits().length - 1; i >= 0; i--) {
                    if (toRead.getBits()[i] != null) {
                        int firstWrite = toRead.getBits()[i].IntIndex << Bits.BITS_PER_UNIT_SHIFT;

                        for (int b = 0; b < Bits.BITS_PER_UNIT; b++) {
                            if (Bits.get(toRead.getBits()[i].Value, b)) {
                                int index = firstWrite + b;
                                int count = binarySearch(reindexedSource._removals, reindexedSource._removalsCount, index);

                                if (count >= 0) { // Otherwise means removed
                                    index -= count;
                                    index = offsetAdd(reindexedSource._inserts, reindexedSource._insertsCount, index);

                                    if (merged.getBits() == null) {
                                        if (Debug.ENABLED)
                                            Debug.assertion(merged.getValues() == null);

                                        merged.setBits(PlatformAdapter.createBitsArray(toRead.getBits().length));
                                    }

                                    merged.setBit(index);
                                    merged.set(index, toRead.get(firstWrite + b));
                                }
                            }
                        }
                    }
                }
            }

            // Merge inserts & removals

            if (toRead._removals == null && toRead._inserts == null) {
                if (Debug.ENABLED)
                    Debug.assertion(toRead._insertsCount == 0 && toRead._removalsCount == 0);

                if (toRead.getCleared()) {
                    merged._removals = null;
                    merged._removalsCount = 0;
                } else {
                    merged._removals = source._removals;
                    merged._removalsCount = source._removalsCount;
                }

                merged._inserts = source._inserts;
                merged._insertsCount = source._insertsCount;
            } else {
                merged._removals = toRead._removals;
                merged._removalsCount = toRead._removalsCount;
                merged._inserts = toRead._inserts;
                merged._insertsCount = toRead._insertsCount;

                if (source._removalsCount > 0 || source._insertsCount > 0) {
                    /*
                     * Both arrays can be modified for inserts or removals.
                     */
                    if ((flags & MERGE_FLAG_BY_COPY) != 0) {
                        if (merged._removals != null) {
                            int[] temp = new int[merged._removals.length];
                            PlatformAdapter.arraycopy(merged._removals, 0, temp, 0, merged._removals.length);
                            merged._removals = temp;
                        }

                        if (merged._inserts != null) {
                            int[] temp = new int[merged._inserts.length];
                            PlatformAdapter.arraycopy(merged._inserts, 0, temp, 0, merged._inserts.length);
                            merged._inserts = temp;
                        }
                    }

                    for (int i = 0; i < source._removalsCount; i++)
                        merged.addRemoval(source._removals[i]);

                    for (int i = 0; i < source._insertsCount; i++)
                        merged.addInsert(source._inserts[i]);
                }
            }
        }

        if ((flags & MERGE_FLAG_PRIVATE) != 0) {
            if (Debug.ENABLED) {
                Debug.assertion(!merged.sizeValid() && !source.sizeValid());
                Debug.assertion(toRead._copied == null && source._copied == null);
            }
        } else {
            if (Debug.ENABLED)
                Debug.assertion(merged.sizeValid() && source.sizeValid());

            merged._size = source._size;
            merged._versionId = source._versionId;

            if (!merged.getCleared() && source._copied != null) {
                merged._copied = source._copied;
                merged._copiedStart = source._copiedStart;
            }

            if (merged._copied != null)
                for (int i = merged._size; i < merged._copied.length; i++)
                    merged._copied[i] = null; // GC
        }

        TObject.Version result = merged.superMerge(target, next, flags);

        if (Debug.ENABLED)
            ((TListVersion) result).checkInvariants();

        return result;
    }

    boolean offsets(Bits.Entry[] bits) {
        int min = -1;

        if (_removals != null)
            min = _removals[0];

        if (_inserts != null)
            if (min < 0 || _inserts[0] < min)
                min = _inserts[0];

        if (min >= 0) {
            for (int i = bits.length - 1; i >= 0; i--) {
                if (bits[i] != null) {
                    int firstWrite = bits[i].IntIndex << Bits.BITS_PER_UNIT_SHIFT;
                    int lastWrite = firstWrite + Bits.BITS_PER_UNIT - 1;

                    if (lastWrite >= min) // TODO use a mask instead
                        for (int b = min & Bits.BIT_INDEX_MASK; b < Bits.BITS_PER_UNIT; b++)
                            if (Bits.get(bits[i].Value, b))
                                return true;
                }
            }
        }

        return false;
    }

    private void reindexRemovalsAndInsertsAsPrevious(TListVersion source) {
        _removals = source.getRemovalsReindexedAsPrevious(_removalsCount);
        _removalsCount = source._removalsCount;

        _inserts = source.getInsertsReindexedAsPrevious(_insertsCount);
        _insertsCount = source._insertsCount;
    }

    int[] getRemovalsReindexedAsPrevious(int removalsCount) {
        if (Debug.ENABLED)
            if (_removals == null)
                Debug.assertion(_removalsCount == 0);

        int[] result = null;

        if (_removals != null) {
            // Optim, at the end of merge, arrays will be aggregated
            result = new int[removalsCount + _removalsCount];

            for (int i = 0; i < _removalsCount; i++)
                result[i] = _removals[i] + i;

            if (Debug.ENABLED)
                for (int j = 0; j < _removalsCount; j++)
                    for (int i = 0; i < j; i++)
                        if (i != j)
                            Debug.assertion(result[i] != result[j]);
        }

        return result;
    }

    int[] getInsertsReindexedAsPrevious(int insertsCount) {
        if (Debug.ENABLED)
            if (_inserts == null)
                Debug.assertion(_insertsCount == 0);

        int[] result = null;

        if (_inserts != null) {
            // Optim, at the end of merge, arrays will be aggregated
            result = new int[insertsCount + _insertsCount];

            for (int i = 0; i < _insertsCount; i++)
                result[i] = _inserts[i] - i;
        }

        return result;
    }

    private final TObject.Version superMerge(TObject.Version target, TObject.Version next, int flags) {
        return super.merge(target, next, flags);
    }

    @Override
    public void visit(com.objectfabric.Visitor visitor) {
        visitor.visit(this);
    }

    // Debug

    @Override
    public boolean hasWritesForDebug() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        /*
         * Does not work as an empty version can be created by merging two non-empty
         * versions.
         */
        return true;
    }

    @Override
    public void checkInvariants_() {
        super.checkInvariants_();

        Debug.assertion(_removalsCount >= 0);
        Debug.assertion(_insertsCount >= 0);

        if (getCleared()) {
            Debug.assertion(_removals == null);

            for (int i = 0; i < _insertsCount; i++)
                if (_inserts != null)
                    Debug.assertion(_inserts[i] == i);

            Debug.assertion(_copied == null);
        }

        if (_removals != null) {
            for (int i = 0; i < _removalsCount; i++)
                Debug.assertion(_removals[i] >= 0);

            int last = -1;

            for (int i = 0; i < _removalsCount; i++) {
                Debug.assertion(_removals[i] >= last);
                last = _removals[i];
            }
        }

        if (_inserts != null) {
            for (int i = 0; i < _insertsCount; i++)
                Debug.assertion(_inserts[i] >= 0);

            int last = -1;

            for (int i = 0; i < _insertsCount; i++) {
                Debug.assertion(_inserts[i] >= last);
                last = _inserts[i];
            }

            for (int j = 0; j < _insertsCount; j++)
                for (int i = 0; i < j; i++)
                    if (i != j)
                        Debug.assertion(_inserts[i] != _inserts[j]);
        }

        if (_inserts != null && _removals != null) {
            for (int i = 0; i < _removalsCount; i++)
                Debug.assertion(!Arrays.asList(_inserts).contains(_removals[i]));

            for (int i = 0; i < _insertsCount; i++)
                Debug.assertion(!Arrays.asList(_removals).contains(_inserts[i]));
        }
    }
}