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
 * $Id: RecordFile.java,v 1.13 2006/06/03 18:22:46 thompsonbry Exp $
 */

package com.objectfabric;

import java.util.HashMap;
import java.util.Iterator;

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.RuntimeIOException;

/**
 * This class represents a random access file as a set of fixed size records known as
 * <em>blocks</em> or <em>pages</em>. Each record has a physical record number (
 * <em>blockid</em>), and records are cached in order to improve access. The size of a
 * block is specified by {@link #BLOCK_SIZE}. A block is modeled by {@link BlockIo}which
 * provides an in-memory copy of the state of the block on disk and also maintains some
 * metadata about the state of the block.
 * <p>
 * The {@link RecordFile}uses a write-ahead logging strategy and maintains a separate
 * <em>data file</em> and <em>log file</em>. Dirty pages are accumulated during a
 * transaction and transactions are written to disk by {@link RecordFile#commit()}. The
 * advantage of the write-ahead logging strategy is that writes against the log are
 * continguous and can therefore take advantage of higher potential IO bandwidth. In
 * contrast updates against the data file are random and have higher seek latency and
 * lower potential IO bandwidth.
 * <p>
 * Nothing is written onto the data file until {@link RecordFile#commit()}is invoked. If
 * transactions are disabled, then {@link RecordFile#commit()} causes the dirty pages to
 * be updated directly against the data file. Otherwise the dirty pages constituting the
 * current transaction are written on the log. In either case, the <i>dirty </i> flag for
 * each dirty page is cleared during {@link RecordFile#commit()}and all entries are
 * removed from the dirty list. If a crash occurs while there are transaction(s) on the
 * log, then the state of those transaction(s) is recovered on startup, applied to the
 * data file, and the log file is deleted.
 * <p>
 * From time to time during normal operation the transactions in the log are processed,
 * which involves updating the relevant pages in the data file and deleting the log file.
 * Writes on the data file are aggregated across transactions and ordered in an attempt to
 * minimize redundent updates and improve effective throughput.
 * <p>
 * The <em>free</em> list is an allocation cache for {@link BlockIo}objects. Its size is
 * capped by a configurable parameter.
 * <p>
 * The <em>clean</em> list contains those pages which have been fetched from the store and
 * whose state has NOT been modified. The clean list is implemented as a MRU based on a
 * hash map. Pages that fall off of the end of the clean list are migrated to the "free"
 * list for reuse. The size of the clean list is capped by a configurable parameter.
 * <p>
 * The <em>in-use</em> list contains those pages on which a
 * {@link RecordFile#get(long blockid )}has been performed and which have not been
 * {@link RecordFile#release(BlockIo block) released}. {@link RecordFile#get(long blockid)}
 * returns a {@link BlockIo}. Normally a {@link RecordFile#get(long blockid)}is wrapped in
 * a <code>try</code> block and the <code>finally</code> clause performs the release. As
 * an alternative {@link RecordFile#release(long blockid, boolean isDirty)}is available to
 * indicate that the block is dirty and should be placed onto the <em>dirty</em> list. The
 * in-use list is used to detect "double get"s in an effort to eagerly identify attempts
 * to fetch more than one copy of the same block, which are regarded as logic errors.
 * <p>
 * The <em>dirty</em> list is a list of pages whose state has been updated but not yet
 * written to disk (either to the log if using transactions or to the data file if not
 * using transactions). The dirty list grows during a transaction until
 * {@link RecordFile#commit()}, at which point the dirty pages are accumulated into a
 * transaction which is written onto the log file.
 * <p>
 * The <em>in-txn</em> list is a list of pages within historical transaction(s) that have
 * not yet been updated against the data file. This list is populated each time another
 * transaction is committed by {@link RecordFile#commit()}. The list is cleared only when
 * the log is updated against the data file. However, individual pages are removed from
 * the transaction list by {@link #get(long blockid)}and migrated back to the transaction
 * list by {@link #release(BlockIo block)}as part of an effort to make sure that any given
 * page appears on at most one of the page lists: { free, dirty, in-use, in-transaction}.
 * <p>
 * Some questions:
 * <ul>
 * <li>An iterator is used over the dirty page list during a commit. The javadoc for
 * {@link HashMap} indicates that iterators can be inefficient for a hash map since all
 * hash entries must be visited (even those which are null) and then the chain on each
 * entry must be visited. It would be more efficient to link the pages on the dirty list
 * together so that we could chase those references instead.</li>
 * <li>Based on what I can read into the code, the practice of periodically forcing a
 * commit when transactions are NOT in use means that the dirty pages are flushed to the
 * data file and added to the free list (see my comments on the free list above). Doing
 * this means that you can not rollback the transactions, and in fact an exception is
 * thrown if you try to do this. However, if we were NOT periodically flushing the dirty
 * pages to disk, then you could rollback the transaction since nothing is written onto
 * the disk until {@link RecordFile#commit()}. This goes back to the question about the
 * semantics of the {@link jdbm.RecordManagerOptions#DISABLE_TRANSACTIONS} option. As the
 * code stands, this both disables the write-ahead logging strategy and invalidates the
 * use of rollback. I think that these should be distinct options. I.e., let's define an
 * "auto-commit" option which invalidates rollback once an auto-commit has been performed
 * within a transaction and clarify that the "disable transaction" option disables the
 * write-ahead logging strategy, but the current transaction is still present on the dirty
 * list and may be rolled back unless the auto-commit option has been specified with a
 * non-zero size (in pages) and an auto-commit has been performed during the transaction.
 * In fact, the use of auto-commit with transactions enabled is equally valid since it
 * flushes the dirty pages to the log and migrates them to the in-transaction page list.
 * So, auto-commit would be a memory management option that sacrifices the ability to
 * rollback a transaction once a threshold amount of pages have been buffered in exchange
 * for placing a cap on the size of the dirty list. Note that I think that the auto-commit
 * value should be the size of the dirty list, which has a direct interpretation as N
 * pages worth of heap space. Another drawback is that the code is not capping the size of
 * the free list, so dirty pages without write ahead logging can wind up on the free list
 * instead of being released back to the heap. In addition, the #of transaction buffers
 * would be part of a memory management strategy.</li>
 * <li>{@link com.objectfabric.BlockIo#incrementTransactionCount()}is supposed to snapshot
 * the block (javadoc), but it does not do this and is marked as a "fixme" for alex.</li>
 * <li>Each transaction "buffer" in the {@link Backend}is an array list whose values are
 * the dirty pages (by reference) in that transaction. A new list of the dirty pages is
 * created by {@link Backend#start()}. {@link RecordManager#commit()}scans the dirty page
 * list, removes each dirty page and adds it to the current transaction buffer using
 * {@link Backend#add(BlockIo block)}. {@link RecordManager#commit()} finally invokes
 * {@link Backend#commitToLog()}, which uses default Java serialization (ouch) to write
 * the array list of dirty pages onto the log file. The object output stream and the
 * underlying file output stream are then flushed to the log file which is synched to
 * disk. However, the transaction buffer is NOT discarded, which means that jdbm is
 * holding hard references to all blocks in up to N historical transactions. The
 * references to those blocks held by the transaction buffers are not released until the
 * log is applied to the data file.
 * <p>
 * The comments mention a thread which is responsible for updating the data file from the
 * log, but I can not identify any such thread in jdbm. Instead it appears that
 * {@link Backend#start()}does this synchronously when there are no more transaction
 * buffers, which could result in a large latency for the application. Further, since the
 * transaction buffers are lists of references to the dirty pages, the state of those
 * pages may be more current than their state when the transaction was committed, which
 * means that the synchronization of the data file from the transaction log buffers could
 * be inconsistent (and probably is unless the code manages to ensure that those blocks
 * are NOT also used by the {@link RecordFile}).
 * <p>
 * In fact, {@link RecordFile#commit()}adds dirty pages to the <em>in-transaction</em>
 * list, which is tested by {@link RecordFile#get(long blockid)}. This means that a
 * {@link BlockIo} instance that is part of the transaction log can, in fact, re-enter use
 * within a subsequent transaction. If it is then modified within that subsequent
 * transaction (there is no protection against this), then the transaction isolation has
 * failed and the update from the in memory log buffers to the data file will result in an
 * inconsistent state for the data file. (I think that this could be fixed by a lazy copy
 * of the state of the block based on the transaction counter, which appears to have been
 * the original intention.)
 * <p>
 * There exists code to synchronize the data file from the disk log, and that code is in
 * fact used for recovery on startup. I would suggest that we could create a runtime
 * option to perform synchronization from the on disk log and improve the log file format
 * and serialization to make this more efficient. We could use a new magic number for the
 * new log format or insist that people recover any log files before migrating (or both).
 * <li>
 * <li>Should we offer an option to sync on commit when transactions are disabled?</li>
 * <li>FIXME If there was a crash while writing to the log or if the log file is otherwise
 * corrupt then recovery process will ignore the rest of the log and does NOT issue a
 * warning. I would suggest that we require an explicit option for this behavior
 * ("forceRecovery") and otherwise terminate the application with a warning and without
 * deleting the log file.</li>
 * <li>There is a discussion on the <a
 * href="mailto:jdbm-developer@users.sourceforge.net">developers mailing list </a> in
 * which we are considering replacing the {@link RecordFile}and {@link Backend}with an
 * implementation of DBCache. DBCache is a high-throughput parallel transaction model with
 * a design well suited to the recman layer of jdbm.</li>
 * </ul>
 * 
 * @see Backend
 */
final class RecordFile {

    private final Backend _backend;

    /**
     * Size of the MRU consisting of blocks that are clean (recently read and not modified
     * since). Blocks that fall off of the MRU are moved to the free list or discarded if
     * the free list is full.
     */
    private static final int CLEAN_MRU_SIZE = 1000;

    /**
     * Maximum #of blocks on the "free" list. The free list is an allocation cache for
     * {@link BlockIo} objects.
     */
    private static final int MAX_FREE_SIZE = 1000;

    /**
     * Effects behavior when transactions are enabled and results in eager partial commit
     * of the transaction.
     */
    private static final int MAX_DIRTY_SIZE = 10000;

    /**
     * Allocation cache for {@link BlockIo}.
     */
    private final List<BlockIo> free = new List<BlockIo>();

    /**
     * Blocks that are clean (vs dirty) to avoid re-reads of clean blocks (blocks whose
     * state in current with the state of the block on disk). MRU policy governs ejection
     * of LRU pages from this cache (to the free list or the GC depending on the size of
     * the free list).
     * <P>
     * Note: The {dirty,serializer} metadata maintained by the object CachePolicy are
     * ignored for the purposes of the RecordFile cache.
     */
    private final MRUNativeLong clean;

    /**
     * Blocks currently locked for read/update ops. When released the block goes to the
     * dirty or clean list, depending on a flag. The file header block is normally locked
     * plus the block that is currently being read or modified.
     * 
     * @see BlockIo#isDirty()
     */
    private final LongKeyChainedHashMap<BlockIo> inUse = new LongKeyChainedHashMap<BlockIo>();

    /**
     * Blocks whose state is dirty.
     */
    private final LongKeyChainedHashMap<BlockIo> dirty = new LongKeyChainedHashMap<BlockIo>();

    /**
     * Blocks in a <em>historical</em> transaction(s) that have been written onto the log
     * but which have not yet been committed to the database.
     */
    private final LongKeyChainedHashMap<BlockIo> inTxn = new LongKeyChainedHashMap<BlockIo>();

    /**
     * Creates a new object on the indicated filename. The file is opened in read/write
     * mode.
     * 
     * @param fileName
     *            the name of the file to open or create, without an extension.
     */
    RecordFile(Backend backend) {
        if (backend == null)
            throw new NullPointerException();

        _backend = backend;

        clean = new MRUNativeLong(CLEAN_MRU_SIZE, this);
    }

    void start() {
        _backend.init(this);
    }

    /**
     * Closes file. Uncommitted writes are lost.
     */
    void close() {
        _backend.close();
    }

    /**
     * Gets a block from the file. The returned byte array is the in-memory copy of the
     * record, and thus can be written (and subsequently released with a dirty flag in
     * order to write the block back).
     * 
     * @param blockid
     *            The record number to retrieve.
     */
    BlockIo get(long blockid) {
        long key = blockid;

        // try in transaction list, dirty list, clear, free list
        BlockIo block = inTxn.get(key);

        if (block != null) {
            inTxn.remove(key);
            inUse.put(key, block);
            return block;
        }

        block = dirty.get(key);

        if (block != null) {
            dirty.remove(key);
            inUse.put(key, block);
            return block;
        }

        block = (BlockIo) clean.get(key);

        if (block != null) {
            clean.remove(key);

            if (Debug.ENABLED)
                cleanBlocksHitCount++;

            inUse.put(key, block);
            return block;
        }

        // sanity check: can't be on in use list
        if (Debug.ENABLED)
            Debug.assertion(inUse.get(key) == null);

        // get a new node and read it from the file
        block = getNewNode(blockid);
        _backend.read(block);
        showCounters();
        inUse.put(key, block);
        block.setClean();
        return block;
    }

    /**
     * Releases a block.
     * 
     * @param blockid
     *            The record number to release.
     * @param isDirty
     *            If true, the block was modified since the get().
     */
    void release(long blockid, boolean isDirty) {
        BlockIo node = inUse.get(blockid);

        if (node == null)
            throw new RuntimeIOException("bad blockid " + blockid + " on release");

        if (!node.isDirty() && isDirty)
            node.setDirty();

        release(node);
    }

    /**
     * Releases a block.
     * 
     * @param block
     *            The block to release.
     */
    void release(BlockIo block) {
        long key = block.getId();
        inUse.remove(key);

        if (block.isDirty()) {
            dirty.put(key, block);

            if (_backend.logAheadAndSyncDisabled() && dirty.size() > MAX_DIRTY_SIZE && inUse.isEmpty())
                commit();
        } else {
            if (!_backend.logAheadAndSyncDisabled() && block.isInTransaction())
                inTxn.put(key, block);
            else
                putClean(key, block);
        }
    }

    /**
     * Puts a block on the "clean" MRU. This can cause LRU clean blocks to be ejected to
     * the "free" list.
     * 
     * @param key
     * @param block
     */

    private void putClean(long key, BlockIo block) {
        clean.put(key, block);
    }

    /**
     * Discards a block (will not write the block even if it's dirty)
     * 
     * @param block
     *            The block to discard.
     */
    void discard(BlockIo block) {
        long key = block.getId();
        inUse.remove(key);

        // note: block not added to free list on purpose, because
        // it's considered invalid
    }

    /**
     * Commits the current transaction by flushing all dirty buffers to disk.
     */
    void commit() {
        if (Debug.ENABLED) {
            if (inUse.size() > 1) {
                showList(inUse.values().iterator());
                throw new Error("in use list not empty at commit time (" + inUse.size() + ")");
            }
        }

        if (dirty.size() == 0) {
            // if no dirty blocks, skip commit process
            return;
        }

        if (Debug.ENABLED)
            if (TRIGGER_RATE != 0)
                showCounters(false);

        if (!_backend.logAheadAndSyncDisabled())
            _backend.start();

        for (Iterator<BlockIo> i = dirty.values().iterator(); i.hasNext();) {
            BlockIo node = i.next();
            i.remove();

            if (_backend.logAheadAndSyncDisabled()) {
                _backend.writeDirect(node);

                showCounters();
                node.setClean();
                putClean(node.getId(), node);
                // free.add(node);
            } else {
                // add the page to the transaction buffer.
                _backend.add(node);
                inTxn.put(node.getId(), node);
            }
        }

        if (!_backend.logAheadAndSyncDisabled()) {
            // write the transaction buffer to the log file.
            _backend.commitToLog();
        }

        if (Debug.ENABLED)
            if (TRIGGER_RATE != 0)
                showCounters(true);
    }

    /**
     * Rollback the current transaction by discarding all dirty buffers
     */
    void rollback() {
        if (_backend.logAheadAndSyncDisabled())
            throw new RuntimeIOException("Rollback not allowed if transactions are disabled");

        // debugging...
        if (!inUse.isEmpty()) {
            showList(inUse.values().iterator());
            throw new Error("in use list not empty at rollback time (" + inUse.size() + ")");
        }

        // System.out.println("rollback...");
        dirty.clear();

        _backend.synchronizeLogFromDisk();

        if (!inTxn.isEmpty()) {
            showList(inTxn.values().iterator());
            throw new Error("in txn list not empty at rollback time (" + inTxn.size() + ")");
        }
    }

    /**
     * Prints contents of a list
     */
    private void showList(Iterator<BlockIo> i) {
        int cnt = 0;

        while (i.hasNext()) {
            Log.write("elem " + cnt + ": " + i.next());
            cnt++;
        }
    }

    /**
     * Returns a new node. The node is retrieved (and removed) from the released list or
     * created new.
     */
    private BlockIo getNewNode(long blockid) {
        BlockIo retval = null;

        if (free.size() > 0) {
            retval = free.removeLast();

            if (Debug.ENABLED)
                freeBlocksUsedCount++;
        }

        if (retval == null)
            retval = new BlockIo();

        retval.setId(blockid);
        retval.setView(null);
        return retval;
    }

    /**
     * Releases a node from the transaction list, if it was sitting there.
     * 
     * @param recycle
     *            true if block data can be reused
     */
    void releaseFromTransaction(BlockIo node, boolean recycle) {
        long key = node.getId();

        if ((inTxn.remove(key) != null) && recycle)
            putClean(key, node);
    }

    /**
     * Used to migrate LRU pages from the "clean" list to the "free" list. If the free
     * list reaches capacity, blocks evicted from the "clean" list will be eventually
     * swept by the JVM.
     * 
     * @param obj
     * @throws CacheEvictionException
     */
    public void cacheObjectEvicted(Object obj) {
        /*
         * Note: Eviction notices are fired by the "clean" MRU. Those notices carry
         * additional metadata defined by the CachePolicy interface that are ignored for
         * the purposes of the RecordFile class.
         */
        if (Debug.ENABLED)
            cleanBlocksEvictedCount++;

        if (free.size() < MAX_FREE_SIZE) {
            if (Debug.ENABLED)
                freeBlocksAddedCount++;

            free.add((BlockIo) obj);
        }
    }

    /*
     * Debug
     */

    private long cleanBlocksEvictedCount = 0L;

    private long freeBlocksAddedCount = 0L;

    private long freeBlocksUsedCount = 0L;

    private long cleanBlocksHitCount = 0L;

    private long fetchBlockCount = 0L;

    private long writeBlockCount = 0L;

    private long extendBlockCount = 0L;

    /**
     * When non-zero, the counters are written every N events. Try values of 1000 or so
     * when debugging.
     */
    private static final int TRIGGER_RATE = 0;

    /**
     * Event counter. An event is a fetch (from disk), a write (to disk), or an extend
     * operation (which is only logical and does not touch the disk).
     */
    private long triggerCount = 0L;

    void incrementWriteBlockCount() {
        if (Debug.ENABLED) {
            writeBlockCount++;
            showCounters();
        }
    }

    void incrementFetchBlockCount() {
        if (Debug.ENABLED) {
            fetchBlockCount++;
            showCounters();
        }
    }

    void incrementExtendBlockCount() {
        if (Debug.ENABLED) {
            extendBlockCount++;
            showCounters();
        }
    }

    private void showCounters() {
        if (Debug.ENABLED) {
            triggerCount++;

            if (TRIGGER_RATE != 0 && triggerCount % TRIGGER_RATE == 0)
                showCounters(false);
        }
    }

    /**
     * Writes the current counters and other data of interest.
     * 
     * @param reset
     *            When true, the counters are reset afterwards.
     */

    private void showCounters(boolean reset) {
        if (Debug.ENABLED) {
            // #of blocks held on all lists by the RecordFile (the
            // transaction manager can have more data in buffered
            // transactions).

            long nblocks = inTxn.size() + dirty.size() + inUse.size() + clean.size() + free.size();
            long memused = (nblocks * BlockIo.LENGTH) / (1024 * 1024);

            Log.write("memory used (mb): " + memused);
            Log.write("# blocks in mem : " + nblocks);
            Log.write("# inTxn blocks  : " + inTxn.size());
            Log.write("# dirty blocks  : " + dirty.size());
            Log.write("# inUse blocks  : " + inUse.size());
            Log.write("# fetch blocks  : " + fetchBlockCount);
            Log.write("# write blocks  : " + writeBlockCount);
            Log.write("# extend blocks : " + extendBlockCount);
            Log.write("# clean blocks  : " + clean.size());
            Log.write("# clean hit     : " + cleanBlocksHitCount);
            Log.write("# clean evicted : " + cleanBlocksEvictedCount);
            Log.write("# free blocks   : " + free.size());
            Log.write("# free added    : " + freeBlocksAddedCount);
            Log.write("# free used     : " + freeBlocksUsedCount);
            Log.write("-----------------------------------\n");

            if (reset)
                resetCounters();
        }
    }

    /**
     * Resets the counters (typically done in commit).
     */
    private void resetCounters() {
        if (Debug.ENABLED) {
            cleanBlocksEvictedCount = 0L;
            freeBlocksAddedCount = 0L;
            freeBlocksUsedCount = 0L;
            cleanBlocksHitCount = 0L;
            fetchBlockCount = 0L;
            writeBlockCount = 0L;
            extendBlockCount = 0L;
            triggerCount = 0L;
        }
    }
}
