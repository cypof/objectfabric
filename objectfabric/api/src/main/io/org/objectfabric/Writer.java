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
import org.objectfabric.TIndexed.VersionN;
import org.objectfabric.ThreadAssert.SingleThreaded;

@SuppressWarnings("rawtypes")
@SingleThreaded
public final class Writer extends TObjectWriter {

    /*
     * Values. First bit: is value a TObject.
     */

    static final int VALUE_IS_TOBJECT = 1 << 7;

    /*
     * Flags for type TObject.
     */

    static final int TOBJECT_CACHED = 1 << 6;

    static final int TOBJECT_RANGE_CHANGE = 1 << 5;

    static final int TOBJECT_RANGE_CACHED = 1 << 4;

    static final int TOBJECT_MODEL_CHANGE = 1 << 3;

    static final int TOBJECT_MODEL_CACHED = 1 << 2;

    static final int TOBJECT_GENERIC_ARGUMENTS = 1 << 1;

    // Remaining

    /*
     * Otherwise value is immutable, data is index.
     */

    static final int IMMUTABLE_MAX_COUNT = 1 << 7;

    /*
     * Commands.
     */

    static final byte COMMAND_ROOT_WRITE = 0;

    static final byte COMMAND_ROOT_READ = 1;

    static final byte COMMAND_WRITE = 2;

    // TODO
    static final byte COMMAND_READ = 3;

    static final byte COMMAND_DEPENDENCY = 4;

    static final byte COMMAND_HAPPENED_BEFORE = 5;

    static final byte COMMAND_TICK = 6;

    // Debug

    static final int DEBUG_TAG_CODE = 1111111111;

    static final int DEBUG_TAG_CONNECTION = 222222222;

    //

    Writer(Watcher watcher) {
        super(watcher, watcher.getInterruptionStack());

        if (Debug.ENABLED) {
            // Assert enough room for all immutable types
            Debug.assertion(Immutable.ALL.size() <= Writer.IMMUTABLE_MAX_COUNT);
        }
    }

    public final void writeObject(Object value) {
        UnknownObjectSerializer.write(this, value);
    }

    final void writeCommand(byte code) {
        if (Debug.ENABLED)
            Debug.assertion((code & 0xff) == code);

        if (interrupted())
            resume();

        if (!canWriteByte()) {
            interrupt(null);
            return;
        }

        writeByte(code, DEBUG_TAG_CODE);
    }

    private enum HappenedBeforeStep {
        COMMAND, PEER, TICK
    }

    @SuppressWarnings("fallthrough")
    final void writePeerTick(byte command, long tick) {
        HappenedBeforeStep step = HappenedBeforeStep.COMMAND;

        if (interrupted())
            step = (HappenedBeforeStep) resume();

        switch (step) {
            case COMMAND: {
                writeCommand(command);

                if (interrupted()) {
                    interrupt(HappenedBeforeStep.COMMAND);
                    return;
                }
            }
            case PEER: {
                // TODO cache peers like models and ranges
                writeBinary(Peer.get(Tick.peer(tick)).uid());

                if (interrupted()) {
                    interrupt(HappenedBeforeStep.PEER);
                    return;
                }
            }
            case TICK: {
                if (!canWriteLong()) {
                    interrupt(HappenedBeforeStep.TICK);
                    return;
                }

                writeLong(Tick.time(tick));
            }
        }
    }

    private final byte command() {
        return watcher().visitingRead() ? COMMAND_READ : COMMAND_WRITE;
    }

    /*
     * Root.
     */

    final void writeRootRead() {
        writeCommand(COMMAND_ROOT_READ);
    }

    final void writeRootVersion(Object value) {
        boolean wroteCommand = false;

        if (interrupted())
            wroteCommand = resumeBoolean();

        if (!wroteCommand) {
            writeCommand(COMMAND_ROOT_WRITE);

            if (interrupted()) {
                interruptBoolean(false);
                return;
            }
        }

        writeObject(value);

        if (interrupted()) {
            interruptBoolean(true);
            return;
        }
    }

    /*
     * Indexed 32.
     */

    private enum TIndexedStep {
        COMMAND, TOBJECT, BITS, VALUES
    }

    @SuppressWarnings("fallthrough")
    final void write(TIndexed32Read version) {
        if (version.getBits() == 0)
            return;

        TIndexedStep step = TIndexedStep.COMMAND;
        int index = 0;

        if (interrupted()) {
            step = (TIndexedStep) resume();
            index = resumeInt();
        }

        switch (step) {
            case COMMAND: {
                writeCommand(command());

                if (interrupted()) {
                    interruptInt(index);
                    interrupt(TIndexedStep.COMMAND);
                    return;
                }
            }
            case TOBJECT: {
                writeTObject(version.object());

                if (interrupted()) {
                    interruptInt(index);
                    interrupt(TIndexedStep.TOBJECT);
                    return;
                }
            }
            case BITS: {
                if (!canWriteInteger()) {
                    interruptInt(index);
                    interrupt(TIndexedStep.BITS);
                    return;
                }

                writeInteger(version.getBits());
            }
            case VALUES: {
                if (!watcher().visitingRead()) {
                    Version32 v32 = (Version32) version;
                    TGenerated generated = (TGenerated) version.object();

                    for (; index < generated.getFieldCount(); index++) {
                        if (Bits.get(version.getBits(), index)) {
                            v32.writeWrite(this, index);

                            if (interrupted()) {
                                interruptInt(index);
                                interrupt(TIndexedStep.VALUES);
                                return;
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

    @SuppressWarnings("fallthrough")
    final void write(TIndexedNRead version) {
        if (version.getBits() == null)
            return;

        TIndexedNStep step = TIndexedNStep.COMMAND;

        if (interrupted())
            step = (TIndexedNStep) resume();

        switch (step) {
            case COMMAND: {
                writeCommand(command());

                if (interrupted()) {
                    interrupt(TIndexedNStep.COMMAND);
                    return;
                }
            }
            case TOBJECT: {
                writeTObject(version.object());

                if (interrupted()) {
                    interrupt(TIndexedNStep.TOBJECT);
                    return;
                }
            }
            case ENTRIES: {
                writeEntries(version);

                if (interrupted()) {
                    interrupt(TIndexedNStep.ENTRIES);
                    return;
                }
            }
        }
    }

    private final void writeEntries(TIndexedNRead version) {
        if (Debug.ENABLED)
            Debug.assertion(!Bits.isEmpty(version.getBits()));

        int index = 0;

        if (interrupted())
            index = resumeInt();
        else
            index = next(version.getBits(), 0);

        for (;;) {
            int next = next(version.getBits(), index + 1);

            if (version.getBits()[index] != null) {
                writeEntry(version.getBits()[index], version, next == version.getBits().length);

                if (interrupted()) {
                    interruptInt(index);
                    return;
                }
            }

            if (next == version.getBits().length)
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

    @SuppressWarnings("fallthrough")
    private final void writeEntry(Bits.Entry entry, TIndexedNRead read, boolean isLast) {
        EntryStep step = EntryStep.INT_INDEX;
        int index = 0;
        VersionN toWrite = null;

        if (interrupted()) {
            step = (EntryStep) resume();
            index = resumeInt();
            toWrite = (VersionN) resume();
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
                if (!watcher().visitingRead()) {
                    int offset = entry.IntIndex << Bits.BITS_PER_UNIT_SHIFT;

                    for (; index < Bits.BITS_PER_UNIT; index++) {
                        if (Bits.get(entry.Value, index)) {
                            ((VersionN) read).writeWrite(this, offset + index);

                            if (interrupted()) {
                                interrupt(toWrite);
                                interruptInt(index);
                                interrupt(EntryStep.VALUES);
                                return;
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

    @SuppressWarnings("fallthrough")
    final void writeTKeyed(TObject object, TKeyedEntry[] entries, boolean cleared, boolean fullyRead) {
        if (entries == null && !cleared && !fullyRead)
            return;

        KeyedStep step = KeyedStep.COMMAND;
        int index = 0;
        boolean wroteKey = false;

        if (interrupted()) {
            step = (KeyedStep) resume();
            index = resumeInt();
            wroteKey = resumeBoolean();
        }

        switch (step) {
            case COMMAND: {
                writeCommand(command());

                if (interrupted()) {
                    interruptBoolean(wroteKey);
                    interruptInt(index);
                    interrupt(KeyedStep.COMMAND);
                    return;
                }
            }
            case TOBJECT: {
                writeTObject(object);

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

                if (watcher().visitingRead()) {
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
                                writeObject(entries[index].getKey());

                                if (interrupted()) {
                                    interruptBoolean(false);
                                    interruptInt(index);
                                    interrupt(KeyedStep.ENTRIES);
                                    return;
                                }
                            }

                            if (!watcher().visitingRead()) {
                                writeObject(entries[index].getValueOrRemoval());

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
     * Counter.
     */

    private enum CounterStep {
        COMMAND, TOBJECT, DELTA, RESET
    }

    @SuppressWarnings("fallthrough")
    final void writeCounter(TObject object, boolean write, long delta, boolean reset) {
        if (write && delta == 0 && !reset)
            return;

        CounterStep step = CounterStep.COMMAND;

        if (interrupted())
            step = (CounterStep) resume();

        switch (step) {
            case COMMAND: {
                writeCommand(command());

                if (interrupted()) {
                    interrupt(CounterStep.COMMAND);
                    return;
                }
            }
            case TOBJECT: {
                writeTObject(object);

                if (interrupted()) {
                    interrupt(CounterStep.TOBJECT);
                    return;
                }

                if (!write)
                    return;
            }
            case DELTA: {
                if (!canWriteLong()) {
                    interrupt(CounterStep.DELTA);
                    return;
                }

                writeLong(delta);
            }
            case RESET: {
                if (!canWriteBoolean()) {
                    interrupt(CounterStep.RESET);
                    return;
                }

                writeBoolean(reset);
            }
        }
    }

    // Debug

    public static String getCommandString(int code) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        switch (code) {
            case COMMAND_ROOT_WRITE:
                return "ROOT_WRITE";
            case COMMAND_ROOT_READ:
                return "ROOT_READ";
            case COMMAND_WRITE:
                return "WRITE";
            case COMMAND_READ:
                return "READ";
            case COMMAND_DEPENDENCY:
                return "DEPENDENCY";
            case COMMAND_HAPPENED_BEFORE:
                return "HAPPENED_BEFORE";
            case COMMAND_TICK:
                return "TICK";
            default:
                throw new IllegalStateException();
        }
    }
}
