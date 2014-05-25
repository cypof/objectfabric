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

import org.objectfabric.TObject.Version;
import org.objectfabric.ThreadAssert.AllowSharedRead;
import org.objectfabric.ThreadAssert.SingleThreaded;
import org.objectfabric.Workspace.Granularity;

/**
 * Plugs into an View to get notified of changes on transactional objects. Uses two
 * alternative snapshots of the version queue to "walk" it and visit changes.
 */
@SingleThreaded
abstract class Extension extends Visitor {

    // TODO remove?
    enum Action {
        VISIT, SKIP
    }

    @SuppressWarnings("serial")
    static final class ExtensionShutdownException extends RuntimeException {
    }

    @AllowSharedRead
    private final Workspace _workspace;

    @AllowSharedRead
    private final boolean _splitsSources;

    private Snapshot _snapshot;

    private int _mapIndex1, _mapIndex2;

    //

    private TObject[] _reads = new TObject[OpenMap.CAPACITY];

    private TObject[] _writes = new TObject[OpenMap.CAPACITY];

    private final Resources _resources = new Resources();

    private TObject[][] _readList = new TObject[Resources.DEFAULT_CAPACITY][];

    private TObject[][] _writeList = new TObject[Resources.DEFAULT_CAPACITY][];

    private int[] _readCount = new int[Resources.DEFAULT_CAPACITY];

    private int[] _writeCount = new int[Resources.DEFAULT_CAPACITY];

    private boolean _visitsTransients, _visitsReads;

    // TODO simplify
    private boolean _visitingRead, _visitingNewObject;

    private int[] _mapIndexes = new int[16];

    private int _mapIndexCount;

    Extension(Workspace workspace, boolean splitsSources) {
        super(new List<Object>());

        if (workspace == null)
            throw new IllegalArgumentException();

        _workspace = workspace;
        _splitsSources = splitsSources;
    }

    // TODO move constructor?
    final void init(boolean visitsTransients, boolean visitsReads) {
        _visitsTransients = visitsTransients;
        _visitsReads = visitsReads;
    }

    final Workspace workspace() {
        return _workspace;
    }

    final boolean visitsTransients() {
        return _visitsTransients;
    }

    final boolean visitsReads() {
        return _visitsReads;
    }

    public final boolean splitsSources() {
        return _splitsSources;
    }

    final boolean visitingNewObject() {
        return _visitingNewObject;
    }

    final void visitingNewObject(boolean value) {
        _visitingNewObject = value;
    }

    final boolean visitingRead() {
        return _visitingRead;
    }

    final void visitingRead(boolean value) {
        _visitingRead = value;
    }

    final Snapshot snapshot() {
        return _snapshot;
    }

    final int mapIndex1() {
        return _mapIndex1;
    }

    final void mapIndex1(int value) {
        _mapIndex1 = value;
    }

    final int mapIndex2() {
        return _mapIndex2;
    }

    final void mapIndex2(int value) {
        _mapIndex2 = value;
    }

    //

    boolean casSnapshotWithThis(Snapshot expected, Snapshot update) {
        VersionMap map = expected.last();

        if (_workspace.granularity() == Granularity.COALESCE) {
            if (map.tryToAddWatchers(1)) {
                if (Debug.ENABLED)
                    Helper.instance().addWatcher(map, this, expected, "casSnapshotWithThis");
            } else
                return false;
        }

        if (_workspace.casSnapshot(expected, update)) {
            _snapshot = expected;
            return true;
        }

        // If failed, remove watchers and return false to retry

        if (_workspace.granularity() == Granularity.COALESCE) {
            if (Debug.ENABLED)
                Helper.instance().removeWatcher(map, this, expected, "casSnapshotWithThis failed");

            map.removeWatchers(_workspace, 1, false, null);
        }

        return false;
    }

    boolean casSnapshotWithoutThis(Snapshot expected, Snapshot update, Exception exception) {
        if (_workspace.casSnapshot(expected, update)) {
            VersionMap previous = _snapshot.last();

            if (_workspace.granularity() == Granularity.COALESCE) {
                if (Debug.ENABLED)
                    Helper.instance().removeWatcher(previous, this, expected, "unregister previous");

                previous.removeWatchers(_workspace, 1, false, null);
            } else {
                int index = Helper.getIndex(expected, _snapshot.last());
                int start = expected.getVersionMaps().length - 2;

                if (expected.last() == VersionMap.CLOSING)
                    start--;

                for (int i = start; i >= index; i--) {
                    if (Debug.ENABLED)
                        Helper.instance().removeWatcher(expected.getVersionMaps()[i], this, expected, "unregister " + i);

                    expected.getVersionMaps()[i].removeWatchers(_workspace, 1, false, null);
                }
            }

            _snapshot = null;
            return true;
        }

        return false;
    }

    //

    final void walk() {
        if (!interrupted()) {
            if (_snapshot == null)
                return;

            Snapshot snapshot = _workspace.snapshotWithoutClosing();
            VersionMap previous = _snapshot.last();

            if (snapshot.last() == previous)
                return;

            /*
             * TODO Create snapshot only if a maximum number is not reached, e.g. 8 for
             * all walkers, so that queue is not too large. Otherwise reuse the latest
             * snapshot created.
             */
            for (;;) {
                if (_workspace.granularity() == Granularity.ALL)
                    break;

                VersionMap map = snapshot.last();

                if (map.tryToAddWatchers(1)) {
                    if (Debug.ENABLED)
                        Helper.instance().addWatcher(map, this, snapshot, "run");

                    break;
                }

                snapshot = _workspace.snapshotWithoutClosing();
            }

            _snapshot = snapshot;

            if (Debug.ENABLED)
                Debug.assertion(snapshot.last() != previous);

            int index = Helper.getIndex(snapshot, previous);
            mapIndex1(index + 1);
            mapIndex2(snapshot.lastIndex() + 1);
        }

        visitWorkspace();

        if (!interrupted()) {
            if (_workspace.granularity() == Granularity.COALESCE)
                releaseSnapshot(mapIndex1(), mapIndex2());

            if (Debug.ENABLED)
                Debug.assertion(!interrupted());
        }
    }

    void releaseSnapshot(int start, int end) {
        VersionMap[] maps = _snapshot.getVersionMaps();
        VersionMap map = maps[start - 1];

        if (Debug.ENABLED)
            Helper.instance().removeWatcher(map, this, _snapshot, "Walker releaseSnapshot");

        map.removeWatchers(_workspace, 1, false, _snapshot);

        if (_splitsSources) {
            for (int i = start; i < end; i++) {
                if (maps[i].isRemote() != map.isRemote()) {
                    if (Debug.ENABLED)
                        Helper.instance().removeWatcher(map, this, _snapshot, "Walker releaseSnapshot 2");

                    map.removeWatchers(_workspace, 1, false, null);
                }

                map = maps[i];
            }
        }
    }

    /*
     * Version gathering.
     */

    final void addMapIndex(int mapIndex) {
        if (_mapIndexCount == _mapIndexes.length)
            _mapIndexes = Helper.extend(_mapIndexes);

        _mapIndexes[_mapIndexCount++] = mapIndex;

        if (Debug.ENABLED)
            for (int i = 0; i < _mapIndexCount; i++)
                for (int j = 0; j < _mapIndexCount; j++)
                    if (i != j)
                        Debug.assertion(_mapIndexes[i] != _mapIndexes[j]);
    }

    final Version getGatheredVersion(TObject object, int index) {
        int mapIndex = mapIndex(index);

        if (mapIndex == TransactionManager.OBJECTS_VERSIONS_INDEX)
            return object.shared_();

        Version[][] versions = visitingRead() ? snapshot().getReads() : snapshot().writes();

        if (versions[mapIndex] != null)
            return TransactionBase.getVersion(versions[mapIndex], object);

        return null;
    }

    final int mapIndexCount() {
        if (_visitingNewObject)
            return mapIndex2() - mapIndex1();

        return _mapIndexCount;
    }

    final void mapIndexCount(int value) {
        if (Debug.ENABLED)
            Debug.assertion(!_visitingNewObject);

        _mapIndexCount = value;
    }

    private final int mapIndex(int index) {
        if (_visitingNewObject) {
            if (Debug.ENABLED)
                Debug.assertion(index < mapIndex2() - mapIndex1());

            return mapIndex1() + index;
        }

        return _mapIndexes[index];
    }

    /*
     * Visit.
     */

    void onVisitingWorkspace() {
        if (!interrupted())
            OverrideAssert.set(this);
    }

    void onVisitedWorkspace() {
        if (!interrupted())
            OverrideAssert.set(this);

        if (_workspace.granularity() != Granularity.ALL)
            visitResources();
    }

    //

    private enum VisitWorkspaceStep {
        VISITING, VISIT, VISITED
    }

    @SuppressWarnings("fallthrough")
    private final void visitWorkspace() {
        VisitWorkspaceStep step = VisitWorkspaceStep.VISITING;
        int mapIndex;

        if (interrupted()) {
            step = (VisitWorkspaceStep) resume();
            mapIndex = resumeInt();
        } else {
            if (Debug.ENABLED)
                assertNothingToFlush();

            mapIndex = mapIndex1();
        }

        switch (step) {
            case VISITING: {
                if (!interrupted())
                    OverrideAssert.add(this);

                onVisitingWorkspace();

                if (!interrupted())
                    OverrideAssert.end(this);

                if (interrupted()) {
                    interruptInt(mapIndex);
                    interrupt(VisitWorkspaceStep.VISITING);
                    return;
                }
            }
            case VISIT: {
                for (; mapIndex < mapIndex2(); mapIndex++) {
                    visitMap(mapIndex);

                    if (interrupted()) {
                        interruptInt(mapIndex);
                        interrupt(VisitWorkspaceStep.VISIT);
                        return;
                    }

                    if (_workspace.granularity() == Granularity.ALL) {
                        // Done with map, let it merge
                        releaseSnapshot(mapIndex, mapIndex + 1);
                    }
                }

                if (Debug.ENABLED)
                    Debug.assertion(!interrupted());
            }
            case VISITED: {
                if (!interrupted())
                    OverrideAssert.add(this);

                onVisitedWorkspace();

                if (!interrupted())
                    OverrideAssert.end(this);

                if (interrupted()) {
                    interruptInt(mapIndex);
                    interrupt(VisitWorkspaceStep.VISITED);
                    return;
                }
            }
        }
    }

    //

    Action onVisitingMap(int mapIndex) {
        if (!interrupted())
            OverrideAssert.set(this);

        if (Debug.ENABLED) {
            if (_workspace.granularity() == Granularity.ALL) {
                VersionMap map = snapshot().getVersionMaps()[mapIndex];

                Debug.assertion(map.isNotMerging());
                Debug.assertion(map.getTransaction() != null);
                Debug.assertion(map.getTransaction().parent() == null);
                Debug.assertion(map.getTransaction().workspace() == _workspace);

                if (Debug.THREADS)
                    ThreadAssert.assertShared(map);
            }
        }

        return Action.VISIT;
    }

    void onVisitedMap(int mapIndex) {
        if (!interrupted())
            OverrideAssert.set(this);

        if (_workspace.granularity() == Granularity.ALL)
            visitResources();
    }

    //

    private enum VisitMapStep {
        VISITING, READS, WRITES, VISITED
    }

    @SuppressWarnings("fallthrough")
    final void visitMap(int mapIndex) {
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
                if (!interrupted())
                    OverrideAssert.add(this);

                action = onVisitingMap(mapIndex);

                if (!interrupted())
                    OverrideAssert.end(this);

                if (interrupted()) {
                    interruptBoolean(atLeastOneWrite);
                    interruptBoolean(atLeastOneRead);
                    interrupt(VisitMapStep.VISITING);
                    return;
                }

                if (Debug.ENABLED)
                    Debug.assertion(action != null);
            }
            case READS: {
                if (action == Action.VISIT) {
                    if (visitsReads()) {
                        Version[] reads = snapshot().getReads() != null ? snapshot().getReads()[mapIndex] : null;

                        if (reads != null) {
                            visitingRead(true);
                            atLeastOneRead = gatherReads(reads);

                            if (interrupted()) {
                                interruptBoolean(atLeastOneWrite);
                                interruptBoolean(atLeastOneRead);
                                interrupt(VisitMapStep.READS);
                                return;
                            }

                            visitingRead(false);
                        }
                    }
                }
            }
            case WRITES: {
                if (action == Action.VISIT) {
                    Version[] versions = snapshot().writes()[mapIndex];
                    atLeastOneWrite = gatherWrites(versions);

                    if (interrupted()) {
                        interruptBoolean(atLeastOneWrite);
                        interruptBoolean(atLeastOneRead);
                        interrupt(VisitMapStep.WRITES);
                        return;
                    }

                    if (atLeastOneRead || atLeastOneWrite)
                        addMapIndex(mapIndex);
                }
            }
            case VISITED: {
                if (!interrupted())
                    OverrideAssert.add(this);

                onVisitedMap(mapIndex);

                if (!interrupted())
                    OverrideAssert.end(this);

                if (interrupted()) {
                    interruptBoolean(atLeastOneWrite);
                    interruptBoolean(atLeastOneRead);
                    interrupt(VisitMapStep.VISITED);
                    return;
                }
            }
        }
    }

    //

    private final boolean gatherReads(Version[] reads) {
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

            if (version != null) {
                Action action = visitTObject(version.object());

                if (interrupted()) {
                    interruptBoolean(atLeastOne);
                    interruptInt(index);
                    return false;
                }

                if (action == Action.VISIT) {
                    atLeastOne = true;
                    int result;

                    while ((result = TObjectSet.tryToAdd(_reads, version.object())) == OpenMap.REHASH) {
                        TObject[] previous = _reads;

                        for (;;) {
                            _reads = new TObject[_reads.length << OpenMap.TIMES_TWO_SHIFT];

                            if (TObjectSet.rehash(previous, _reads))
                                break;
                        }
                    }

                    if (result >= 0) {
                        int resource = _resources.add(version.object().resource());

                        if (resource >= 0) {
                            if (resource >= _readCount.length) {
                                _readCount = Helper.extend(_readCount);
                                _readList = Helper.extend(_readList);
                            }

                            if (_readList[resource] == null)
                                _readList[resource] = new TObject[16];
                        } else
                            resource = -resource - 1;

                        if (_readCount[resource] == _readList[resource].length)
                            _readList[resource] = Helper.extend(_readList[resource]);

                        _readList[resource][_readCount[resource]++] = version.object();
                    }
                }
            }
        }

        return atLeastOne;
    }

    private final boolean gatherWrites(Version[] versions) {
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

            if (version != null) {
                Action action = visitTObject(version.object());

                if (interrupted()) {
                    interruptBoolean(atLeastOne);
                    interruptInt(index);
                    return false;
                }

                if (action == Action.VISIT) {
                    atLeastOne = true;
                    int result;

                    while ((result = TObjectSet.tryToAdd(_writes, version.object())) == OpenMap.REHASH) {
                        TObject[] previous = _writes;

                        for (;;) {
                            _writes = new TObject[_writes.length << OpenMap.TIMES_TWO_SHIFT];

                            if (TObjectSet.rehash(previous, _writes))
                                break;
                        }
                    }

                    if (result >= 0) {
                        int resource = _resources.add(version.object().resource());

                        if (resource >= 0) {
                            if (resource >= _writeCount.length) {
                                _writeCount = Helper.extend(_writeCount);
                                _writeList = Helper.extend(_writeList);
                            }

                            if (_writeList[resource] == null)
                                _writeList[resource] = new TObject[16];
                        } else
                            resource = -resource - 1;

                        if (_writeCount[resource] == _writeList[resource].length)
                            _writeList[resource] = Helper.extend(_writeList[resource]);

                        _writeList[resource][_writeCount[resource]++] = version.object();
                    }
                }
            }
        }

        return atLeastOne;
    }

    Action onVisitingTObject(TObject object) {
        if (!interrupted())
            OverrideAssert.set(this);

        return Action.VISIT;
    }

    private final Action visitTObject(TObject object) {
        if (!interrupted())
            OverrideAssert.add(this);

        Action action = onVisitingTObject(object);

        if (!interrupted())
            OverrideAssert.end(this);

        if (interrupted())
            return null;

        if (Debug.ENABLED)
            Debug.assertion(action != null);

        return action;
    }

    //

    // Not interruptible
    void onVisitingResources(Resources resources) {
        OverrideAssert.set(this);
    }

    void onVisitingResource(Resource resource) {
        if (!interrupted())
            OverrideAssert.set(this);
    }

    void onVisitedResource(Resource resource) {
        if (!interrupted())
            OverrideAssert.set(this);
    }

    //

    private enum VisitURIStep {
        VISITING, VISIT, VISITED
    }

    @SuppressWarnings("fallthrough")
    private final void visitResources() {
        int index;
        VisitURIStep step = VisitURIStep.VISITING;

        if (interrupted()) {
            index = resumeInt();
            step = (VisitURIStep) resume();
        } else {
            index = _resources.size() - 1;

            if (index >= 0) {
                OverrideAssert.add(this);
                onVisitingResources(_resources);
                OverrideAssert.end(this);
            }
        }

        for (; index >= 0; index--) {
            Resource resource = _resources.get(index);

            switch (step) {
                case VISITING: {
                    if (!interrupted())
                        OverrideAssert.add(this);

                    onVisitingResource(resource);

                    if (!interrupted())
                        OverrideAssert.end(this);

                    if (interrupted()) {
                        interrupt(VisitURIStep.VISITING);
                        interruptInt(index);
                        return;
                    }
                }
                case VISIT: {
                    visitVersions(index);

                    if (interrupted()) {
                        interrupt(VisitURIStep.VISIT);
                        interruptInt(index);
                        return;
                    }

                    _resources.pollPartOfClear();

                    if (Debug.ENABLED)
                        Debug.assertion(!interrupted());
                }
                case VISITED: {
                    if (!interrupted())
                        OverrideAssert.add(this);

                    onVisitedResource(resource);

                    if (!interrupted())
                        OverrideAssert.end(this);

                    if (interrupted()) {
                        interrupt(VisitURIStep.VISITED);
                        interruptInt(index);
                        return;
                    }
                }
            }
        }

        mapIndexCount(0);
    }

    //

    private enum FlushStep {
        READS, WRITES
    }

    @SuppressWarnings("fallthrough")
    final void visitVersions(int uri) {
        FlushStep step = FlushStep.READS;
        int index;

        if (interrupted()) {
            step = (FlushStep) resume();
            index = resumeInt();
        } else {
            visitingRead(true);
            index = _readCount[uri] - 1;
        }

        switch (step) {
            case READS: {
                for (; index >= 0; index--) {
                    if (Debug.ENABLED)
                        Debug.assertion(visitsReads());

                    TObject object = _readList[uri][index];
                    visit(object);

                    if (interrupted()) {
                        interruptInt(index);
                        interrupt(FlushStep.READS);
                        return;
                    }
                }

                visitingRead(false);

                for (int i = _readCount[uri] - 1; i >= 0; i--) {
                    TObjectSet.removePartOfClear(_reads, _readList[uri][i]);
                    _readList[uri][i] = null;
                }

                _readCount[uri] = 0;

                if (Debug.ENABLED)
                    if (uri == 0)
                        for (int i = _reads.length - 1; i >= 0; i--)
                            Debug.assertion(_reads[i] == null);

                index = _writeCount[uri] - 1;
            }
            case WRITES: {
                for (; index >= 0; index--) {
                    TObject object = _writeList[uri][index];
                    visit(object);

                    if (interrupted()) {
                        interruptInt(index);
                        interrupt(FlushStep.WRITES);
                        return;
                    }
                }

                for (int i = _writeCount[uri] - 1; i >= 0; i--) {
                    TObjectSet.removePartOfClear(_writes, _writeList[uri][i]);
                    _writeList[uri][i] = null;
                }

                _writeCount[uri] = 0;

                if (Debug.ENABLED)
                    if (uri == 0)
                        for (int i = _writes.length - 1; i >= 0; i--)
                            Debug.assertion(_writes[i] == null);
            }
        }
    }

    /**
     * ! Not interruptible !
     */
    void onVisitingVersion(Version version) {
        OverrideAssert.set(this);
    }

    final void visit(TObject object) {
        Version version;

        if (interrupted())
            version = (Version) resume();
        else {
            version = object.createVersion_();
            int length = mapIndexCount();

            for (int i = 0; i < length; i++) {
                Version current = getGatheredVersion(version.object(), i);

                if (current != null)
                    version.deepCopy(current);
            }

            OverrideAssert.add(this);
            onVisitingVersion(version);
            OverrideAssert.end(this);
        }

        version.visit(this);

        if (interrupted())
            interrupt(version);
    }

    // Debug

    void assertNothingToFlush() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Debug.assertion(_resources.size() == 0);

        for (int i = 0; i < _readCount.length; i++)
            Debug.assertion(_readCount[i] == 0);

        for (int i = 0; i < _writeCount.length; i++)
            Debug.assertion(_writeCount[i] == 0);

        Debug.assertion(mapIndexCount() == 0);
    }
}
