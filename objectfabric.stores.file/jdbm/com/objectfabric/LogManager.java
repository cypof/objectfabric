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
import java.util.TreeSet;

import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformFile;

/**
 * This class manages the transaction log that belongs to every {@link RecordFile}. The
 * transaction log is either clean, or in progress. In the latter case, the transaction
 * manager takes care of a roll forward.
 * <p>
 * Implementation note: this is a proof-of-concept implementation which hasn't been
 * optimized for speed. For instance, all sorts of streams are created for every
 * transaction.
 */
@SuppressWarnings("unchecked")
final class LogManager {

    private RecordFile _owner;

    private final PlatformFile _log;

    /**
     * By default, we keep 10 transactions in the log file before synchronizing it with
     * the main database file.
     */
    static final int TXNS_IN_LOG = 10;

    /**
     * In-core copy of transactions. We could read everything back from the log file, but
     * the RecordFile needs to keep the dirty blocks in core anyway, so we might as well
     * point to them and spare us a lot of hassle.
     */
    private final List<BlockIo>[] txns = new List[TXNS_IN_LOG];

    private int curTxn = -1;

    /**
     * Instantiates a transaction manager instance. If recovery needs to be performed, it
     * is done.
     * 
     * @param owner
     *            the RecordFile instance that owns this transaction mgr.
     */
    LogManager(RecordFile owner, PlatformFile log) {
        if (log == null)
            throw new NullPointerException();

        _owner = owner;
        _log = log;

        recover();
        init();
    }

    private void init() {
        _log.setLength(0);
        _log.writeShort(Magic.LOGFILE_HEADER);
        _log.sync();

        curTxn = -1;
    }

    /**
     * Synchronize log file data with the main database file.
     * <p>
     * After this call, the main database file is guaranteed to be consistent and
     * guaranteed to be the only file needed for backup purposes.
     */
    public void synchronizeLog() {
        synchronizeLogFromMemory();
    }

    /** Write in-core transactions to data file and clears log */
    private void synchronizeLogFromMemory() {
        TreeSet<BlockIo> blockList = new TreeSet<BlockIo>(new BlockIoComparator());

        for (int i = 0; i < TXNS_IN_LOG; i++) {
            if (txns[i] == null)
                continue;

            // Add each block to the blockList, replacing the old copy of this
            // block if necessary, thus avoiding writing the same block twice
            for (int t = 0; t < txns[i].size(); t++) {
                BlockIo block = txns[i].get(t);

                if (blockList.contains(block))
                    block.decrementTransactionCount();
                else
                    blockList.add(block);
            }

            txns[i] = null;
        }

        for (BlockIo block : blockList) {
            _owner.write(block);
            block.decrementTransactionCount();

            if (!block.isInTransaction())
                _owner.releaseFromTransaction(block, true);
        }

        _owner.sync();

        init();
    }

    /** Startup recovery on all files */
    @SuppressWarnings("unchecked")
    private void recover() {
        if (_log.length() <= 2)
            return;

        _log.setOffset(0);

        if (_log.readShort() != Magic.LOGFILE_HEADER) {
            // Write was not complete, stop
            return;
        }

        List<BlockIo> tx = new List<BlockIo>();
        int txSize = 0;

        for (;;) {
            if (_log.remaining() < 4)
                break;

            int count = _log.readInt();

            if (_log.remaining() < count * (8 + RecordFile.BLOCK_SIZE))
                break;

            for (int i = 0; i < count; i++) {
                if (txSize == tx.size()) {
                    BlockIo block = new BlockIo();
                    tx.add(block);
                }

                BlockIo block = tx.get(txSize++);
                block.setId(_log.readLong());
                _log.readFull(block.getData());
            }

            if (_log.remaining() < 2)
                break;

            if (_log.readShort() != Magic.COMMIT_END)
                break;

            // Transaction is OK, write to file

            for (int i = 0; i < count; i++)
                _owner.write(tx.get(i));

            txSize = 0;
        }

        _owner.sync();
    }

    /** Set clean flag on the blocks. */
    private void setClean(List<BlockIo> blocks) {
        for (int i = 0; i < blocks.size(); i++)
            blocks.get(i).setClean();
    }

    /** Discards the indicated blocks and notify the owner. */
    private void discardBlocks(List<BlockIo> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            BlockIo block = blocks.get(i);
            block.decrementTransactionCount();

            if (!block.isInTransaction())
                _owner.releaseFromTransaction(block, false);
        }
    }

    /**
     * Starts a transaction. This can block if all slots have been filled with full
     * transactions, waiting for the synchronization thread to clean out slots.
     */
    void start() {
        curTxn++;

        if (curTxn == TXNS_IN_LOG) {
            synchronizeLogFromMemory();
            curTxn = 0;
        }

        txns[curTxn] = new List<BlockIo>();
    }

    /**
     * Indicates the block is part of the transaction.
     */
    void add(BlockIo block) {
        block.incrementTransactionCount();
        txns[curTxn].add(block);
    }

    /**
     * Commits the transaction to the log file.
     */
    void commit() {
        _log.writeInt(txns[curTxn].size());

        for (int i = 0; i < txns[curTxn].size(); i++) {
            _log.writeLong(txns[curTxn].get(i).getId());
            _log.write(txns[curTxn].get(i).getData());
        }

        _log.writeShort(Magic.COMMIT_END);
        _log.sync();

        // set clean flag to indicate blocks have been written to log
        setClean(txns[curTxn]);
    }

    /**
     * Shutdowns the transaction manager. Uncommitted writes are lost.
     */
    void close() {
        _log.close();
    }

    /**
     * Use the disk-based transaction log to synchronize the data file. Outstanding memory
     * logs are discarded because they are believed to be inconsistent.
     */
    void synchronizeLogFromDisk() {
        for (int i = 0; i < TXNS_IN_LOG; i++) {
            if (txns[i] == null)
                continue;

            discardBlocks(txns[i]);
            txns[i] = null;
        }

        recover();
        init();
    }

    /**
     * Comparator class for use by the tree set used to store the blocks to write for this
     * transaction. The BlockIo objects are ordered by their blockIds.
     */
    private static final class BlockIoComparator implements Comparator<BlockIo> {

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
