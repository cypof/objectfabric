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

import of4gwt.misc.AtomicLong;

import of4gwt.misc.PlatformAdapter;

public final class Stats {

    // TODO, export to JMX

    public static final boolean ENABLED = false;

    private static final Stats _instance;

    public final AtomicLong Created = new AtomicLong();

    public final AtomicLong Started = new AtomicLong();

    public final AtomicLong Committed = new AtomicLong();

    public final AtomicLong Retried = new AtomicLong();

    public final AtomicLong MaxRetried = new AtomicLong();

    public final AtomicLong LocallyAborted = new AtomicLong();

    public final AtomicLong RemotelyAborted = new AtomicLong();

    public final AtomicLong UserAborted = new AtomicLong();

    public final AtomicLong Merged = new AtomicLong();

    public final AtomicLong MaxMapCount = new AtomicLong();

    public final AtomicLong Put = new AtomicLong();

    public final AtomicLong PutRetry = new AtomicLong();

    public final AtomicLong WrittenUID = new AtomicLong();

    public final AtomicLong CachedUID = new AtomicLong();

    public final AtomicLong SocketWritten = new AtomicLong();

    public final AtomicLong SocketRemaining = new AtomicLong();

    public final AtomicLong FileReadCount = new AtomicLong();

    public final AtomicLong FileWriteCount = new AtomicLong();

    public final AtomicLong FileTotalRead = new AtomicLong();

    public final AtomicLong FileTotalWritten = new AtomicLong();

    public final AtomicLong BTreeLoads = new AtomicLong();

    public final AtomicLong BTreeFetches = new AtomicLong();

    public final AtomicLong BTreePuts = new AtomicLong();

    public final AtomicLong BTreeRemoves = new AtomicLong();

    private Stats() {
        if (!ENABLED)
            throw new IllegalStateException();
    }

    static {
        if (ENABLED)
            _instance = new Stats();
        else
            _instance = null;
    }

    public static Stats getInstance() {
        return _instance;
    }

    public void writeAndReset() {
        writeAndReset(true);
    }

    public void reset() {
        writeAndReset(false);
    }

    private void writeAndReset(boolean write) {
        if (ENABLED) {
            // TODO assert invariants
            PlatformAdapter.writeAndResetAtomicLongs(this, write);
        }
    }
}