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

package com.objectfabric;

import com.objectfabric.VersionMap.Source;
import com.objectfabric.misc.Debug;

/**
 * Prevents merge of maps with different sources.
 */
final class SourceSplitter extends Extension<SourceSplitter.State> {

    protected static final class State {

        public Connection.Version Connection;

        public VersionMap Map;
    }

    @Override
    protected boolean requestRun() {
        // Called on registering a branch, ignore
        return false;
    }

    @Override
    boolean casSnapshotWithThis(Transaction branch, Snapshot snapshot, Snapshot newSnapshot) {
        State state = new State();
        int index = -2;

        if (snapshot.getVersionMaps()[0].tryToAddWatchers(1)) {
            if (Debug.ENABLED)
                Helper.getInstance().addWatcher(snapshot.getVersionMaps()[0], state, "casSnapshotWithThis");

            for (index = snapshot.getVersionMaps().length - 2; index >= 0; index--) {
                VersionMap map = snapshot.getVersionMaps()[index];
                VersionMap next = snapshot.getVersionMaps()[index + 1];

                Connection.Version a = map.getSource() != null ? map.getSource().Connection : null;
                Connection.Version b = next.getSource() != null ? next.getSource().Connection : null;

                if (a != b) {
                    if (!map.tryToAddWatchers(1))
                        break;

                    if (Debug.ENABLED)
                        Helper.getInstance().addWatcher(map, this, "casSnapshotWithThis" + index);
                }
            }
        }

        if (index == -1 && super.casSnapshotWithThis(branch, snapshot, newSnapshot)) {
            Source source = snapshot.getVersionMaps()[0].getSource();
            state.Connection = source != null ? source.Connection : null;
            state.Map = snapshot.getVersionMaps()[0];
            put(branch, state);
            return true;
        }

        // TODO: write register/unregister multi-threaded loop to test this

        if (index != -2) {
            if (Debug.ENABLED)
                Helper.getInstance().removeWatcher(snapshot.getVersionMaps()[0], state, "casSnapshotWithThis failed");

            snapshot.getVersionMaps()[0].removeWatchers(branch, 1, false, null);

            for (int i = snapshot.getVersionMaps().length - 2; i > index; i--) {
                VersionMap map = snapshot.getVersionMaps()[i];
                VersionMap next = snapshot.getVersionMaps()[i + 1];

                Connection.Version a = map.getSource() != null ? map.getSource().Connection : null;
                Connection.Version b = next.getSource() != null ? next.getSource().Connection : null;

                if (a != b) {
                    if (Debug.ENABLED)
                        Helper.getInstance().removeWatcher(map, this, "casSnapshotWithThis failed" + i);

                    map.removeWatchers(branch, 1, false, null);
                }
            }
        }

        return false;
    }

    @Override
    boolean casSnapshotWithoutThis(Transaction branch, Snapshot snapshot, Snapshot newSnapshot, Exception exception) {
        if (super.casSnapshotWithoutThis(branch, snapshot, newSnapshot, exception)) {
            State state = get(branch);

            int index = Helper.getIndex(snapshot, state.Map);
            Connection.Version a = state.Connection;

            if (Debug.ENABLED)
                Debug.assertion(index >= 0);

            for (int i = index + 1; i < snapshot.getVersionMaps().length; i++) {
                VersionMap map = snapshot.getVersionMaps()[i];
                Connection.Version b = map.getSource() != null ? map.getSource().Connection : null;

                if (a != b) {
                    VersionMap barrier = snapshot.getVersionMaps()[i - 1];

                    if (Debug.ENABLED)
                        Helper.getInstance().removeWatcher(barrier, this, "casSnapshotWithoutThis sources " + i);

                    barrier.removeWatchers(branch, 1, false, null);
                    a = b;
                }
            }

            if (Debug.ENABLED)
                Helper.getInstance().removeWatcher(state.Map, state, "casSnapshotWithoutThis");

            state.Map.removeWatchers(branch, 1, false, null);
            return true;
        }

        return false;
    }

    public final void transferWatch(Transaction branch, Snapshot snapshot, int mapIndex, Object currentWatcher) {
        State state = get(branch);

        if (state == null) {
            register(branch);
            state = get(branch);
        }

        if (Debug.ENABLED) {
            VersionMap map = snapshot.getVersionMaps()[mapIndex];
            Helper.getInstance().removeWatcher(map, currentWatcher, "SourceSplitter.transferWatch");
            Helper.getInstance().addWatcher(map, state, "SourceSplitter.transferWatch");
        }

        int index = Helper.getIndex(snapshot, state.Map);

        if (Debug.ENABLED) {
            Debug.assertion(index >= 0);
            Debug.assertion(index <= mapIndex);
        }

        if (index < mapIndex) {
            for (int i = index + 1; i <= mapIndex; i++) {
                VersionMap map = snapshot.getVersionMaps()[i];
                Connection.Version connection = map.getSource() != null ? map.getSource().Connection : null;

                if (connection != state.Connection) {
                    VersionMap previous = snapshot.getVersionMaps()[i - 1];

                    if (Debug.ENABLED)
                        Helper.getInstance().removeWatcher(previous, this, "SourceSplitter.transferWatch 2");

                    previous.removeWatchers(branch, 1, false, null);
                    state.Connection = connection;
                }
            }

            state.Map = snapshot.getVersionMaps()[mapIndex];
        }

        if (Debug.ENABLED)
            Helper.getInstance().removeWatcher(snapshot.getVersionMaps()[index], state, "SourceSplitter.transferWatch 3");

        snapshot.getVersionMaps()[index].removeWatchers(branch, 1, false, null);
    }

    @Override
    protected boolean isUpToDate(Transaction branch, State value) {
        throw new IllegalStateException();
    }
}
