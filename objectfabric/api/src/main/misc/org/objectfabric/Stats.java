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

    static final boolean ENABLED = false;

    static final Stats Instance;

    final AtomicLong Created = new AtomicLong();

    final AtomicLong Started = new AtomicLong();

    final AtomicLong Committed = new AtomicLong();

    final AtomicLong Aborted = new AtomicLong();

    final AtomicLong ValidationRetries = new AtomicLong();

    final AtomicLong ValidationRetriesMax = new AtomicLong();

    final AtomicLong TransactionRetries = new AtomicLong();

    final AtomicLong TransactionRetriesMax = new AtomicLong();

    final AtomicLong Merged = new AtomicLong();

    final AtomicLong MaxMapCount = new AtomicLong();

    final AtomicLong Put = new AtomicLong();

    final AtomicLong PutRetry = new AtomicLong();

    final AtomicLong BlockCreated = new AtomicLong();

    final AtomicLong BlockOverwritten = new AtomicLong();

    final AtomicLong BlockReceived = new AtomicLong();

    final AtomicLong BlockRequestsSent = new AtomicLong();

    final AtomicLong BlockRequestsReceived = new AtomicLong();

    final AtomicLong AckCreated = new AtomicLong();

    final AtomicLong AckReceived = new AtomicLong();

    final AtomicLong BuffCount = new AtomicLong();

    final AtomicLong ConnectionQueues = new AtomicLong();

    final AtomicLong BlockQueues = new AtomicLong();

    final AtomicLong MemoryBlocksCreated = new AtomicLong();

    final AtomicLong MemoryBlocksLive = new AtomicLong();

    final AtomicLong BlockListCount = new AtomicLong();

    final AtomicLong BlockReadCount = new AtomicLong();

    final AtomicLong BlockWriteCount = new AtomicLong();

    final AtomicLong BlockMaxBytes = new AtomicLong();

    final AtomicLong NativeAllocations = new AtomicLong();

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

    static void max(AtomicLong field, long value) {
        for (;;) {
            long max = field.get();

            if (value <= max)
                break;

            if (field.compareAndSet(max, value))
                break;
        }
    }

    final void writeAndReset() {
        writeAndReset(true);
    }

    final void reset() {
        writeAndReset(false);
    }

    private void writeAndReset(boolean write) {
        if (ENABLED && Platform.get().value() != Platform.GWT) {
            // TODO assert invariants
            Platform.get().writeAndResetAtomicLongs(this, write);
        }
    }
}