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
import org.objectfabric.Resource.Block;
import org.objectfabric.Resource.ResourceRead;
import org.objectfabric.Resource.ResourceVersion;
import org.objectfabric.TIndexed.Version32;
import org.objectfabric.TIndexed.VersionN;
import org.objectfabric.TObject.Version;

@SuppressWarnings({ "rawtypes", "unchecked" })
public final class Reader extends TObjectReader {

    private final ReadVisitor _visitor;

    private Version[][] _writes;

    private long[] _happenedBefore;

    private long[] _dependencies;

    private Version[] _versions;

    // Add new fields to clean

    Reader() {
        super(new List<Object>());

        _visitor = new ReadVisitor(getInterruptionStack());
    }

    @Override
    void clean() {
        super.clean();

        if (_writes != null)
            for (int i = 0; i < _writes.length; i++)
                _writes[i] = null;

        _happenedBefore = null;
        _dependencies = null;
        _versions = null;
    }

    public final Object readObject() {
        return UnknownObjectSerializer.read(this);
    }

    @Override
    final void startRead() {
        super.startRead();

        if (_writes == null || _writes.length < resources().size())
            _writes = new Version[resources().size()][];
    }

    @SuppressWarnings("fallthrough")
    final void read(long tick) {
        for (;;) {
            byte code = -1;

            if (interrupted())
                code = resumeByte();

            if (code < 0) {
                if (!canReadByte()) {
                    interruptByte(code);
                    return;
                }

                code = readByte(Writer.DEBUG_TAG_CODE);

                if (Debug.COMMUNICATIONS_LOG)
                    log("Command: " + Writer.getCommandString(code));
            }

            switch (code) {
                case Writer.COMMAND_ROOT_WRITE: {
                    readRootVersion();

                    if (interrupted()) {
                        interruptByte(code);
                        return;
                    }

                    break;
                }
                case Writer.COMMAND_ROOT_READ: {
                    // TODO
                    break;
                }
                case Writer.COMMAND_WRITE: {
                    if (_versions == null) {
                        TObject[] objects = readTObject();

                        if (interrupted()) {
                            interruptByte(code);
                            return;
                        }

                        _versions = new Version[objects.length];

                        for (int i = 0; i < objects.length; i++)
                            _versions[i] = objects[i].createVersion_();
                    }

                    _versions[0].visit(_visitor);

                    if (interrupted()) {
                        interruptByte(code);
                        return;
                    }

                    for (int i = 0; i < _versions.length; i++)
                        addWrite(i, _versions[i]);

                    _versions = null;
                    break;
                }
                case Writer.COMMAND_READ: {
                    // TODO
                    break;
                }
                case Writer.COMMAND_DEPENDENCY: {
                    long dependency = readTick();

                    if (interrupted()) {
                        interruptByte(code);
                        return;
                    }

                    _dependencies = Tick.add(_dependencies, dependency);
                    break;
                }
                case Writer.COMMAND_HAPPENED_BEFORE: {
                    long happenedBefore = readTick();

                    if (interrupted()) {
                        interruptByte(code);
                        return;
                    }

                    if (Debug.ENABLED)
                        Debug.assertion(Tick.peer(happenedBefore) != Tick.peer(tick));

                    _happenedBefore = Tick.putMax(_happenedBefore, happenedBefore, false);
                    break;
                }
                case Writer.COMMAND_TICK: {
                    for (int i = resources().size() - 1; i >= 0; i--) {
                        Version[] writes = _writes[i];
                        _writes[i] = null;

                        for (int j = writes.length - 1; j >= 0; j--)
                            if (writes[j] != null)
                                writes[j].mergeReadOnlyFields();

                        resources().get(i).onBlock(new Block(tick, writes, _happenedBefore, _dependencies));
                    }

                    _happenedBefore = null;
                    _dependencies = null;
                    // TODO add a checksum at end of each block
                    return;
                }
                default:
                    throw new IllegalStateException();
            }
        }
    }

    private final long readTick() {
        byte[] uid = null;

        if (interrupted())
            uid = (byte[]) resume();

        if (uid == null) {
            uid = readBinary();

            if (interrupted()) {
                interrupt(null);
                return 0;
            }
        }

        if (!canReadLong()) {
            interrupt(uid);
            return 0;
        }

        Peer peer = Peer.get(new UID(uid));
        long tick = readLong();
        return Tick.get(peer.index(), tick);
    }

    private final void addWrite(int index, Version version) {
        if (_writes[index] == null)
            _writes[index] = new Version[OpenMap.CAPACITY];

        _writes[index] = TransactionBase.putVersion(_writes[index], version);
    }

    /*
     * Root.
     */

    private final void readRootVersion() {
        if (interrupted())
            resume();

        Object root = readObject();

        if (interrupted()) {
            interrupt(null);
            return;
        }

        for (int i = 0; i < resources().size(); i++) {
            Resource resource = resources().get(i);
            ResourceVersion version = (ResourceVersion) resource.createVersion_();
            Object value;
            TObject[] refs = null;

            if (root == null)
                value = Resource.NULL;
            else if (root instanceof TObject[]) {
                refs = (TObject[]) root;
                value = refs[i];
            } else
                value = root;

            version.setValue(value);
            addWrite(i, version);
        }
    }

    /*
     * Indexed 32.
     */

    final void readTIndexed32() {
        int index = -1;

        if (interrupted())
            index = resumeInt();

        if (index < 0) {
            if (!canReadInteger()) {
                interruptInt(index);
                return;
            }

            int bits = readInteger();

            for (int i = 0; i < _versions.length; i++)
                ((TIndexed32Read) _versions[i]).setBits(bits);

            index = 0;
        }

        if (_versions[0] instanceof Version32) {
            Version32 version32 = (Version32) _versions[0];
            TGenerated generated = (TGenerated) version32.object();

            for (; index < generated.getFieldCount(); index++) {
                if (Bits.get(version32.getBits(), index)) {
                    version32.readWrite(this, index, _versions);

                    if (interrupted()) {
                        interruptInt(index);
                        return;
                    }
                }
            }
        }
    }

    /*
     * Indexed N.
     */

    private enum EntryStep {
        INT_INDEX, BITS, VALUES
    }

    @SuppressWarnings({ "fallthrough", "null" })
    final void readTIndexedN() {
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

                    int bits = readInteger();

                    for (int i = 0; i < _versions.length; i++) {
                        entry = new Bits.Entry(actualIntIndex, bits);
                        ((TIndexedNRead) _versions[i]).addEntry(entry);
                    }
                }
                case VALUES: {
                    if (_versions[0] instanceof VersionN) {
                        VersionN versionN = (VersionN) _versions[0];
                        int offset = entry.IntIndex << Bits.BITS_PER_UNIT_SHIFT;

                        for (; bit < Bits.BITS_PER_UNIT; bit++) {
                            if (Bits.get(entry.Value, bit)) {
                                versionN.readWrite(this, offset + bit, _versions);

                                if (interrupted()) {
                                    interruptInt(bit);
                                    interrupt(entry);
                                    interruptInt(intIndex);
                                    interrupt(EntryStep.VALUES);
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            if (intIndex < 0)
                break;
        }
    }

    /*
     * Keyed.
     */

    final void readTKeyedRead() {
        boolean fullyReadDone = false;

        if (interrupted())
            fullyReadDone = resumeBoolean();

        if (!fullyReadDone) {
            if (!canReadBoolean()) {
                interruptBoolean(false);
                return;
            }

            boolean fullyRead = readBoolean();

            for (int i = 0; i < _versions.length; i++)
                ((TKeyedRead) _versions[i]).setFullyRead(fullyRead);

            if (fullyRead)
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

            if (key instanceof TObject[]) {
                TObject[] objects = (TObject[]) key;

                for (int i = 0; i < _versions.length; i++) {
                    TKeyedEntry entry = new TKeyedEntry(objects[i], TKeyed.hash(objects[i]), null);
                    ((TKeyedRead) _versions[i]).putEntry(entry, true, true, true);
                }
            } else {
                for (int i = 0; i < _versions.length; i++) {
                    TKeyedEntry entry = new TKeyedEntry(key, TKeyed.hash(key), null);
                    ((TKeyedRead) _versions[i]).putEntry(entry, true, true, true);
                }
            }
        }
    }

    final void readTKeyedVersion() {
        boolean cleared = visitTKeyed();

        if (!interrupted()) {
            for (int i = 0; i < _versions.length; i++) {
                TKeyedVersion version = (TKeyedVersion) _versions[i];
                version.setCleared(cleared);

                if (!cleared) {
                    // Usually set by adding elements, so force
                    version.setVerifySizeDeltaOnCommit();
                }
            }
        }
    }

    private final boolean visitTKeyed() {
        boolean clearHasBeenRead = false;
        boolean keyHasBeenRead = false;
        boolean cleared = false;
        Object key = null;

        if (interrupted()) {
            clearHasBeenRead = resumeBoolean();
            keyHasBeenRead = resumeBoolean();
            cleared = resumeBoolean();
            key = resume();
        }

        if (!clearHasBeenRead) {
            if (!canReadBoolean()) {
                interrupt(null);
                interruptBoolean(cleared);
                interruptBoolean(false);
                interruptBoolean(false);
                return false;
            }

            cleared = readBoolean();
        }

        for (;;) {
            if (!keyHasBeenRead) {
                key = readObject();

                if (interrupted()) {
                    interrupt(key);
                    interruptBoolean(cleared);
                    interruptBoolean(false);
                    interruptBoolean(true);
                    return false;
                }

                if (key == null)
                    break;
            }

            Object value = readObject();

            if (interrupted()) {
                interrupt(key);
                interruptBoolean(cleared);
                interruptBoolean(true);
                interruptBoolean(true);
                return false;
            }

            for (int i = 0; i < _versions.length; i++) {
                Object key_, value_;

                if (key instanceof TObject[])
                    key_ = ((TObject[]) key)[i];
                else
                    key_ = key;

                if (value instanceof TObject[])
                    value_ = ((TObject[]) value)[i];
                else
                    value_ = value;

                TKeyedEntry entry = new TKeyedEntry(key_, TKeyed.hash(key_), value_);
                ((TKeyedBase2) _versions[i]).putEntry(entry, true, false, true);
            }

            keyHasBeenRead = false;
        }

        return cleared;
    }

    /*
     * Counter.
     */

    final void readCounter() {
        boolean deltaDone = false;
        long delta = 0;

        if (interrupted()) {
            deltaDone = resumeBoolean();
            delta = resumeLong();
        }

        if (!deltaDone) {
            if (!canReadLong()) {
                interruptLong(delta);
                interruptBoolean(false);
                return;
            }

            delta = readLong();
        }

        if (!canReadBoolean()) {
            interruptLong(delta);
            interruptBoolean(true);
            return;
        }

        boolean reset = readBoolean();

        for (int i = 0; i < _versions.length; i++)
            ((CounterVersion) _versions[i]).init(delta, reset);
    }

    //

    private final class ReadVisitor extends Visitor {

        ReadVisitor(List<Object> interruptionStack) {
            super(interruptionStack);
        }

        @Override
        void visit(ResourceRead version) {
            throw new IllegalStateException();
        }

        @Override
        void visit(ResourceVersion version) {
            throw new IllegalStateException();
        }

        @Override
        void visit(TIndexed32Read version) {
            readTIndexed32();
        }

        @Override
        void visit(TIndexedNRead version) {
            readTIndexedN();
        }

        @Override
        void visit(TKeyedRead read) {
            readTKeyedRead();
        }

        @Override
        void visit(TKeyedVersion version) {
            readTKeyedVersion();
        }

        @Override
        void visit(TKeyedSharedVersion shared) {
            throw new IllegalStateException();
        }

        @Override
        void visit(CounterRead read) {
            // nothing
        }

        @Override
        void visit(CounterVersion version) {
            readCounter();
        }

        @Override
        void visit(CounterSharedVersion version) {
            throw new IllegalStateException();
        }
    }

    // Debug

    final void assertIdle() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (_writes != null)
            for (int i = 0; i < _writes.length; i++)
                Debug.assertion(_writes[i] == null);

        Debug.assertion(_happenedBefore == null);
    }

    @Override
    void addThreadContextObjects(List<Object> list) {
        super.addThreadContextObjects(list);

        list.add(_visitor);
    }
}
