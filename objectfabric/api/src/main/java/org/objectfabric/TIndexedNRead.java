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

import org.objectfabric.TIndexed.VersionN;
import org.objectfabric.TObject.Version;

class TIndexedNRead extends TIndexed.Read {

    private Bits.Entry[] _bits;

    final Bits.Entry[] getBits() {
        return _bits;
    }

    final void setBits(Bits.Entry[] value) {
        setBits(value, false);
    }

    final void setBits(Bits.Entry[] value, boolean ctor) {
        if (Debug.ENABLED)
            Debug.assertion(ctor || this != object().shared_());

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
            Debug.assertion(this != object().shared_());

        if (_bits == null)
            _bits = new Bits.Entry[Bits.SPARSE_BITSET_DEFAULT_CAPACITY];

        while (!Bits.tryToSet(_bits, index))
            reindex();
    }

    final int addEntry(Bits.Entry entry) {
        if (_bits == null)
            _bits = new Bits.Entry[Bits.SPARSE_BITSET_DEFAULT_CAPACITY];

        int index;

        while ((index = Bits.add(_bits, entry)) < 0)
            reindex();

        return index;
    }

    final boolean bitsEmpty() {
        if (_bits != null) {
            for (int i = 0; i < _bits.length; i++)
                if (_bits[i] != null)
                    return false;

            if (Debug.ENABLED)
                Debug.assertion(this == object().shared_());
        }

        return true;
    }

    void reindexed(Bits.Entry[] old) {
    }

    private final void reindex() {
        if (Debug.ENABLED)
            Debug.assertion(this != object().shared_());

        Bits.Entry[] old = _bits;

        for (;;) {
            _bits = new Bits.Entry[_bits.length << OpenMap.TIMES_TWO_SHIFT];

            if (Bits.reindex(old, _bits)) {
                reindexed(old);
                break;
            }
        }
    }

    //

    @Override
    boolean validAgainst(VersionMap map, Snapshot snapshot, int start, int stop) {
        if (_bits != null) {
            for (int i = start; i < stop; i++) {
                TObject.Version write = TransactionBase.getVersion(snapshot.writes()[i], object());

                if (write != null) {
                    VersionN version = (VersionN) write;

                    if (version.getBits() != null)
                        if (Bits.intersects(_bits, version.getBits()))
                            return false;
                }
            }
        }

        return true;
    }

    @Override
    TObject.Version merge(TObject.Version target, TObject.Version next, boolean threadPrivate) {
        TIndexedNRead source = (TIndexedNRead) next;

        if (getBits() != null && source.getBits() != null) {
            TIndexedNRead merged = this;

            for (;;) {
                boolean result = Bits.mergeInPlace(merged.getBits(), source.getBits());

                if (result)
                    break;

                if (Debug.ENABLED)
                    Debug.assertion(this != object().shared_());

                if (merged == target && !threadPrivate)
                    merged = (TIndexedNRead) clone(!(this instanceof VersionN));

                merged.reindex();
            }

            return merged;
        }

        if (getBits() == null && source.getBits() != null)
            setBits(source.getBits());

        return this;
    }

    @Override
    void deepCopy(Version source_) {
        TIndexedNRead source = (TIndexedNRead) source_;

        if (!source.bitsEmpty()) {
            if (getBits() != null) {
                TIndexedNRead merged = this;

                for (;;) {
                    boolean result = Bits.mergeByCopy(merged.getBits(), source.getBits());

                    if (result)
                        break;

                    merged.reindex();
                }
            } else {
                Bits.Entry[] bits = new Bits.Entry[source.getBits().length];

                for (int i = bits.length - 1; i >= 0; i--)
                    if (source.getBits()[i] != null)
                        bits[i] = new Bits.Entry(source.getBits()[i].IntIndex, source.getBits()[i].Value);

                setBits(bits);
            }
        }
    }

    @Override
    void clone(Version source) {
        merge(this, source, true);
    }

    @Override
    void visit(Visitor visitor) {
        visitor.visit(this);
    }

    protected static Bits.Entry[] setBit(Bits.Entry[] bits, int index) {
        return Bits.set(bits, index);
    }

    protected Bits.Entry[] getReadOnlys() {
        return null;
    }

    @Override
    void mergeReadOnlyFields() {
        if (Debug.ENABLED)
            Debug.assertion(this != object().shared_());

        Bits.Entry[] bits = getBits();
        Bits.Entry[] ro = getReadOnlys();

        if (ro != null) {
            Bits.Entry[] toDo = Bits.and(bits, ro);

            /*
             * Important: do not allow update of read-only fields. Users expect them not
             * to change and might store user name or other security related info. Read
             * volatile snapshot to sync with shared first.
             */
            TIndexedNRead shared = (TIndexedNRead) object().shared_();
            object().workspace().snapshot();
            Bits.Entry[] toAvoid = shared.getBits();
            setBits(Bits.andNot(toDo, toAvoid));
            VersionMap.merge(shared, this, false);
            setBits(Bits.andNot(bits, ro));
        }
    }

    // Debug

    @Override
    void getContentForDebug(List<Object> list) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        list.add(_bits);
    }

    @Override
    boolean hasWritesForDebug() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (_bits != null)
            for (int i = _bits.length - 1; i >= 0; i--)
                if (_bits[i] != null)
                    return true;

        return false;
    }
}