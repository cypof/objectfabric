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

import java.util.Arrays;


import of4gwt.Acknowledger;
import of4gwt.Connection;
import of4gwt.Extension;
import of4gwt.Interception;
import of4gwt.SourceSplitter;
import of4gwt.Walker;
import of4gwt.TObject.UserTObject;
import of4gwt.TObject.Version;
import of4gwt.Transaction.Granularity;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.ThreadAssert;

final class Snapshot {

    private VersionMap[] _maps;

    private Version[][] _writes;

    private Version[][] _reads; // null if local

    private Interception _interception;

    private int _acknowledged;

    /*
     * TODO bench with 3 levels of change frequencies instead of 2 to remove everything
     * but writes.
     */
    private SlowChanging _slowChanging;

    public Snapshot() {
    }

    public static Snapshot createInitial(Transaction trunk) {
        Snapshot snapshot = new Snapshot();

        snapshot._maps = new VersionMap[1];
        snapshot._writes = new Version[1][];

        VersionMap objectsMap = new VersionMap();

        if (Debug.ENABLED)
            Helper.getInstance().addWatcher(objectsMap, trunk, "Trunk");

        if (Debug.THREADS) {
            ThreadAssert.removePrivate(objectsMap);
            ThreadAssert.addShared(objectsMap);
        }

        snapshot._maps[TransactionManager.OBJECTS_VERSIONS_INDEX] = objectsMap;
        snapshot._writes[TransactionManager.OBJECTS_VERSIONS_INDEX] = TransactionManager.OBJECTS_VERSIONS;
        snapshot._acknowledged = TransactionManager.OBJECTS_VERSIONS_INDEX;

        return snapshot;
    }

    public void copyWithNewSlowChanging(Snapshot newSnapshot, SlowChanging slowChanging) {
        newSnapshot.setVersionMaps(getVersionMaps());
        newSnapshot.setWrites(getWrites());
        newSnapshot.setReads(getReads());
        newSnapshot.setInterception(getInterception());
        newSnapshot.setAcknowledgedIndex(getAcknowledgedIndex());

        newSnapshot.setSlowChanging(slowChanging);
    }

    public void trimWithoutReads(Snapshot newSnapshot, Interception interception, SlowChanging slowChanging, int length) {
        VersionMap[] maps = new VersionMap[length];
        PlatformAdapter.arraycopy(getVersionMaps(), 0, maps, 0, maps.length);
        Version[][] versions = new Version[length][];
        PlatformAdapter.arraycopy(getWrites(), 0, versions, 0, versions.length);

        newSnapshot.setVersionMaps(maps);
        // Ignore reads
        newSnapshot.setWrites(versions);
        newSnapshot.setAcknowledgedIndex(getAcknowledgedIndex());
        newSnapshot.setInterception(interception);
        newSnapshot.setSlowChanging(slowChanging);
    }

    public VersionMap[] getVersionMaps() {
        return _maps;
    }

    public void setVersionMaps(VersionMap[] value) {
        _maps = value;
    }

    public Version[][] getReads() {
        return _reads;
    }

    public void setReads(Version[][] value) {
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

    public Version[][] getWrites() {
        return _writes;
    }

    public void setWrites(Version[][] value) {
        _writes = value;
    }

    public Interception getInterception() {
        return _interception;
    }

    public void setInterception(Interception interception) {
        _interception = interception;
    }

    public int getAcknowledgedIndex() {
        return _acknowledged;
    }

    public void setAcknowledgedIndex(int value) {
        _acknowledged = value;
    }

    public SlowChanging getSlowChanging() {
        return _slowChanging;
    }

    public void setSlowChanging(SlowChanging value) {
        _slowChanging = value;
    }

    //

    public VersionMap getLast() {
        return _maps[_maps.length - 1];
    }

    public int getLastIndex() {
        return _maps.length - 1;
    }

    public VersionMap getAcknowledged() {
        return _maps[_acknowledged];
    }

    //

    public static final class SlowChanging {

        public static final Connection.Version[] BLOCKED_AS_BRANCH_DISCONNECTED = new Connection.Version[0];

        private final Extension[] _extensions;

        private final Acknowledger[] _acknowledgers;

        private final Walker[] _walkers;

        private final Walker[] _noMergeWalkers;

        private final SourceSplitter[] _splitters;

        /**
         * When a distributed transaction aborts, all subsequent transactions sent by a
         * connection must be aborted until the connection has acknowledged the abort and
         * restarted a series.
         */
        private final Connection.Version[] _blocked; // TODO list -> map?

        // TODO: return null instead of empty instance if no extension
        public SlowChanging(Transaction branch, Extension[] extensions, Connection.Version[] blocked) {
            _extensions = extensions;

            List<Acknowledger> acknowledgers = new List<Acknowledger>();
            List<Walker> walkers = new List<Walker>();
            List<Walker> noMergeWalkers = new List<Walker>();
            List<SourceSplitter> splitters = new List<SourceSplitter>();

            if (extensions != null) {
                for (Extension extension : extensions) {
                    if (extension instanceof Acknowledger)
                        acknowledgers.add((Acknowledger) extension);

                    if (extension instanceof Walker) {
                        Walker walker = (Walker) extension;
                        walkers.add(walker);

                        if (walker.getGranularity(branch) == Granularity.ALL)
                            noMergeWalkers.add(walker);
                    }

                    if (extension instanceof SourceSplitter)
                        splitters.add((SourceSplitter) extension);
                }
            }

            if (acknowledgers.size() > 0) {
                _acknowledgers = new Acknowledger[acknowledgers.size()];
                acknowledgers.copyToFixed(_acknowledgers);
            } else
                _acknowledgers = null;

            if (walkers.size() > 0) {
                _walkers = new Walker[walkers.size()];
                walkers.copyToFixed(_walkers);
            } else
                _walkers = null;

            if (noMergeWalkers.size() > 0) {
                _noMergeWalkers = new Walker[noMergeWalkers.size()];
                noMergeWalkers.copyToFixed(_noMergeWalkers);
            } else
                _noMergeWalkers = null;

            if (splitters.size() > 0) {
                _splitters = new SourceSplitter[splitters.size()];
                splitters.copyToFixed(_splitters);
            } else
                _splitters = null;

            _blocked = blocked;

            if (Debug.ENABLED) {
                Debug.assertion(_extensions == null || _extensions.length > 0);
                Debug.assertion(_acknowledgers == null || _acknowledgers.length > 0);
                Debug.assertion(_noMergeWalkers == null || _noMergeWalkers.length > 0);
                Debug.assertion(_splitters == null || _splitters.length > 0);
                Debug.assertion(_blocked == null || _blocked.length > 0 || _blocked == BLOCKED_AS_BRANCH_DISCONNECTED);
            }
        }

        public SlowChanging(SlowChanging model, Connection.Version[] blocked) {
            _extensions = model._extensions;
            _acknowledgers = model._acknowledgers;
            _walkers = model._walkers;
            _noMergeWalkers = model._noMergeWalkers;
            _splitters = model._splitters;

            _blocked = blocked;
        }

        public Extension[] getExtensions() {
            return _extensions;
        }

        public Acknowledger[] getAcknowledgers() {
            return _acknowledgers;
        }

        public Walker[] getWalkers() {
            return _walkers;
        }

        public Walker[] getNoMergeWalkers() {
            return _noMergeWalkers;
        }

        public SourceSplitter[] getSourceSplitters() {
            return _splitters;
        }

        //

        public Connection.Version[] getBlocked() {
            return _blocked;
        }

        public SlowChanging block(Connection.Version blocked) {
            if (_blocked == null)
                return new SlowChanging(this, new Connection.Version[] { blocked });

            if (Debug.ENABLED)
                Debug.assertion(!Arrays.asList(_blocked).contains(blocked));

            Connection.Version[] array = new Connection.Version[_blocked.length + 1];
            PlatformAdapter.arraycopy(_blocked, 0, array, 0, _blocked.length);
            array[array.length - 1] = blocked;
            return new SlowChanging(this, array);
        }

        public SlowChanging block(List<Connection.Version> blocked) {
            if (_blocked == null) {
                Connection.Version[] array = new Connection.Version[blocked.size()];
                blocked.copyToFixed(array);
                return new SlowChanging(this, array);
            }

            if (Debug.ENABLED)
                Debug.assertion(!Arrays.asList(_blocked).contains(blocked));

            Connection.Version[] array = new Connection.Version[_blocked.length + blocked.size()];
            PlatformAdapter.arraycopy(_blocked, 0, array, 0, _blocked.length);

            for (int i = 0; i < blocked.size(); i++)
                array[array.length - 1 + i] = blocked.get(i);

            return new SlowChanging(this, array);
        }

        public boolean isBlocked(Connection.Version connection) {
            if (_blocked != null)
                for (int i = _blocked.length - 1; i >= 0; i--)
                    if (_blocked[i] == connection)
                        return true;

            return false;
        }

        public SlowChanging unblock(Connection.Version blocked) {
            if (Debug.ENABLED)
                Debug.assertion(Arrays.asList(_blocked).contains(blocked));

            if (_blocked.length == 1)
                return new SlowChanging(this, (Connection.Version[]) null);

            Connection.Version[] array = new Connection.Version[_blocked.length - 1];
            int j = 0;

            for (int i = 0; i < _blocked.length; i++)
                if (_blocked[i] != blocked)
                    array[j++] = _blocked[i];

            if (Debug.ENABLED)
                Debug.assertion(j == array.length);

            return new SlowChanging(this, array);
        }
    }

    // Debug

    public void checkInvariants(Transaction branch) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Debug.assertion(getVersionMaps().length == getWrites().length);
        Debug.assertion(getReads() == null || getReads().length == getWrites().length);

        for (int i = getAcknowledgedIndex() + 1; i < getVersionMaps().length; i++) {
            for (int j = getAcknowledgedIndex() + 1; j < getVersionMaps().length; j++) {
                VersionMap a = getVersionMaps()[i];
                VersionMap b = getVersionMaps()[j];

                if (a.getInterception() != null && b.getInterception() != null && a.getInterception() != b.getInterception()) {
                    if (a.getInterception().getId() == b.getInterception().getId()) {
                        boolean aDone = a.getInterception().getAsync().isDone();
                        boolean bDone = b.getInterception().getAsync().isDone();
                        Debug.assertion(aDone || bDone);
                    }
                }
            }
        }

        boolean empty = true;

        for (Version[] writes : getWrites()) {
            for (Version version : writes) {
                if (version != null) {
                    empty = false;
                    UserTObject object = version.getShared().getReference().get();

                    if (object != null)
                        Debug.assertion(object.getTrunk() == branch);
                }
            }
        }

        Debug.assertion(getWrites().length == 1 || !empty);

        if (getReads() != null) {
            empty = true;

            for (Version[] reads : getReads()) {
                if (reads != null) {
                    for (Version version : reads) {
                        if (version != null) {
                            empty = false;
                            UserTObject object = version.getShared().getReference().get();

                            if (object != null)
                                Debug.assertion(object.getTrunk() == branch);
                        }
                    }
                }
            }

            Debug.assertion(!empty);
        }
    }
}
