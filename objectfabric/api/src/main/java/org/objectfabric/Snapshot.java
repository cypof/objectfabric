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

final class Snapshot {

    private VersionMap[] _maps;

    private Version[][] _writes;

    private Version[][] _reads; // Can be null

    private SlowChanging _slowChanging;

    static Snapshot createInitial(Workspace workspace) {
        Snapshot snapshot = new Snapshot();

        snapshot._maps = new VersionMap[1];
        snapshot._writes = new Version[1][];

        VersionMap objectsMap = new VersionMap();

        if (Debug.ENABLED)
            Helper.instance().addWatcher(objectsMap, workspace, snapshot, "Initial");

        if (Debug.THREADS)
            ThreadAssert.share(objectsMap);

        snapshot._maps[TransactionManager.OBJECTS_VERSIONS_INDEX] = objectsMap;
        snapshot._writes[TransactionManager.OBJECTS_VERSIONS_INDEX] = TransactionManager.OBJECTS_VERSIONS;

        return snapshot;
    }

    final void copyWithNewSlowChanging(Snapshot newSnapshot, SlowChanging slowChanging) {
        newSnapshot.setVersionMaps(getVersionMaps());
        newSnapshot.writes(writes());
        newSnapshot.setReads(getReads());

        newSnapshot.slowChanging(slowChanging);
    }

    final void trimWithoutReads(Snapshot newSnapshot, SlowChanging slowChanging, int length) {
        VersionMap[] maps = new VersionMap[length];
        Platform.arraycopy(getVersionMaps(), 0, maps, 0, maps.length);
        Version[][] versions = new Version[length][];
        Platform.arraycopy(writes(), 0, versions, 0, versions.length);

        newSnapshot.setVersionMaps(maps);
        // Ignore reads
        newSnapshot.writes(versions);
        newSnapshot.slowChanging(slowChanging);
    }

    final VersionMap[] getVersionMaps() {
        return _maps;
    }

    final void setVersionMaps(VersionMap[] value) {
        _maps = value;
    }

    final Version[][] getReads() {
        int shouldBeNull;
        return _reads;
    }

    final void setReads(Version[][] value) {
        if (Debug.ENABLED) {
            if (value != null) {
                boolean ok = false;

                for (Version[] reads : value)
                    if (reads != null)
                        ok = true;

                Debug.assertion(ok);
            }
        }

        _reads = value;
    }

    final Version[][] writes() {
        return _writes;
    }

    final void writes(Version[][] value) {
        _writes = value;
    }

    final SlowChanging slowChanging() {
        return _slowChanging;
    }

    final void slowChanging(SlowChanging value) {
        _slowChanging = value;
    }

    //

    final VersionMap last() {
        return _maps[_maps.length - 1];
    }

    final int lastIndex() {
        return _maps.length - 1;
    }

    //

    static final class SlowChanging {

        final Actor[] Actors;

        final Extension[] Extensions;

        final Extension[] Splitters;

        // TODO: return null instead of empty instance if no extension
        SlowChanging(Actor[] actors, Extension[] extensions) {
            Actors = actors;
            Extensions = extensions;

            List<Extension> splitters = new List<Extension>();

            if (extensions != null)
                for (Extension extension : extensions)
                    if (extension.splitsSources())
                        splitters.add(extension);

            if (splitters.size() > 0) {
                Splitters = new Extension[splitters.size()];
                splitters.copyToFixed(Splitters);
            } else
                Splitters = null;

            if (Debug.ENABLED) {
                Debug.assertion(Actors == null || Actors.length > 0);
                Debug.assertion(Extensions == null || Extensions.length > 0);
                Debug.assertion(Splitters == null || Splitters.length > 0);
            }
        }
    }

    // Debug

    final void checkInvariants(Workspace workspace) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Debug.assertion(getVersionMaps().length == writes().length);
        Debug.assertion(getReads() == null || getReads().length == writes().length);

        for (int i = 0; i < writes().length; i++) {
            if (writes()[i] == null) {
                Debug.assertion(i == getVersionMaps().length - 1);
                Debug.assertion(getVersionMaps()[i] == VersionMap.CLOSING);
            } else {
                boolean empty = true;

                for (Version version : writes()[i]) {
                    if (version != null) {
                        empty = false;
                        Debug.assertion(version.object().workspace() == workspace);
                    }
                }

                Debug.assertion(i == TransactionManager.OBJECTS_VERSIONS_INDEX || !empty);
            }
        }

        if (getReads() != null) {
            for (Version[] reads : getReads()) {
                if (reads != null) {
                    boolean empty = true;

                    for (Version version : reads) {
                        if (version != null) {
                            empty = false;
                            Debug.assertion(version.object().workspace() == workspace);
                        }
                    }

                    Debug.assertion(!empty);
                }
            }
        }
    }
}
