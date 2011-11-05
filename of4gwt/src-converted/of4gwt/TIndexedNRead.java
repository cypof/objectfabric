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

import of4gwt.misc.Bits;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.SparseArrayHelper;

class TIndexedNRead extends TObject.Version {

    private Bits.Entry[] _bits;

    public TIndexedNRead(TObject.Version shared) {
        super(shared);
    }

    public final Bits.Entry[] getBits() {
        return _bits;
    }

    public final void setBits(Bits.Entry[] value) {
        _bits = value;
    }

    public final boolean hasBits() {
        return _bits != null;
    }

    public final boolean getBit(int index) {
        return Bits.get(_bits, index);
    }

    public final void setBit(int index) {
        if (Debug.ENABLED)
            Debug.assertion(!isShared());

        if (_bits == null)
            _bits = PlatformAdapter.createBitsArray(Bits.SPARSE_BITSET_DEFAULT_CAPACITY);

        while (!Bits.tryToSet(_bits, index))
            reindex();
    }

    public final void unsetBit(int index) {
        if (Debug.ENABLED)
            Debug.assertion(getBit(index));

        Bits.unset(_bits, index);
    }

    final int addEntry(Bits.Entry entry) {
        if (_bits == null)
            _bits = PlatformAdapter.createBitsArray(Bits.SPARSE_BITSET_DEFAULT_CAPACITY);

        int index;

        while ((index = Bits.add(_bits, entry)) < 0)
            reindex();

        return index;
    }

    /**
     * @param old
     */
    public void reindexed(Bits.Entry[] old) {
    }

    private final void reindex() {
        if (Debug.ENABLED)
            Debug.assertion(!isShared());

        Bits.Entry[] old = _bits;

        for (;;) {
            _bits = PlatformAdapter.createBitsArray(_bits.length << SparseArrayHelper.TIMES_TWO_SHIFT);

            if (Bits.reindex(old, _bits)) {
                reindexed(old);
                break;
            }
        }
    }

    //

    @Override
    public boolean validAgainst(VersionMap map, Snapshot snapshot, int start, int stop) {
        if (_bits != null) {
            for (int i = start; i < stop; i++) {
                boolean skip = false;

                if (map.getSource() != null) {
                    Connection.Version c = snapshot.getVersionMaps()[i].getSource() != null ? snapshot.getVersionMaps()[i].getSource().Connection : null;
                    skip = map.getSource().Connection == c;
                }

                if (!skip) {
                    TObject.Version write = TransactionSets.getVersionFromSharedVersion(snapshot.getWrites()[i], getUnion());

                    if (write != null) {
                        TIndexedNVersion version = (TIndexedNVersion) write;

                        if (version.getBits() != null)
                            if (Bits.intersects(_bits, version.getBits()))
                                return false;
                    }
                }
            }
        }

        return true;
    }

    @Override
    public TObject.Version merge(TObject.Version target, TObject.Version next, int flags) {
        TIndexedNRead source = (TIndexedNRead) next;

        if (getBits() != null && source.getBits() != null) {
            TIndexedNRead merged = this;

            for (;;) {
                boolean result;

                if ((flags & MERGE_FLAG_COPY_ARRAY_ELEMENTS) != 0)
                    result = Bits.mergeByCopy(merged.getBits(), source.getBits());
                else
                    result = Bits.mergeInPlace(merged.getBits(), source.getBits());

                if (result)
                    break;

                if (Debug.ENABLED) {
                    Debug.assertion(!isShared());
                    Debug.assertion((flags & MERGE_FLAG_CLONE) == 0);
                }

                if (merged == target && ((flags & (MERGE_FLAG_PRIVATE | MERGE_FLAG_COPY_ARRAY_ELEMENTS)) == 0))
                    merged = (TIndexedNRead) cloneThis((flags & MERGE_FLAG_READS) != 0, false);

                merged.reindex();
            }

            return merged;
        }

        if (getBits() == null && source.getBits() != null) {
            Bits.Entry[] bits = source.getBits();

            if ((flags & MERGE_FLAG_COPY_ARRAYS) != 0) {
                Bits.Entry[] temp = PlatformAdapter.createBitsArray(bits.length);
                PlatformAdapter.arraycopy(bits, 0, temp, 0, temp.length);
                bits = temp;
            }

            setBits(bits);
        }

        return this;
    }

    @Override
    public void visit(of4gwt.Visitor visitor) {
        visitor.visit(this);
    }

    public static final Bits.Entry[] setBit(Bits.Entry[] bits, int index) {
        return Bits.set(bits, index);
    }

    public Bits.Entry[] getReadOnlys() {
        return null;
    }

    public Bits.Entry[] getTransients() {
        return null;
    }

    @Override
    public boolean allModifiedFieldsAreReadOnly() {
        Bits.Entry[] bits = getBits();
        Bits.Entry[] readOnlys = getReadOnlys();

        if (bits == null)
            return true;

        if (readOnlys == null)
            return false;

        return Bits.isEmpty(Bits.andNot(bits, readOnlys));
    }

    @Override
    public void mergeReadOnlyFields() {
        Bits.Entry[] bits = getBits();
        Bits.Entry[] ro = getReadOnlys();

        if (ro != null) {
            Bits.Entry[] toDo = Bits.and(bits, ro);

            /*
             * Important: do not allow update of read-only fields. Users expect them not
             * to change and might store user name or other security related info. Read
             * volatile snapshot to sync with shared first.
             */
            TIndexedNRead shared = (TIndexedNRead) getUnionAsVersion();
            shared.getTrunk().getSharedSnapshot();
            Bits.Entry[] toAvoid = shared.getBits();
            setBits(Bits.andNot(toDo, toAvoid));
            VersionMap.merge(shared, this, MERGE_FLAG_NONE);
            setBits(Bits.andNot(bits, ro));
        }
    }

    // Debug

    @Override
    public void getContentForDebug(List<Object> list) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        list.add(_bits);
    }

    @Override
    public boolean hasWritesForDebug() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (_bits != null)
            for (int i = _bits.length - 1; i >= 0; i--)
                if (_bits[i] != null)
                    return true;

        return false;
    }
}