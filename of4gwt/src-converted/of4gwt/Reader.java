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
import of4gwt.misc.Bits;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
public class Reader extends TObjectReader {

    protected Reader(List<UserTObject> newTObjects) {
        super(newTObjects);
    }

    public final Object readObject() {
        return UnknownObjectSerializer.read(this);
    }

    /*
     * Indexed 32.
     */

    @Override
    protected void visit(TIndexed32Read read) {
        if (interrupted())
            resume();

        if (!canReadInteger()) {
            interrupt(null);
            return;
        }

        read.setBits(readInteger());
    }

    @Override
    protected void visit(TIndexed32Version version) {
        int index = -1;

        if (interrupted())
            index = resumeInt();

        if (index < 0) {
            if (!canReadInteger()) {
                interruptInt(index);
                return;
            }

            version.setBits(readInteger());
            index = 0;
        }

        for (; index < version.length(); index++) {
            if (Bits.get(version.getBits(), index)) {
                version.readWrite(this, index);

                if (interrupted()) {
                    interruptInt(index);
                    return;
                }
            }
        }
    }

    /*
     * Indexed N.
     */

    @Override
    protected void visit(TIndexedNRead read) {
        readEntries(read);
    }

    @Override
    protected void visit(TIndexedNVersion version) {
        readEntries(version);
    }

    private enum EntryStep {
        INT_INDEX, BITS, VALUES
    }

    @SuppressWarnings({ "fallthrough", "null" })
    private final void readEntries(TIndexedNRead read) {
        for (;;) {
            EntryStep step = EntryStep.INT_INDEX;
            int intIndex = 0, bit = 0;
            Bits.Entry entry = null;

            if (interrupted()) {
                step = (EntryStep) resume();
                intIndex = resumeInt();
                entry = (Bits.Entry) resume();
                bit = resumeInt();
            }

            switch (step) {
                case INT_INDEX: {
                    if (!canReadInteger()) {
                        interruptInt(bit);
                        interrupt(entry);
                        interruptInt(intIndex);
                        interrupt(EntryStep.INT_INDEX);
                        return;
                    }

                    intIndex = readInteger();
                }
                case BITS: {
                    int actualIntIndex = intIndex >= 0 ? intIndex : -intIndex - 1;

                    if (!canReadInteger()) {
                        interruptInt(bit);
                        interrupt(entry);
                        interruptInt(intIndex);
                        interrupt(EntryStep.BITS);
                        return;
                    }

                    entry = PlatformAdapter.createBitsEntry(actualIntIndex, readInteger());
                    read.addEntry(entry);
                }
                case VALUES: {
                    if (read instanceof TIndexedNVersion) {
                        TIndexedNVersion version = (TIndexedNVersion) read;
                        int offset = entry.IntIndex << Bits.BITS_PER_UNIT_SHIFT;

                        for (; bit < Bits.BITS_PER_UNIT; bit++) {
                            if (Bits.get(entry.Value, bit)) {
                                version.readWrite(this, offset + bit);

                                if (interrupted()) {
                                    interruptInt(bit);
                                    interrupt(entry);
                                    interruptInt(intIndex);
                                    interrupt(EntryStep.VALUES);
                                    return;
                                }
                            }
                        }
                    } else if (Debug.ENABLED)
                        Debug.assertion(visitingReads());
                }
            }

            if (intIndex < 0)
                break;
        }
    }

    /*
     * Keyed.
     */

    @Override
    protected void visit(TKeyedRead read) {
        boolean fullyReadDone = false;

        if (interrupted())
            fullyReadDone = resumeBoolean();

        if (!fullyReadDone) {
            if (!canReadBoolean()) {
                interruptBoolean(false);
                return;
            }

            read.setFullyRead(readBoolean());

            if (read.getFullyRead())
                return;
        }

        for (;;) {
            Object key = readObject();

            if (interrupted()) {
                interruptBoolean(true);
                return;
            }

            if (key == null)
                break;

            @SuppressWarnings("unchecked")
            TKeyedEntry previous = read.putEntry(key, new TKeyedEntry(key, TKeyed.hash(key), null, false), true, true);

            if (Debug.ENABLED)
                Debug.assertion(previous == null);
        }
    }

    @Override
    protected void visit(TKeyedVersion version) {
        boolean clearHasBeenRead = false;
        boolean keyHasBeenRead = false;
        Object key = null;

        if (interrupted()) {
            clearHasBeenRead = resumeBoolean();
            keyHasBeenRead = resumeBoolean();
            key = resume();
        }

        if (!clearHasBeenRead) {
            if (!canReadBoolean()) {
                interrupt(null);
                interruptBoolean(false);
                interruptBoolean(false);
                return;
            }

            version.setCleared(readBoolean());

            if (!version.getCleared()) {
                // Usually set by adding elements, so force
                version.setVerifySizeDeltaOnCommit();
            }
        }

        for (;;) {
            if (!keyHasBeenRead) {
                key = readObject();

                if (interrupted()) {
                    interrupt(key);
                    interruptBoolean(false);
                    interruptBoolean(true);
                    return;
                }

                if (key == null)
                    break;
            }

            Object value;

            if (!visitingReads()) {
                value = readObject();

                if (interrupted()) {
                    interrupt(key);
                    interruptBoolean(true);
                    interruptBoolean(true);
                    return;
                }
            } else
                value = TKeyedEntry.READ;

            @SuppressWarnings("unchecked")
            TKeyedEntry previous = version.putEntry(key, new TKeyedEntry(key, TKeyed.hash(key), value, false), true, true);

            if (Debug.ENABLED)
                Debug.assertion(previous == null);

            keyHasBeenRead = false;
        }
    }

    @Override
    protected void visit(TKeyedSharedVersion version) {
        throw new IllegalStateException();
    }

    /*
     * List.
     */

    @Override
    protected void visit(TListRead read) {
    }

    @Override
    protected void visitRead(TListSharedVersion shared) {
    }

    private enum VisitListWrites {
        SIZE_OR_FLAGS, SHARED_VALUES, ENTRIES, REMOVALS, INSERTS
    }

    @SuppressWarnings("fallthrough")
    @Override
    protected void visit(TListVersion version) {
        VisitListWrites step = VisitListWrites.SIZE_OR_FLAGS;
        int size = 0;
        int index = 0;

        if (interrupted()) {
            step = (VisitListWrites) resume();
            size = resumeInt();
            index = resumeInt();
        }

        switch (step) {
            case SIZE_OR_FLAGS: {
                if (!canReadInteger()) {
                    interruptInt(index);
                    interruptInt(size);
                    interrupt(VisitListWrites.SIZE_OR_FLAGS);
                    return;
                }

                size = readInteger();
            }
            case SHARED_VALUES: {
                if (size >= 0) {
                    version.setCleared(true);

                    for (; index < size; index++) {
                        Object value = readObject();

                        if (interrupted()) {
                            interruptInt(index);
                            interruptInt(size);
                            interrupt(VisitListWrites.SHARED_VALUES);
                            return;
                        }

                        version.insert(index);
                        version.set(index, value);
                    }

                    // Reading a shared version, done
                    break;
                }

                if ((size & Writer.TLIST_VERSION_CLEARED) != 0)
                    version.setCleared(true);
                else if (Debug.ENABLED)
                    Debug.assertion(!version.getCleared());
            }
            case ENTRIES: {
                if ((size & Writer.TLIST_VERSION_ENTRIES) != 0) {
                    readEntries(version);

                    if (interrupted()) {
                        interruptInt(index);
                        interruptInt(size);
                        interrupt(VisitListWrites.ENTRIES);
                        return;
                    }
                } else if (Debug.ENABLED)
                    Debug.assertion(version.getBits() == null);
            }
            case REMOVALS: {
                if ((size & Writer.TLIST_VERSION_REMOVALS) != 0) {
                    for (;;) {
                        if (!canReadInteger()) {
                            interruptInt(index);
                            interruptInt(size);
                            interrupt(VisitListWrites.REMOVALS);
                            return;
                        }

                        int value = readInteger();

                        if (value < 0) {
                            version.writeRemoval(-value - 1);
                            break;
                        }

                        version.writeRemoval(value);
                    }
                } else if (Debug.ENABLED)
                    Debug.assertion(version.getRemovals() == null);
            }
            case INSERTS: {
                if ((size & Writer.TLIST_VERSION_INSERTS) != 0) {
                    for (;;) {
                        if (!canReadInteger()) {
                            interruptInt(index);
                            interruptInt(size);
                            interrupt(VisitListWrites.INSERTS);
                            return;
                        }

                        int value = readInteger();

                        if (value < 0) {
                            version.writeInsert(-value - 1);
                            break;
                        }

                        version.writeInsert(value);
                    }
                } else if (Debug.ENABLED)
                    Debug.assertion(version.getInserts() == null);
            }
        }

        if (Debug.ENABLED)
            version.checkInvariants();
    }

    @Override
    protected void visit(TListSharedVersion version) {
        throw new IllegalStateException();
    }
}
