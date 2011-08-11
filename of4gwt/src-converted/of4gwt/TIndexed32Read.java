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

class TIndexed32Read extends TObject.Version {

    private int _bits;

    public TIndexed32Read(TObject.Version shared, int length) {
        super(shared);

        if (Debug.ENABLED)
            Debug.assertion(length <= Integer.SIZE);
    }

    public final int getBits() {
        return _bits;
    }

    public final void setBits(int value) {
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

    public final void unsetBit(int index) {
        if (Debug.ENABLED)
            Debug.assertion(getBit(index));

        _bits = Bits.unset(_bits, index);
    }

    //

    @Override
    public boolean validAgainst(VersionMap map, Snapshot snapshot, int start, int stop) {
        if (getBits() != 0) {
            for (int i = start; i < stop; i++) {
                boolean skip = false;

                if (map.getSource() != null) {
                    Connection.Version c = snapshot.getVersionMaps()[i].getSource() != null ? snapshot.getVersionMaps()[i].getSource().Connection : null;
                    skip = map.getSource().Connection == c;
                }

                if (!skip) {
                    TObject.Version write = TransactionSets.getVersionFromSharedVersion(snapshot.getWrites()[i], getUnion());

                    if (write != null) {
                        TIndexed32Version version = (TIndexed32Version) write;

                        if (Bits.intersects(getBits(), version.getBits()))
                            return false;
                    }
                }
            }
        }

        return true;
    }

    @Override
    public TObject.Version merge(TObject.Version target, TObject.Version next, int flags) {
        if (Debug.ENABLED)
            Debug.assertion(target == this);

        TIndexed32Read source = (TIndexed32Read) next;
        setBits(Bits.merge(getBits(), source.getBits()));
        return this;
    }

    @Override
    public void visit(of4gwt.Visitor visitor) {
        visitor.visit(this);
    }

    public static final int setBit(int bits, int index) {
        return Bits.set(bits, index);
    }

    public int getReadOnlys() {
        return 0;
    }

    public int getTransients() {
        return 0;
    }

    @Override
    public boolean allModifiedFieldsAreReadOnly() {
        return Bits.andNot(getBits(), getReadOnlys()) == 0;
    }

    @Override
    public void mergeReadOnlyFields() {
        int bits = getBits();
        int ro = getReadOnlys();

        if (ro != 0) {
            int toDo = Bits.and(bits, ro);

            /*
             * Important: do not allow update of read-only fields. Users expect them not
             * to change and might store user name or other security related info. Read
             * volatile snapshot to sync with shared first.
             */
            TIndexed32Read shared = (TIndexed32Read) getUnionAsVersion();
            shared.getTrunk().getSharedSnapshot();
            int toAvoid = shared.getBits();
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

        return _bits != 0;
    }
}