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

import org.objectfabric.TIndexed.Version32;
import org.objectfabric.TObject.Version;

class TIndexed32Read extends TIndexed.Read {

    private int _bits;

    final int getBits() {
        return _bits;
    }

    final void setBits(int value) {
        _bits = value;
    }

    public final boolean hasBits() {
        return _bits != 0;
    }

    public final boolean getBit(int index) {
        return Bits.get(_bits, index);
    }

    public final void setBit(int index) {
        _bits = Bits.set(_bits, index);
    }

    //

    @Override
    boolean validAgainst(VersionMap map, Snapshot snapshot, int start, int stop) {
        if (getBits() != 0) {
            for (int i = start; i < stop; i++) {
                TObject.Version write = TransactionBase.getVersion(snapshot.writes()[i], object());

                if (write != null) {
                    Version32 version = (Version32) write;

                    if (Bits.intersects(getBits(), version.getBits()))
                        return false;
                }
            }
        }

        return true;
    }

    @Override
    TObject.Version merge(TObject.Version target, TObject.Version next, boolean threadPrivate) {
        if (Debug.ENABLED)
            Debug.assertion(target == this);

        TIndexed32Read source = (TIndexed32Read) next;
        setBits(Bits.merge(getBits(), source.getBits()));
        return this;
    }

    @Override
    void deepCopy(Version source) {
        setBits(Bits.merge(getBits(), ((TIndexed32Read) source).getBits()));
    }

    @Override
    void visit(Visitor visitor) {
        visitor.visit(this);
    }

    protected static int setBit(int bits, int index) {
        return Bits.set(bits, index);
    }

    protected int getReadOnlys() {
        return 0;
    }

    @Override
    void mergeReadOnlyFields() {
        int bits = getBits();
        int ro = getReadOnlys();

        if (ro != 0) {
            int toDo = Bits.and(bits, ro);

            /*
             * Important: do not allow update of read-only fields. Users expect them not
             * to change and might store user name or other security related info. Read
             * volatile snapshot to sync with shared first.
             */
            TIndexed32Read shared = (TIndexed32Read) object().shared_();
            object().workspace().snapshot();
            int toAvoid = shared.getBits();
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

        return _bits != 0;
    }
}