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

import org.objectfabric.Counter.CounterRead;
import org.objectfabric.Counter.CounterSharedVersion;
import org.objectfabric.Counter.CounterVersion;
import org.objectfabric.Resource.ResourceRead;
import org.objectfabric.Resource.ResourceVersion;

@SuppressWarnings("rawtypes")
abstract class Dispatcher extends Extension {

    Dispatcher(Workspace workspace, boolean splitsSources) {
        super(workspace, splitsSources);
    }

    /*
     * Resource.
     */

    protected void onResourcePut(TObject object) {
    }

    protected void onResourceDelete(TObject object) {
    }

    @Override
    void visit(ResourceRead version) {
    }

    @Override
    void visit(ResourceVersion version) {
        if (version.getValue() != null)
            onResourcePut(version.object());
        else
            onResourceDelete(version.object());
    }

    /*
     * Indexed.
     */

    protected void onIndexedRead(TObject object, int index) {
    }

    protected void onIndexedWrite(TObject object, int index) {
    }

    @Override
    void visit(TIndexed32Read version) {
        if (version.getBits() != 0) {
            int index = 0;

            if (interrupted())
                index = resumeInt();

            TGenerated object = (TGenerated) version.object();

            if (visitingRead()) {
                for (; index < Integer.SIZE; index++) {
                    if (Bits.get(version.getBits(), index)) {
                        onIndexedRead(object, index);

                        if (interrupted()) {
                            interruptInt(index);
                            return;
                        }
                    }
                }
            } else {
                for (; index < object.getFieldCount(); index++) {
                    if (Bits.get(version.getBits(), index)) {
                        onIndexedWrite(object, index);

                        if (interrupted()) {
                            interruptInt(index);
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    void visit(TIndexedNRead version) {
        if (version.getBits() != null) {
            int index = 0, bit = 0;

            if (interrupted()) {
                index = resumeInt();
                bit = resumeInt();
            }

            TObject object = version.object();

            for (; index < version.getBits().length; index++) {
                if (version.getBits()[index] != null) {
                    int offset = version.getBits()[index].IntIndex << Bits.BITS_PER_UNIT_SHIFT;

                    for (; bit < Bits.BITS_PER_UNIT; bit++) {
                        if (Bits.get(version.getBits()[index].Value, bit)) {
                            int actualIndex = offset + bit;

                            if (visitingRead())
                                onIndexedRead(object, actualIndex);
                            else
                                onIndexedWrite(object, actualIndex);

                            if (interrupted()) {
                                interruptInt(bit);
                                interruptInt(index);
                                return;
                            }
                        }
                    }

                    bit = 0;
                }
            }
        }
    }

    /*
     * TKeyed.
     */

    protected void onKeyedRead(TObject object, Object key) {
    }

    protected void onKeyedFullRead(TObject object) {
    }

    @Override
    void visit(TKeyedRead version) {
        TKeyedEntry[] entries = version.getEntries();

        if (entries != null || version.getFullyRead()) {
            int index;

            if (interrupted())
                index = resumeInt();
            else
                index = -1;

            if (index < 0) {
                if (version.getFullyRead()) {
                    onKeyedFullRead(version.object());

                    if (interrupted()) {
                        interruptInt(index);
                        return;
                    }
                }

                index = 0;
            }

            if (entries != null) {
                for (; index < entries.length; index++) {
                    if (entries[index] != null && entries[index] != TKeyedEntry.REMOVED) {
                        onKeyedRead(version.object(), entries[index].getKey());

                        if (interrupted()) {
                            interruptInt(index);
                            return;
                        }
                    }
                }
            }
        }
    }

    protected void onKeyedPut(TObject object, Object key, Object value) {
    }

    protected void onKeyedRemoval(TObject object, Object key) {
    }

    protected void onKeyedClear(TObject object) {
    }

    @Override
    void visit(TKeyedVersion version) {
        TKeyedEntry[] entries = version.getEntries();

        if (entries != null || version.getCleared()) {
            int index;

            if (interrupted())
                index = resumeInt();
            else
                index = -1;

            if (index < 0) {
                if (version.getCleared()) {
                    onKeyedClear(version.object());

                    if (interrupted()) {
                        interruptInt(index);
                        return;
                    }
                }

                index = 0;
            }

            if (entries != null) {
                for (; index < entries.length; index++) {
                    if (entries[index] != null && entries[index] != TKeyedEntry.REMOVED) {
                        if (entries[index].isRemoval())
                            onKeyedRemoval(version.object(), entries[index].getKey());
                        else
                            onKeyedPut(version.object(), entries[index].getKey(), entries[index].getValue());

                        if (interrupted()) {
                            interruptInt(index);
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    void visit(TKeyedSharedVersion shared) {
        throw new IllegalStateException();
    }

    /*
     * TCounter.
     */

    protected void onCounterRead(TObject object) {
    }

    protected void onCounterAdded(TObject object, long delta) {
    }

    @Override
    void visit(CounterRead version) {
        onCounterRead(version.object());
    }

    @Override
    void visit(CounterVersion version) {
        onCounterAdded(version.object(), version.getDelta());
    }

    @Override
    void visit(CounterSharedVersion shared) {
        throw new IllegalStateException();
    }
}