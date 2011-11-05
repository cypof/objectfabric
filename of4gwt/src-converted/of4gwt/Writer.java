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
import of4gwt.misc.Bits;
import of4gwt.misc.Debug;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SuppressWarnings({ "fallthrough", "null" })
@SingleThreaded
public abstract class Writer extends TObjectWriter {

    public static final byte NULL_COMMAND = -1;

    private byte _nextCommand = NULL_COMMAND;

    protected Writer(boolean allowResets) {
        super(allowResets);
    }

    final byte getNextCommand() {
        return _nextCommand;
    }

    final void setNextCommand(byte value) {
        _nextCommand = value;
    }

    public abstract void writeCommand(byte command);

    public final void writeObject(Object value) {
        UnknownObjectSerializer.write(this, value, -1);
    }

    /*
     * Indexed 32.
     */

    private enum TIndexedStep {
        COMMAND, TOBJECT, BITS, VALUES
    }

    @Override
    protected void visit(TIndexed32Read read, int bits) {
        if (Debug.ENABLED)
            Debug.assertion(bits != 0);

        TIndexedStep step;
        int index = 0;
        TIndexed32Version toWrite = null;

        if (interrupted()) {
            step = (TIndexedStep) resume();
            index = resumeInt();
            toWrite = (TIndexed32Version) resume();
        } else
            step = _nextCommand != NULL_COMMAND ? TIndexedStep.COMMAND : TIndexedStep.BITS;

        switch (step) {
            case COMMAND: {
                writeCommand(_nextCommand);

                if (interrupted()) {
                    interrupt(toWrite);
                    interruptInt(index);
                    interrupt(TIndexedStep.COMMAND);
                    return;
                }
            }
            case TOBJECT: {
                writeTObject(read.getShared());

                if (interrupted()) {
                    interrupt(toWrite);
                    interruptInt(index);
                    interrupt(TIndexedStep.TOBJECT);
                    return;
                }
            }
            case BITS: {
                if (!canWriteInteger()) {
                    interrupt(toWrite);
                    interruptInt(index);
                    interrupt(TIndexedStep.BITS);
                    return;
                }

                writeInteger(bits);
            }
            case VALUES: {
                if (!visitingReads()) {
                    TIndexed32Version version = (TIndexed32Version) read;

                    for (; index < version.length(); index++) {
                        if (Bits.get(bits, index)) {
                            if (!visitingGatheredVersions()) {
                                version.writeWrite(this, index);

                                if (interrupted()) {
                                    interrupt(toWrite);
                                    interruptInt(index);
                                    interrupt(TIndexedStep.VALUES);
                                    return;
                                }
                            } else {
                                if (toWrite == null) {
                                    for (int i = getMapIndexCount() - 1; i >= 0; i--) {
                                        toWrite = (TIndexed32Version) getGatheredVersion(version, i);

                                        if (toWrite != null && toWrite.getBit(index))
                                            break;
                                    }
                                }

                                toWrite.writeWrite(this, index);

                                if (interrupted()) {
                                    interrupt(toWrite);
                                    interruptInt(index);
                                    interrupt(TIndexedStep.VALUES);
                                    return;
                                }

                                toWrite = null;
                            }
                        }
                    }
                }
            }
        }
    }

    /*
     * Indexed N.
     */

    private enum TIndexedNStep {
        COMMAND, TOBJECT, ENTRIES
    }

    @Override
    protected void visit(TIndexedNRead read, Bits.Entry[] bits) {
        TIndexedNStep step;

        if (interrupted())
            step = (TIndexedNStep) resume();
        else
            step = _nextCommand != NULL_COMMAND ? TIndexedNStep.COMMAND : TIndexedNStep.ENTRIES;

        switch (step) {
            case COMMAND: {
                writeCommand(_nextCommand);

                if (interrupted()) {
                    interrupt(TIndexedNStep.COMMAND);
                    return;
                }
            }
            case TOBJECT: {
                writeTObject(read.getShared());

                if (interrupted()) {
                    interrupt(TIndexedNStep.TOBJECT);
                    return;
                }
            }
            case ENTRIES: {
                writeEntries(bits, read, false);

                if (interrupted()) {
                    interrupt(TIndexedNStep.ENTRIES);
                    return;
                }
            }
        }
    }

    private final void writeEntries(Bits.Entry[] bits, TIndexedNRead read, boolean forceUseOfVersionValues) {
        if (Debug.ENABLED)
            Debug.assertion(!Bits.isEmpty(bits));

        int index = 0;

        if (interrupted())
            index = resumeInt();
        else
            index = next(bits, 0);

        for (;;) {
            int next = next(bits, index + 1);

            if (bits[index] != null) {
                writeEntry(bits[index], read, next == bits.length, forceUseOfVersionValues);

                if (interrupted()) {
                    interruptInt(index);
                    return;
                }
            }

            if (next == bits.length)
                break;

            index = next;
        }
    }

    private static int next(Bits.Entry[] bits, int index) {
        for (int i = index; i < bits.length; i++)
            if (bits[i] != null)
                return i;

        return bits.length;
    }

    private enum EntryStep {
        INT_INDEX, BITS, VALUES
    }

    private final void writeEntry(Bits.Entry entry, TIndexedNRead read, boolean isLast, boolean forceUseOfVersionValues) {
        EntryStep step = EntryStep.INT_INDEX;
        int index = 0;
        TIndexedNVersion toWrite = null;

        if (interrupted()) {
            step = (EntryStep) resume();
            index = resumeInt();
            toWrite = (TIndexedNVersion) resume();
        }

        switch (step) {
            case INT_INDEX: {
                if (!canWriteInteger()) {
                    interrupt(toWrite);
                    interruptInt(index);
                    interrupt(EntryStep.INT_INDEX);
                    return;
                }

                writeInteger(isLast ? -entry.IntIndex - 1 : entry.IntIndex);
            }
            case BITS: {
                if (!canWriteInteger()) {
                    interrupt(toWrite);
                    interruptInt(index);
                    interrupt(EntryStep.BITS);
                    return;
                }

                writeInteger(entry.Value);
            }
            case VALUES: {
                if (!visitingReads()) {
                    int offset = entry.IntIndex << Bits.BITS_PER_UNIT_SHIFT;

                    for (; index < Bits.BITS_PER_UNIT; index++) {
                        if (Bits.get(entry.Value, index)) {
                            int actualIndex = offset + index;

                            if (forceUseOfVersionValues || !visitingGatheredVersions()) {
                                ((TIndexedNVersion) read).writeWrite(this, actualIndex);

                                if (interrupted()) {
                                    interrupt(toWrite);
                                    interruptInt(index);
                                    interrupt(EntryStep.VALUES);
                                    return;
                                }
                            } else {
                                if (toWrite == null) {
                                    for (int i = getMapIndexCount() - 1; i >= 0; i--) {
                                        Version base = getGatheredVersion(read.getShared(), i);

                                        if (base != null && ((TIndexedNVersion) base).getBit(actualIndex)) {
                                            toWrite = (TIndexedNVersion) base;
                                            break;
                                        }
                                    }
                                }

                                toWrite.writeWrite(this, actualIndex);

                                if (interrupted()) {
                                    interrupt(toWrite);
                                    interruptInt(index);
                                    interrupt(EntryStep.VALUES);
                                    return;
                                }

                                toWrite = null;
                            }
                        }
                    }
                }
            }
        }
    }

    /*
     * Keyed.
     */

    private enum KeyedStep {
        COMMAND, TOBJECT, BOOLEAN, ENTRIES, END
    }

    @Override
    protected void visit(TKeyedSharedVersion shared, TKeyedEntry[] entries, boolean cleared, boolean fullyRead) {
        KeyedStep step;
        int index = 0;
        boolean wroteKey = false;

        if (interrupted()) {
            step = (KeyedStep) resume();
            index = resumeInt();
            wroteKey = resumeBoolean();
        } else
            step = _nextCommand != NULL_COMMAND ? KeyedStep.COMMAND : KeyedStep.BOOLEAN;

        switch (step) {
            case COMMAND: {
                writeCommand(_nextCommand);

                if (interrupted()) {
                    interruptBoolean(wroteKey);
                    interruptInt(index);
                    interrupt(KeyedStep.COMMAND);
                    return;
                }
            }
            case TOBJECT: {
                writeTObject(shared);

                if (interrupted()) {
                    interruptBoolean(wroteKey);
                    interruptInt(index);
                    interrupt(KeyedStep.TOBJECT);
                    return;
                }
            }
            case BOOLEAN: {
                if (!canWriteBoolean()) {
                    interruptBoolean(wroteKey);
                    interruptInt(index);
                    interrupt(KeyedStep.BOOLEAN);
                    return;
                }

                if (visitingReads()) {
                    writeBoolean(fullyRead);

                    if (Debug.ENABLED)
                        Debug.assertion(!cleared);

                    if (fullyRead)
                        return;
                } else {
                    writeBoolean(cleared);

                    if (Debug.ENABLED)
                        Debug.assertion(!fullyRead);
                }
            }
            case ENTRIES: {
                if (entries != null) {
                    for (; index < entries.length; index++) {
                        if (entries[index] != null && entries[index] != TKeyedEntry.REMOVED) {
                            if (!wroteKey) {
                                writeObject(entries[index].getKeyDirect());

                                if (interrupted()) {
                                    interruptBoolean(false);
                                    interruptInt(index);
                                    interrupt(KeyedStep.ENTRIES);
                                    return;
                                }
                            }

                            if (!visitingReads()) {
                                writeObject(entries[index].getValueDirect());

                                if (interrupted()) {
                                    interruptBoolean(true);
                                    interruptInt(index);
                                    interrupt(KeyedStep.ENTRIES);
                                    return;
                                }
                            }

                            wroteKey = false;
                        }
                    }
                }
            }
            case END: {
                writeObject(null);

                if (interrupted()) {
                    interruptBoolean(wroteKey);
                    interruptInt(index);
                    interrupt(KeyedStep.END);
                    return;
                }
            }
        }
    }

    /*
     * List.
     */

    private enum ListReadStep {
        COMMAND, TOBJECT
    }

    @Override
    protected void visit(TListRead read) {
        ListReadStep step = ListReadStep.COMMAND;

        if (interrupted())
            step = (ListReadStep) resume();

        switch (step) {
            case COMMAND: {
                writeCommand(_nextCommand);

                if (interrupted()) {
                    interrupt(ListReadStep.COMMAND);
                    return;
                }
            }
            case TOBJECT: {
                writeTObject(read.getShared());

                if (interrupted()) {
                    interrupt(ListReadStep.TOBJECT);
                    return;
                }
            }
        }
    }

    private enum ListSharedStep {
        COMMAND, TOBJECT, SIZE_OR_FLAGS, VALUES
    }

    @Override
    protected void visitSnapshot(TListSharedVersion shared, Object[] array, int size) {
        if (Debug.ENABLED)
            Debug.assertion(size > 0);

        ListSharedStep step;
        int index = 0;

        if (interrupted()) {
            step = (ListSharedStep) resume();
            index = resumeInt();
        } else
            step = _nextCommand != NULL_COMMAND ? ListSharedStep.COMMAND : ListSharedStep.SIZE_OR_FLAGS;

        switch (step) {
            case COMMAND: {
                writeCommand(_nextCommand);

                if (interrupted()) {
                    interruptInt(index);
                    interrupt(ListSharedStep.COMMAND);
                    return;
                }
            }
            case TOBJECT: {
                writeTObject(shared);

                if (interrupted()) {
                    interruptInt(index);
                    interrupt(ListSharedStep.TOBJECT);
                    return;
                }
            }
            case SIZE_OR_FLAGS: {
                if (!canWriteInteger()) {
                    interruptInt(index);
                    interrupt(ListSharedStep.SIZE_OR_FLAGS);
                    return;
                }

                writeInteger(size);
            }
            case VALUES: {
                for (; index < size; index++) {
                    writeObject(array[index]);

                    if (interrupted()) {
                        interruptInt(index);
                        interrupt(ListSharedStep.VALUES);
                        return;
                    }
                }
            }
        }
    }

    static final int TLIST_VERSION_CLEARED = 1 << 0;

    static final int TLIST_VERSION_ENTRIES = 1 << 1;

    static final int TLIST_VERSION_REMOVALS = 1 << 2;

    static final int TLIST_VERSION_INSERTS = 1 << 3;

    private enum ListStep {
        COMMAND, TOBJECT, SIZE_OR_FLAGS, ENTRIES, REMOVALS, INSERTS
    }

    @Override
    protected void visit(TListVersion version) {
        if (Debug.ENABLED) {
            boolean ok = false;

            if (version.getBits() != null && !Bits.isEmpty(version.getBits()))
                ok = true;

            if (version.getRemovalsCount() != 0 || version.getInsertsCount() != 0 || version.getCleared())
                ok = true;

            Debug.assertion(ok);
        }

        ListStep step;
        int index;

        if (interrupted()) {
            step = (ListStep) resume();
            index = resumeInt();
        } else {
            step = _nextCommand != NULL_COMMAND ? ListStep.COMMAND : ListStep.SIZE_OR_FLAGS;
            index = 0;
        }

        boolean hasBits = !Bits.isEmpty(version.getBits());

        switch (step) {
            case COMMAND: {
                writeCommand(_nextCommand);

                if (interrupted()) {
                    interruptInt(index);
                    interrupt(ListStep.COMMAND);
                    return;
                }
            }
            case TOBJECT: {
                writeTObject(version.getShared());

                if (interrupted()) {
                    interruptInt(index);
                    interrupt(ListStep.TOBJECT);
                    return;
                }
            }
            case SIZE_OR_FLAGS: {
                if (!canWriteInteger()) {
                    interruptInt(index);
                    interrupt(ListStep.SIZE_OR_FLAGS);
                    return;
                }

                int flags = Integer.MIN_VALUE;

                if (version.getCleared())
                    flags |= TLIST_VERSION_CLEARED;

                if (hasBits)
                    flags |= TLIST_VERSION_ENTRIES;

                if (version.getRemovalsCount() != 0)
                    flags |= TLIST_VERSION_REMOVALS;

                if (version.getInsertsCount() != 0)
                    flags |= TLIST_VERSION_INSERTS;

                writeInteger(flags);
            }
            case ENTRIES: {
                if (hasBits) {
                    writeEntries(version.getBits(), version, true);

                    if (interrupted()) {
                        interruptInt(index);
                        interrupt(ListStep.ENTRIES);
                        return;
                    }
                }
            }
            case REMOVALS: {
                if (version.getRemovalsCount() != 0) {
                    for (; index < version.getRemovalsCount(); index++) {
                        if (!canWriteInteger()) {
                            interruptInt(index);
                            interrupt(ListStep.REMOVALS);
                            return;
                        }

                        int value = version.getRemovals()[index];

                        if (Debug.ENABLED)
                            Debug.assertion(value >= 0);

                        writeInteger(index < version.getRemovalsCount() - 1 ? value : -value - 1);
                    }

                    index = 0;
                }
            }
            case INSERTS: {
                if (version.getInsertsCount() != 0) {
                    for (; index < version.getInsertsCount(); index++) {
                        if (!canWriteInteger()) {
                            interruptInt(index);
                            interrupt(ListStep.INSERTS);
                            return;
                        }

                        int value = version.getInserts()[index];

                        if (Debug.ENABLED)
                            Debug.assertion(value >= 0);

                        writeInteger(index < version.getInsertsCount() - 1 ? value : -value - 1);
                    }
                }
            }
        }
    }
}
