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

import java.util.concurrent.atomic.AtomicLong;

final class Stats {

    // TODO export to JMX?

    public static final boolean ENABLED = false;

    static final Stats Instance;

    public final AtomicLong Created = new AtomicLong();

    public final AtomicLong Started = new AtomicLong();

    public final AtomicLong Committed = new AtomicLong();

    public final AtomicLong Aborted = new AtomicLong();

    public final AtomicLong ValidationRetries = new AtomicLong();

    public final AtomicLong ValidationRetriesMax = new AtomicLong();

    public final AtomicLong TransactionRetries = new AtomicLong();

    public final AtomicLong TransactionRetriesMax = new AtomicLong();

    public final AtomicLong Merged = new AtomicLong();

    public final AtomicLong MaxMapCount = new AtomicLong();

    public final AtomicLong Put = new AtomicLong();

    public final AtomicLong PutRetry = new AtomicLong();

    public final AtomicLong BlockCreated = new AtomicLong();

    public final AtomicLong BlockOverwritten = new AtomicLong();

    public final AtomicLong BlockReceived = new AtomicLong();

    public final AtomicLong BlockRequestsSent = new AtomicLong();

    public final AtomicLong BlockRequestsReceived = new AtomicLong();

    public final AtomicLong AckCreated = new AtomicLong();

    public final AtomicLong AckReceived = new AtomicLong();

    public final AtomicLong BuffCount = new AtomicLong();

    public final AtomicLong ConnectionQueues = new AtomicLong();

    public final AtomicLong BlockQueues = new AtomicLong();

    public final AtomicLong MemoryBlocksCreated = new AtomicLong();

    public final AtomicLong MemoryBlocksLive = new AtomicLong();

    public final AtomicLong FileListCount = new AtomicLong();

    public final AtomicLong FileReadCount = new AtomicLong();

    public final AtomicLong FileReadBytes = new AtomicLong();

    public final AtomicLong FileWriteCount = new AtomicLong();

    public final AtomicLong FileWriteBytes = new AtomicLong();

    public final AtomicLong NativeAllocations = new AtomicLong();

    private Stats() {
        if (!ENABLED)
            throw new IllegalStateException();
    }

    static {
        if (ENABLED)
            Instance = new Stats();
        else
            Instance = null;
    }

    public void writeAndReset() {
        writeAndReset(true);
    }

    public void reset() {
        writeAndReset(false);
    }

    private void writeAndReset(boolean write) {
        if (ENABLED && Platform.get().value() != Platform.GWT) {
            // TODO assert invariants
            Platform.get().writeAndResetAtomicLongs(this, write);
        }
    }
}