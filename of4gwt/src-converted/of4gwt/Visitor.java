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
import of4gwt.Visitor.Listener.Action;
import of4gwt.misc.Bits;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.OverrideAssert;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.SparseArrayHelper;
import of4gwt.misc.ThreadAssert.SingleThreaded;
import of4gwt.misc.Utils;

/**
 * Visits versions of objects so processing can be done on them. E.g. a Notifier uses a
 * visitor to react to commits by raising events on changed fields.<br>
 * <br>
 * The visit can be suspended, a stack is then used to store current state until visit is
 * resumed.
 */
@SingleThreaded
public class Visitor {

    static final int NULL_MAP_INDEX = -1;

    /**
     * Called during visit.
     */
    static class Listener extends Privileged {

        /**
         * A listener can return an action to guide the visit.
         */
        protected enum Action {
            VISIT, SKIP, TERMINATE
        }

        protected void onVisitingBranch(Visitor visitor) {
            if (!visitor.interrupted())
                OverrideAssert.set(this);
        }

        protected void onVisitedBranch(Visitor visitor) {
            if (!visitor.interrupted())
                OverrideAssert.set(this);
        }

        protected Action onVisitingMap(Visitor visitor, int mapIndex) {
            if (!visitor.interrupted())
                OverrideAssert.set(this);

            if (visitor.getSnapshot().getVersionMaps()[mapIndex].getSource() == VersionMap.IMPORTS_SOURCE)
                return Action.SKIP;

            return Action.VISIT;
        }

        protected void onVisitedMap(Visitor visitor, int mapIndex) {
            if (!visitor.interrupted())
                OverrideAssert.set(this);
        }

        protected Action onVisitingTObject(Visitor visitor, TObject object) {
            if (!visitor.interrupted())
                OverrideAssert.set(this);

            return Action.VISIT;
        }

        protected void onVisitingVersions(Visitor visitor, Version shared) {
            if (!visitor.interrupted())
                OverrideAssert.set(this);
        }

        protected void onVisitedVersions(Visitor visitor, Version shared) {
            if (!visitor.interrupted())
                OverrideAssert.set(this);
        }
    }

    private final List<Object> _continueStack = new List<Object>();

    private ClassVisitor[] _classVisitors;

    private boolean _visitsTransients, _visitsReads;

    // Per-visit state

    private Listener _listener;

    private Transaction _branch;

    private Snapshot _snapshot;

    private int _mapIndex1, _mapIndex2;

    private boolean _visitingReads, _visitingNewObject, _visitingGatheredVersions = true;

    // Caches

    private Version[] _reads = new Version[SparseArrayHelper.DEFAULT_CAPACITY];

    private Version[] _writes = new Version[SparseArrayHelper.DEFAULT_CAPACITY];

    private Version[] _readList = new Version[16];

    private Version[] _writeList = new Version[16];

    private int[] _mapIndexes = new int[16];

    private int _readCount, _writeCount, _mapIndexCount;

    protected void init(Listener listener, boolean visitsTransients) {
        _listener = listener;
        _visitsTransients = visitsTransients;
    }

    public final Listener getListener() {
        return _listener;
    }

    public final boolean visitsTransients() {
        return _visitsTransients;
    }

    public final boolean visitsReads() {
        return _visitsReads;
    }

    final void setVisitsReads(boolean value) {
        _visitsReads = value;
    }

    //

    /**
     * Public transaction currently visited. E.g. local site's trunk.
     */
    public final Transaction getBranch() {
        return _branch;
    }

    final void setBranch(Transaction value) {
        _branch = value;
    }

    final Snapshot getSnapshot() {
        return _snapshot;
    }

    final void setSnapshot(Snapshot value) {
        _snapshot = value;
    }

    final int getMapIndex1() {
        return _mapIndex1;
    }

    final void setMapIndex1(int value) {
        _mapIndex1 = value;
    }

    final int getMapIndex2() {
        return _mapIndex2;
    }

    final void setMapIndex2(int value) {
        _mapIndex2 = value;
    }

    final boolean visitingNewObject() {
        return _visitingNewObject;
    }

    final void setVisitingNewObject(boolean value) {
        _visitingNewObject = value;
    }

    final boolean visitingReads() {
        return _visitingReads;
    }

    final void setVisitingReads(boolean value) {
        _visitingReads = value;
    }

    // Read Only

    final int getMapIndexCount() {
        if (_visitingNewObject)
            return _mapIndex2 - _mapIndex1;

        return _mapIndexCount;
    }

    private final int getMapIndex(int index) {
        if (_visitingNewObject) {
            if (Debug.ENABLED)
                Debug.assertion(index < _mapIndex2 - _mapIndex1);

            return _mapIndex1 + index;
        }

        return _mapIndexes[index];
    }

    //

    final boolean visitingGatheredVersions() {
        return _visitingGatheredVersions;
    }

    final void setVisitingGatheredVersions(boolean value) {
        _visitingGatheredVersions = value;
    }

    //

    /**
     * This is a specialized version of a visitor for a particular class. A visitor can
     * declare that it can visit a given class by registering a ClassVisitor for this
     * particular class.
     */
    public static abstract class ClassVisitor {

        private final Visitor _parent;

        public ClassVisitor(Visitor parent) {
            _parent = parent;
        }

        public final Visitor getParent() {
            return _parent;
        }

        protected abstract int getId();

        protected void visit(Version version) {
            throw new UnsupportedOperationException();
        }
    }

    public static final int INDEXED_VISITOR_ID = 0;

    public static final int LIST_VISITOR_ID = 1;

    public static final int KEYED_VISITOR_ID = 2;

    // Custom types can call the visit method that accepts a custom id

    final ClassVisitor getClassVisitor(int id) {
        if (_classVisitors != null && id < _classVisitors.length)
            return _classVisitors[id];

        return null;
    }

    public final void registerClassVisitor(int id, ClassVisitor visitor) {
        registerClassVisitor(id, visitor, false);
    }

    final void registerClassVisitor(int id, ClassVisitor visitor, boolean force) {
        if (_classVisitors == null)
            _classVisitors = new ClassVisitor[id + 1];

        if (_classVisitors.length <= id) {
            ClassVisitor[] temp = new ClassVisitor[id + 1];
            PlatformAdapter.arraycopy(_classVisitors, 0, temp, 0, _classVisitors.length);
            _classVisitors = temp;
        }

        if (_classVisitors[id] != null && !force)
            throw new IllegalArgumentException(Strings.CLASS_VISITOR_ID_ALREADY_REGISTERED);

        _classVisitors[id] = visitor;
    }

    //

    public final boolean interrupted() {
        return _continueStack.size() > 0;
    }

    /*
     * Use specialized version for int, byte and boolean to remove classes and avoid
     * .cctor problem with the .NET version.
     */
    public final void interrupt(Object state) {
        interruptImpl(state);
    }

    public final Object resume() {
        return resumeImpl();
    }

    public final void interruptInt(int state) {
        IntBox box = new IntBox();
        box.Value = state;
        interruptImpl(box);
    }

    public final int resumeInt() {
        IntBox box = (IntBox) resumeImpl();
        return box.Value;
    }

    private static final class IntBox {

        public int Value;
    }

    public final void interruptByte(byte state) {
        ByteBox box = new ByteBox();
        box.Value = state;
        interruptImpl(box);
    }

    public final byte resumeByte() {
        ByteBox box = (ByteBox) resumeImpl();
        return box.Value;
    }

    static final class ByteBox {

        public byte Value;
    }

    public final void interruptBoolean(boolean state) {
        BooleanBox box = new BooleanBox();
        box.Value = state;
        interruptImpl(box);
    }

    public final boolean resumeBoolean() {
        BooleanBox box = (BooleanBox) resumeImpl();
        return box.Value;
    }

    static final class BooleanBox {

        public boolean Value;
    }

    private final void interruptImpl(Object state) {
        _continueStack.add(state);

        if (Debug.STACKS)
            _continueStack.add(PlatformAdapter.getCurrentStack());
    }

    private final Object resumeImpl() {
        if (Debug.STACKS) {
            int last = _continueStack.size() - 1;
            PlatformAdapter.assertCurrentStack(_continueStack.remove(last));
        }

        int last = _continueStack.size() - 1;
        return _continueStack.remove(last);
    }

    //

    private enum VisitBranchStep {
        VISITING, VISIT, VISITED
    }

    @SuppressWarnings("fallthrough")
    final void visitBranch() {
        VisitBranchStep step = VisitBranchStep.VISITING;
        int mapIndex;

        if (interrupted()) {
            step = (VisitBranchStep) resume();
            mapIndex = resumeInt();
        } else {
            if (Debug.ENABLED)
                assertNothingToFlush();

            mapIndex = _mapIndex1;
        }

        switch (step) {
            case VISITING: {
                if (_listener != null) {
                    if (!interrupted())
                        OverrideAssert.add(_listener);

                    _listener.onVisitingBranch(this);

                    if (!interrupted())
                        OverrideAssert.end(_listener);

                    if (interrupted()) {
                        interruptInt(mapIndex);
                        interrupt(VisitBranchStep.VISITING);
                        return;
                    }
                }
            }
            case VISIT: {
                for (; mapIndex < _mapIndex2; mapIndex++) {
                    Action action = visitMap(mapIndex);

                    if (interrupted()) {
                        interruptInt(mapIndex);
                        interrupt(VisitBranchStep.VISIT);
                        return;
                    }

                    if (action == Action.TERMINATE)
                        break;
                }

                if (Debug.ENABLED)
                    Debug.assertion(!interrupted());
            }
            case VISITED: {
                if (_listener != null) {
                    if (!interrupted())
                        OverrideAssert.add(_listener);

                    _listener.onVisitedBranch(this);

                    if (!interrupted())
                        OverrideAssert.end(_listener);

                    if (interrupted()) {
                        interruptInt(mapIndex);
                        interrupt(VisitBranchStep.VISITED);
                        return;
                    }
                }
            }
        }
    }

    //

    private enum VisitMapStep {
        VISITING, READS, WRITES, VISITED
    }

    @SuppressWarnings("fallthrough")
    private final Action visitMap(int mapIndex) {
        VisitMapStep step = VisitMapStep.VISITING;
        boolean atLeastOneRead = false;
        boolean atLeastOneWrite = false;

        if (interrupted()) {
            step = (VisitMapStep) resume();
            atLeastOneRead = resumeBoolean();
            atLeastOneWrite = resumeBoolean();
        }

        Action action = Action.VISIT;

        switch (step) {
            case VISITING: {
                if (_listener != null) {
                    if (!interrupted())
                        OverrideAssert.add(_listener);

                    action = _listener.onVisitingMap(this, mapIndex);

                    if (!interrupted())
                        OverrideAssert.end(_listener);

                    if (interrupted()) {
                        interruptBoolean(atLeastOneWrite);
                        interruptBoolean(atLeastOneRead);
                        interrupt(VisitMapStep.VISITING);
                        return null;
                    }

                    if (Debug.ENABLED)
                        Debug.assertion(action != null);

                    if (action == Action.TERMINATE)
                        return action;
                }
            }
            case READS: {
                if (action == Action.VISIT) {
                    if (_visitsReads) {
                        Version[] reads = _snapshot.getReads() != null ? getSnapshot().getReads()[mapIndex] : null;

                        if (reads != null) {
                            _visitingReads = true;
                            atLeastOneRead = gatherReads(reads, mapIndex);

                            if (interrupted()) {
                                interruptBoolean(atLeastOneWrite);
                                interruptBoolean(atLeastOneRead);
                                interrupt(VisitMapStep.READS);
                                return null;
                            }

                            _visitingReads = false;
                        }
                    }
                }
            }
            case WRITES: {
                if (action == Action.VISIT) {
                    Version[] versions = _snapshot.getWrites()[mapIndex];
                    atLeastOneWrite = gatherWrites(versions, mapIndex);

                    if (interrupted()) {
                        interruptBoolean(atLeastOneWrite);
                        interruptBoolean(atLeastOneRead);
                        interrupt(VisitMapStep.WRITES);
                        return null;
                    }

                    if (atLeastOneRead || atLeastOneWrite) {
                        if (_mapIndexCount == _mapIndexes.length)
                            _mapIndexes = Utils.extend(_mapIndexes);

                        _mapIndexes[_mapIndexCount++] = mapIndex;

                        if (Debug.ENABLED)
                            for (int i = 0; i < _mapIndexCount; i++)
                                for (int j = 0; j < _mapIndexCount; j++)
                                    if (i != j)
                                        Debug.assertion(_mapIndexes[i] != _mapIndexes[j]);
                    }
                }
            }
            case VISITED: {
                if (_listener != null) {
                    if (!interrupted())
                        OverrideAssert.add(_listener);

                    _listener.onVisitedMap(this, mapIndex);

                    if (!interrupted())
                        OverrideAssert.end(_listener);

                    if (interrupted()) {
                        interruptBoolean(atLeastOneWrite);
                        interruptBoolean(atLeastOneRead);
                        interrupt(VisitMapStep.VISITED);
                        return null;
                    }
                }
            }
        }

        return null;
    }

    //

    private final boolean gatherReads(Version[] reads, int mapIndex) {
        if (Debug.ENABLED)
            Debug.assertion(reads.length > 0);

        int index;
        boolean atLeastOne = false;

        if (interrupted()) {
            index = resumeInt();
            atLeastOne = resumeBoolean();
        } else
            index = reads.length - 1;

        for (; index >= 0; index--) {
            Version version = reads[index];

            if (version != null && version.visitable(this, mapIndex)) {
                Version shared = version.getShared();
                Action action = onVisitingTObject(shared);

                if (interrupted()) {
                    interruptBoolean(atLeastOne);
                    interruptInt(index);
                    return false;
                }

                if (action == Action.VISIT) {
                    atLeastOne = true;
                    int result;

                    while ((result = VersionSet.tryToAdd(_reads, shared)) == SparseArrayHelper.REHASH) {
                        TObject.Version[] previous = _reads;

                        for (;;) {
                            _reads = new TObject.Version[_reads.length << SparseArrayHelper.TIMES_TWO_SHIFT];

                            if (VersionSet.rehash(previous, _reads))
                                break;
                        }
                    }

                    if (result >= 0) {
                        if (_readCount == _readList.length)
                            _readList = Version.extendArray(_readList);

                        _readList[_readCount++] = shared;
                    }
                }
            }
        }

        return atLeastOne;
    }

    private final boolean gatherWrites(Version[] versions, int mapIndex) {
        if (Debug.ENABLED)
            Debug.assertion(versions.length > 0);

        int index;
        boolean atLeastOne = false;

        if (interrupted()) {
            index = resumeInt();
            atLeastOne = resumeBoolean();
        } else
            index = versions.length - 1;

        for (; index >= 0; index--) {
            Version version = versions[index];

            if (version != null && version.visitable(this, mapIndex)) {
                Version shared = version.getShared();
                Action action = onVisitingTObject(shared);

                if (interrupted()) {
                    interruptBoolean(atLeastOne);
                    interruptInt(index);
                    return false;
                }

                if (action == Action.VISIT) {
                    atLeastOne = true;
                    int result;

                    while ((result = VersionSet.tryToAdd(_writes, shared)) == SparseArrayHelper.REHASH) {
                        TObject.Version[] previous = _writes;

                        for (;;) {
                            _writes = new TObject.Version[_writes.length << SparseArrayHelper.TIMES_TWO_SHIFT];

                            if (VersionSet.rehash(previous, _writes))
                                break;
                        }
                    }

                    if (result >= 0) {
                        if (_writeCount == _writeList.length)
                            _writeList = Version.extendArray(_writeList);

                        _writeList[_writeCount++] = shared;
                    }
                }
            }
        }

        return atLeastOne;
    }

    private final Action onVisitingTObject(Version version) {
        Action action = Action.VISIT;

        if (_listener != null) {
            if (!interrupted())
                OverrideAssert.add(_listener);

            action = _listener.onVisitingTObject(this, version);

            if (!interrupted())
                OverrideAssert.end(_listener);

            if (interrupted())
                return null;

            if (Debug.ENABLED)
                Debug.assertion(action != null);
        }

        return action;
    }

    //

    private enum FlushStep {
        READS, WRITES
    }

    @SuppressWarnings("fallthrough")
    final void flush() {
        FlushStep step = FlushStep.READS;
        int index;

        if (interrupted()) {
            step = (FlushStep) resume();
            index = resumeInt();
        } else {
            if (_mapIndexCount == 0)
                return;

            _visitingReads = true;
            index = _readCount - 1;
        }

        switch (step) {
            case READS: {
                for (; index >= 0; index--) {
                    if (Debug.ENABLED)
                        Debug.assertion(_visitsReads);

                    Version shared = _readList[index];
                    visitVersions(shared);

                    if (interrupted()) {
                        interruptInt(index);
                        interrupt(FlushStep.READS);
                        return;
                    }
                }

                _visitingReads = false;

                for (int i = _readCount - 1; i >= 0; i--) {
                    VersionSet.removePartOfClear(_reads, _readList[i]);
                    _readList[i] = null;
                }

                _readCount = 0;

                if (Debug.ENABLED)
                    for (int i = _reads.length - 1; i >= 0; i--)
                        Debug.assertion(_reads[i] == null);

                index = _writeCount - 1;
            }
            case WRITES: {
                for (; index >= 0; index--) {
                    Version shared = _writeList[index];
                    visitVersions(shared);

                    if (interrupted()) {
                        interruptInt(index);
                        interrupt(FlushStep.WRITES);
                        return;
                    }
                }

                for (int i = _writeCount - 1; i >= 0; i--) {
                    VersionSet.removePartOfClear(_writes, _writeList[i]);
                    _writeList[i] = null;
                }

                _writeCount = 0;

                if (Debug.ENABLED)
                    for (int i = _writes.length - 1; i >= 0; i--)
                        Debug.assertion(_writes[i] == null);

                _mapIndexCount = 0;
            }
        }
    }

    final Version popWrite() {
        Version shared = null;

        if (_writeCount > 0) {
            _writeCount--;
            shared = _writeList[_writeCount];
            _writeList[_writeCount] = null;
            VersionSet.removePartOfClear(_writes, shared);
        }

        return shared;
    }

    final void onPoppedAllWrites() {
        if (Debug.ENABLED) {
            Debug.assertion(_readCount == 0 && _writeCount == 0);

            for (int i = _reads.length - 1; i >= 0; i--)
                Debug.assertion(_reads[i] == null);

            for (int i = _readList.length - 1; i >= 0; i--)
                Debug.assertion(_readList[i] == null);

            for (int i = _writes.length - 1; i >= 0; i--)
                Debug.assertion(_writes[i] == null);

            for (int i = _writeList.length - 1; i >= 0; i--)
                Debug.assertion(_writeList[i] == null);
        }

        _mapIndexCount = 0;
    }

    private enum VisitVersionStep {
        VISITING, VISIT, VISITED
    }

    @SuppressWarnings("fallthrough")
    final void visitVersions(Version shared) {
        VisitVersionStep step = VisitVersionStep.VISITING;

        if (interrupted())
            step = (VisitVersionStep) resume();

        switch (step) {
            case VISITING: {
                if (_listener != null) {
                    if (!interrupted())
                        OverrideAssert.add(_listener);

                    _listener.onVisitingVersions(this, shared);

                    if (!interrupted())
                        OverrideAssert.end(_listener);

                    if (interrupted()) {
                        interrupt(VisitVersionStep.VISITING);
                        return;
                    }
                }
            }
            case VISIT: {
                shared.visit(this);

                if (interrupted()) {
                    interrupt(VisitVersionStep.VISIT);
                    return;
                }
            }
            case VISITED: {
                if (_listener != null) {
                    if (!interrupted())
                        OverrideAssert.add(_listener);

                    _listener.onVisitedVersions(this, shared);

                    if (!interrupted())
                        OverrideAssert.end(_listener);

                    if (interrupted()) {
                        interrupt(VisitVersionStep.VISITED);
                        return;
                    }
                }
            }
        }
    }

    //

    protected final Version getGatheredVersion(Version shared, int index) {
        if (Debug.ENABLED)
            Debug.assertion(shared.isShared());

        int mapIndex = getMapIndex(index);

        if (mapIndex == TransactionManager.OBJECTS_VERSIONS_INDEX)
            return shared;

        Version[][] versions = visitingReads() ? getSnapshot().getReads() : getSnapshot().getWrites();
        return TransactionSets.getVersionFromSharedVersion(versions[mapIndex], shared);
    }

    //

    /*
     * Hard-coded classes. Default implementations is to invoke the class visitors if one
     * is registered.
     */

    /*
     * Indexed 32.
     */

    protected void visit(TIndexed32Read version) {
        int bits;

        if (interrupted())
            bits = resumeInt();
        else {
            if (!visitingGatheredVersions())
                bits = version.getBits();
            else {
                if (Debug.ENABLED)
                    Debug.assertion(version.isShared());

                bits = 0;

                for (int i = getMapIndexCount() - 1; i >= 0; i--) {
                    TIndexed32Read currentVersion = (TIndexed32Read) getGatheredVersion(version, i);

                    if (currentVersion != null) {
                        if (Debug.ENABLED)
                            Debug.assertion(currentVersion.getBits() != 0 || currentVersion.isShared());

                        bits |= currentVersion.getBits();
                    }
                }
            }

            if (!visitsTransients())
                bits = Bits.andNot(bits, version.getTransients());
        }

        if (bits != 0) {
            visit(version, bits);

            if (interrupted())
                interruptInt(bits);
        }
    }

    protected void visit(TIndexed32Version version) {
        visit((TIndexed32Read) version);
    }

    protected void visit(TIndexed32Read version, int bits) {
        TIndexed.Visitor visitor = (TIndexed.Visitor) getClassVisitor(INDEXED_VISITOR_ID);

        if (visitor != null)
            visitor.visitTIndexed32(version.getShared(), bits);
    }

    /*
     * Indexed N.
     */

    @SuppressWarnings("null")
    protected void visit(TIndexedNRead version) {
        Bits.Entry[] bits;

        if (interrupted())
            bits = (Bits.Entry[]) resume();
        else {
            if (!visitingGatheredVersions())
                bits = version.getBits();
            else {
                if (Debug.ENABLED)
                    Debug.assertion(version.isShared());

                bits = null;
                boolean cloned = false;

                for (int i = getMapIndexCount() - 1; i >= 0; i--) {
                    Version currentVersion = getGatheredVersion(version, i);

                    if (currentVersion != null) {
                        Bits.Entry[] currentBits = ((TIndexedNRead) currentVersion).getBits();

                        // Shared always has bits, so can be empty
                        if (currentVersion.isShared()) {
                            if (Bits.isEmpty(currentBits))
                                currentBits = null;
                        } else if (Debug.ENABLED)
                            Debug.assertion(!Bits.isEmpty(currentBits));

                        if (currentBits != null) {
                            if (bits == null)
                                bits = currentBits;
                            else {
                                if (!cloned) {
                                    // No clone in GWT & removed in .NET
                                    Bits.Entry[] clone = new Bits.Entry[bits.length];
                                    PlatformAdapter.arraycopy(bits, 0, clone, 0, bits.length);
                                    bits = clone;
                                    cloned = true;
                                }

                                while (!Bits.mergeByCopy(bits, currentBits)) {
                                    Bits.Entry[] old = bits;

                                    for (;;) {
                                        bits = PlatformAdapter.createBitsArray(bits.length << SparseArrayHelper.TIMES_TWO_SHIFT);

                                        if (Bits.reindex(old, bits))
                                            break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!visitsTransients()) {
                Bits.Entry[] transients = version.getTransients();

                if (transients != null)
                    bits = Bits.andNot(bits, transients);
            }
        }

        if (bits != null) {
            visit(version, bits);

            if (interrupted())
                interrupt(bits);
        }
    }

    protected void visit(TIndexedNVersion version) {
        visit((TIndexedNRead) version);
    }

    protected void visit(TIndexedNRead version, Bits.Entry[] bits) {
        TIndexed.Visitor visitor = (TIndexed.Visitor) getClassVisitor(INDEXED_VISITOR_ID);

        if (visitor != null)
            visitor.visitTIndexedN(version.getShared(), bits);
    }

    /*
     * TKeyed.
     */

    protected void visit(TKeyedRead read) {
        if (Debug.ENABLED)
            Debug.assertion(!visitingGatheredVersions());

        visit((TKeyedSharedVersion) read.getShared(), read.getEntries(), false, read.getFullyRead());
    }

    protected void visit(TKeyedVersion version) {
        if (Debug.ENABLED)
            Debug.assertion(!visitingGatheredVersions());

        visit((TKeyedSharedVersion) version.getShared(), version.getEntries(), version.getCleared(), false);
    }

    protected void visit(LazyMapVersion version) {
        if (Debug.ENABLED)
            Debug.assertion(!visitingGatheredVersions());

        visit((TKeyedSharedVersion) version.getShared(), version.getEntries(), false, false);
    }

    @SuppressWarnings("null")
    protected void visit(TKeyedSharedVersion shared) {
        TKeyedEntry[] entries;
        boolean cleared = false;
        boolean fullyRead = false;

        if (interrupted()) {
            entries = (TKeyedEntry[]) resume();
            cleared = resumeBoolean();
            fullyRead = resumeBoolean();
        } else {
            if (Debug.ENABLED)
                Debug.assertion(visitingGatheredVersions());

            entries = null;
            TKeyedBase2 merged = null;

            for (int i = 0; i < getMapIndexCount(); i++) {
                Version currentVersion = getGatheredVersion(shared, i);

                if (currentVersion != null) {
                    if (currentVersion instanceof TKeyedVersion) {
                        if (((TKeyedVersion) currentVersion).getCleared()) {
                            if (Debug.ENABLED)
                                Debug.assertion(!visitingReads());

                            entries = null;
                            merged = null;
                            cleared = true;
                        }
                    }

                    TKeyedEntry[] currentEntries;

                    if (currentVersion instanceof LazyMapSharedVersion)
                        currentEntries = null;
                    else if (currentVersion instanceof TKeyedSharedVersion)
                        currentEntries = ((TKeyedSharedVersion) currentVersion).getWrites();
                    else {
                        currentEntries = ((TKeyedBase2) currentVersion).getEntries();

                        if (currentVersion instanceof TKeyedRead && ((TKeyedRead) currentVersion).getFullyRead()) {
                            if (Debug.ENABLED)
                                Debug.assertion(visitingReads());

                            entries = null;
                            merged = null;
                            fullyRead = true;
                            break;
                        }
                    }

                    if (currentEntries != null) {
                        if (entries == null)
                            entries = currentEntries;
                        else {
                            if (merged == null) {
                                merged = new TKeyedBase2(shared);
                                merged.setEntries(new TKeyedEntry[entries.length]);
                                PlatformAdapter.arraycopy(entries, 0, merged.getEntries(), 0, entries.length);

                                // Cannot copy, must recompute as might be merging
                                for (int j = 0; j < merged.getEntries().length; j++)
                                    if (merged.getEntries()[j] != null && merged.getEntries()[j] != TKeyedEntry.REMOVED)
                                        merged._entryCount++;
                            }

                            for (int t = currentEntries.length - 1; t >= 0; t--)
                                if (currentEntries[t] != null)
                                    merged.putEntry(currentEntries[t].getKeyDirect(), currentEntries[t], true, false);
                        }
                    }
                }
            }

            if (merged != null)
                entries = merged.getEntries();
        }

        if (entries != null || cleared || fullyRead) {
            visit(shared, entries, cleared, fullyRead);

            if (interrupted()) {
                interruptBoolean(fullyRead);
                interruptBoolean(cleared);
                interrupt(entries);
            }
        }
    }

    protected void visit(TKeyedSharedVersion shared, TKeyedEntry[] entries, boolean cleared, boolean fullyRead) {
        TKeyed.Visitor visitor = (TKeyed.Visitor) getClassVisitor(KEYED_VISITOR_ID);

        if (visitor != null)
            visitor.visitTKeyed(shared, entries, cleared);
    }

    /*
     * TList.
     */

    protected void visitRead(TListSharedVersion shared) {
        TList.Visitor visitor = (TList.Visitor) getClassVisitor(LIST_VISITOR_ID);

        if (visitor != null)
            visitor.visitRead(shared);
    }

    protected void visitSnapshot(TListSharedVersion shared, Object[] array, int size) {
        TList.Visitor visitor = (TList.Visitor) getClassVisitor(LIST_VISITOR_ID);

        if (visitor != null)
            visitor.visitGatheredWrites(shared, array, size);
    }

    //

    protected void visit(TListRead read) {
        if (Debug.ENABLED)
            Debug.assertion(!visitingGatheredVersions());

        TListSharedVersion shared = (TListSharedVersion) read.getUnion();
        visitRead(shared);
    }

    protected void visit(TListVersion version) {
        TList.Visitor visitor = (TList.Visitor) getClassVisitor(LIST_VISITOR_ID);

        if (visitor != null)
            visitor.visitVersion(version);
    }

    @SuppressWarnings("null")
    protected void visit(TListSharedVersion shared) {
        if (Debug.ENABLED)
            Debug.assertion(visitingGatheredVersions());

        if (visitingReads()) {
            visitRead(shared);
            return;
        }

        if (getMapIndexCount() > 0 && getGatheredVersion(shared, 0) == shared) {
            visitSnapshot(shared);
            return;
        }

        TListVersion version = null;

        if (interrupted())
            version = (TListVersion) resume();
        else {
            boolean cloned = false;

            for (int i = 0; i < getMapIndexCount(); i++) {
                Version currentBase = getGatheredVersion(shared, i);

                if (currentBase != null) {
                    if (Debug.ENABLED)
                        Debug.assertion(currentBase != shared);

                    TListVersion currentVersion = (TListVersion) currentBase;

                    if (version == null)
                        version = currentVersion;
                    else {
                        if (!cloned) {
                            version = (TListVersion) version.cloneThis(visitingReads());
                            cloned = true;
                        }

                        TObject.Version merged = version.merge(version, currentVersion, Version.MERGE_FLAG_BY_COPY);

                        if (Debug.ENABLED)
                            Debug.assertion(merged == version);
                    }
                }
            }
        }

        if (version != null) {
            if (version.getBits() != null || version.getRemovalsCount() != 0 || version.getInsertsCount() != 0 || version.getCleared()) {
                visit(version);

                if (interrupted())
                    interrupt(version);
            }
        }
    }

    @SuppressWarnings("null")
    private final void visitSnapshot(TListSharedVersion shared) {
        Object[] array;
        int size;

        if (interrupted()) {
            array = (Object[]) resume();
            size = resumeInt();
        } else {
            array = shared.getArray();
            size = shared.size();

            for (int i = 0; i < getMapIndexCount(); i++) {
                Version currentBase = getGatheredVersion(shared, i);

                if (currentBase != null && currentBase != shared) {
                    TListVersion version = (TListVersion) currentBase;
                    size = version.size();

                    if (version.getCleared())
                        array = null;

                    if (version.getCopied() != null || version.getBits() != null) {
                        if (array == null)
                            array = new Object[size];

                        if (array.length < size) {
                            Object[] temp = new Object[size];
                            PlatformAdapter.arraycopy(array, 0, temp, 0, array.length);
                            array = temp;
                        }

                        if (array == shared.getArray()) {
                            Object[] temp = new Object[array.length];
                            PlatformAdapter.arraycopy(array, 0, temp, 0, array.length);
                            array = temp;
                        }
                    }

                    if (version.getCopied() != null)
                        for (int t = version.getCopiedStart(); t < size; t++)
                            array[t] = version.getCopied()[t - version.getCopiedStart()];

                    if (version.getBits() != null) {
                        for (int entry = 0; entry < version.getBits().length; entry++) {
                            if (version.getBits()[entry] != null) {
                                int offset = version.getBits()[entry].IntIndex << Bits.BITS_PER_UNIT_SHIFT;

                                for (int index = 0; index < Bits.BITS_PER_UNIT; index++) {
                                    if (Bits.get(version.getBits()[entry].Value, index)) {
                                        int actualIndex = offset + index;

                                        if (array.length <= actualIndex) {
                                            Object[] temp = new Object[actualIndex + 1];
                                            PlatformAdapter.arraycopy(array, 0, temp, 0, array.length);
                                            array = temp;
                                        }

                                        array[actualIndex] = version.get(actualIndex);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (size > 0) {
            visitSnapshot(shared, array, size);

            if (interrupted()) {
                interruptInt(size);
                interrupt(array);
            }
        }
    }

    /*
     * Other classes.
     */

    public void visit(int classVisitorId, TObject.Version shared, Version version) {
        ClassVisitor visitor = getClassVisitor(classVisitorId);

        if (visitor != null)
            visitor.visit(version);
    }

    // Debug

    final void assertNothingToFlush() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Debug.assertion(_mapIndexCount == 0);
        Debug.assertion(_readCount == 0);
        Debug.assertion(_writeCount == 0);
    }

    final List<Object> getThreadContextObjects() {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        List<Object> list = new List<Object>();
        OverrideAssert.add(this);
        addThreadContextObjects(list);
        OverrideAssert.end(this);
        return list;
    }

    void addThreadContextObjects(List<Object> list) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        OverrideAssert.set(this);

        list.add(this);
    }
}
