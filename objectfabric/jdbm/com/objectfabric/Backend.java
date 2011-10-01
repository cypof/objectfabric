/**
 * JDBM LICENSE v1.00
 *
 * Redistribution and use of this software and associated documentation
 * ("Software"), with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * 1. Redistributions of source code must retain copyright
 *    statements and notices.  Redistributions must also contain a
 *    copy of this document.
 *
 * 2. Redistributions in binary form must reproduce the
 *    above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other
 *    materials provided with the distribution.
 *
 * 3. The name "JDBM" must not be used to endorse or promote
 *    products derived from this Software without prior written
 *    permission of Cees de Groot.  For written permission,
 *    please contact cg@cdegroot.com.
 *
 * 4. Products derived from this Software may not be called "JDBM"
 *    nor may "JDBM" appear in their names without prior written
 *    permission of Cees de Groot.
 *
 * 5. Due credit should be given to the JDBM Project
 *    (http://jdbm.sourceforge.net/).
 *
 * THIS SOFTWARE IS PROVIDED BY THE JDBM PROJECT AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL
 * CEES DE GROOT OR ANY CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Copyright 2000 (C) Cees de Groot. All Rights Reserved.
 * Contributions are Copyright (C) 2000 by their associated contributors.
 *
 * $Id: TransactionManager.java,v 1.9 2006/06/03 18:22:46 thompsonbry Exp $
 */

package com.objectfabric;

import java.util.Comparator;

import com.objectfabric.misc.List;

@SuppressWarnings("unchecked")
abstract class Backend {

    private final boolean _disableLogAheadAndSync;

    private RecordFile _recordFile;

    /**
     * In-core copy of transactions. We could read everything back from the log file, but
     * the RecordFile needs to keep the dirty blocks in core anyway, so we might as well
     * point to them and spare us a lot of hassle.
     */
    private final List<BlockIo>[] _transactions;

    private int _currentTx = -1;

    Backend(int logLength, boolean disableLogAheadAndSync) {
        _transactions = new List[logLength];
        _disableLogAheadAndSync = disableLogAheadAndSync;
    }

    //

    abstract void clearLog();

    abstract void close();

    abstract void recover();

    abstract void commit();

    abstract void updateData();

    abstract void writeDirect(BlockIo block);

    abstract void read(BlockIo block);

    //

    final void init(RecordFile value) {
        _recordFile = value;

        recover();
        clearLog();
    }

    final RecordFile getRecordFile() {
        return _recordFile;
    }

    final List<BlockIo>[] getTransactions() {
        return _transactions;
    }

    final int getCurrentTransaction() {
        return _currentTx;
    }

    final boolean logAheadAndSyncDisabled() {
        return _disableLogAheadAndSync;
    }

    /** Discards the indicated blocks and notify the owner. */
    private final void discardBlocks(List<BlockIo> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            BlockIo block = blocks.get(i);
            block.decrementTransactionCount();

            if (!block.isInTransaction())
                _recordFile.releaseFromTransaction(block, false);
        }
    }

    /**
     * Starts a transaction. This can block if all slots have been filled with full
     * transactions, waiting for the synchronization thread to clean out slots.
     */
    final void start() {
        _currentTx++;

        if (_currentTx == _transactions.length) {
            updateData();
            clearLog();
            _currentTx = 0;
        }

        _transactions[_currentTx] = new List<BlockIo>();
    }

    /**
     * Indicates the block is part of the transaction.
     */
    final void add(BlockIo block) {
        block.incrementTransactionCount();
        _transactions[_currentTx].add(block);
    }

    /**
     * Commits the transaction to the log file.
     */
    final void commitToLog() {
        commit();

        List<BlockIo> blocks = _transactions[_currentTx];

        for (int i = 0; i < blocks.size(); i++)
            blocks.get(i).setClean();
    }

    /**
     * Use the disk-based transaction log to synchronize the data file. Outstanding memory
     * logs are discarded because they are believed to be inconsistent.
     */
    final void synchronizeLogFromDisk() {
        for (int i = 0; i < _transactions.length; i++) {
            if (_transactions[i] == null)
                continue;

            discardBlocks(_transactions[i]);
            _transactions[i] = null;
        }

        recover();
        clearLog();
        _currentTx = -1;
    }

    /**
     * Comparator class for use by the tree set used to store the blocks to write for this
     * transaction. The BlockIo objects are ordered by their blockIds.
     */
    static final class BlockIoComparator implements Comparator<BlockIo> {

        public int compare(BlockIo block1, BlockIo block2) {
            int result = 0;

            if (block1.getId() == block2.getId())
                result = 0;
            else if (block1.getId() < block2.getId())
                result = -1;
            else
                result = 1;

            return result;
        }
    }
}
