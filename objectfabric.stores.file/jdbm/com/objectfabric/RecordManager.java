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
 * Copyright 2000-2001 (C) Alex Boisvert. All Rights Reserved.
 * Contributions are Copyright (C) 2000 by their associated contributors.
 *
 * $Id: BaseRecordManager.java,v 1.12 2006/06/01 13:13:15 thompsonbry Exp $
 */

package com.objectfabric;

import java.util.concurrent.atomic.AtomicBoolean;

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformFile;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

/**
 * This class manages records, which are uninterpreted blobs of data. The set of
 * operations is simple and straightforward: you communicate with the class using long
 * "rowids" and byte[] data blocks. Rowids are returned on inserts and you can stash them
 * away someplace safe to be able to get back to them. Data blocks can be as long as you
 * wish, and may have lengths different from the original when updating.
 * <p>
 * Operations are made atomic by keeping a transaction log which is recovered after a
 * crash, so the operations specified by this interface all have ACID properties.
 * <p>
 * You identify a file by just the name. The package attaches <tt>.db</tt> for the
 * database file, and <tt>.lg</tt> for the transaction log. The transaction log is
 * synchronized regularly and then restarted, so don't worry if you see the size going up
 * and down.
 * 
 * @author <a href="mailto:boisvert@intalio.com">Alex Boisvert</a>
 * @author <a href="cg@cdegroot.com">Cees de Groot</a>
 * @version $Id: BaseRecordManager.java,v 1.12 2006/06/01 13:13:15 thompsonbry Exp $
 */
@SingleThreaded
final class RecordManager {

    /**
     * Underlying record file.
     */
    private RecordFile _file;

    /**
     * Physical row identifier manager.
     */
    private final PhysicalRowIdManager _physMgr;

    /**
     * Logical to Physical row identifier manager.
     */
    private final LogicalRowIdManager _logMgr;

    /**
     * Page manager.
     */
    private final PageManager _pageman;

    private static AtomicBoolean _openned = new AtomicBoolean();

    /**
     * Creates a record manager.
     */
    public RecordManager(PlatformFile file, PlatformFile log) {
        _file = new RecordFile(file, log);
        _pageman = new PageManager(_file);
        _physMgr = new PhysicalRowIdManager(_file, _pageman);
        _logMgr = new LogicalRowIdManager(_file, _pageman);

        if (_openned.getAndSet(true)) {
            /*
             * TODO immutable classes are stored in first store they can, does not work
             * for multiple stores.
             */
            throw new IllegalStateException("for now only one store is allowed at a time");
        }
    }

    /**
     * Switches off transactioning for the record manager. This means that a) a
     * transaction log is not kept, and b) writes aren't synch'ed after every update. This
     * is useful when batch inserting into a new database.
     * <p>
     * Only call this method directly after opening the file, otherwise the results will
     * be undefined.
     */
    public void disableTransactions() {
        checkIfClosed();

        _file.disableTransactions();
    }

    /**
     * Switches off transactioning for the record manager. This means that a) a
     * transaction log is not kept, and b) writes aren't synch'ed after every update. This
     * is useful when batch inserting into a new database.
     * <p>
     * Only call this method directly after opening the file, otherwise the results will
     * be undefined.
     * 
     * @param autoCommitInterval
     *            Specifies the maximum size of the dirty page pool before an auto-commit
     *            is issued
     * @param syncOnClose
     *            Sets whether the underlying record file should sync when it closes if
     *            transactions are disabled.
     */
    public void disableTransactions(int autoCommitInterval) {
        checkIfClosed();

        _file.disableTransactions(autoCommitInterval);
    }

    /**
     * Closes the record manager.
     */
    public void close() {
        close(true);
    }

    public void close(boolean commit) {
        checkIfClosed();

        if (commit)
            commit();

        _file.close();
        _file = null;

        _openned.set(false);
    }

    public long insert(byte[] data) {
        return insert(data, 0, data.length);
    }

    public long insert(byte[] data, int offset, int length) {
        checkIfClosed();

        /*
         * Allocate a physical row and copy the data into that row. Then generate a
         * logical row identifier entry that is mapped to that physical row and return it
         * to the caller.
         */
        Location physRowId = _physMgr.insert(data, offset, length);
        long recid = _logMgr.insert(physRowId).toLong();

        if (Debug.PERSISTENCE_LOG)
            Log.write("BaseRecordManager.insert() recid " + recid + " length " + length);

        return recid;
    }

    /**
     * Deletes a record.
     * 
     * @param recid
     *            the rowid for the record that should be deleted.
     */
    public void delete(long recid) {
        checkIfClosed();

        if (recid < FileHeader.NROOTS)
            throw new IllegalArgumentException("Argument 'recid' is invalid: " + recid);

        Location logRowId = new Location(recid);
        Location physRowId = _logMgr.fetch(logRowId);

        if (physRowId.getBlock() != 0L) {
            // Delete the physical row. (Not done until the physical row has
            // been allocated).
            _physMgr.delete(physRowId);
        }

        _logMgr.delete(logRowId);
    }

    public void update(long recid, byte[] data) {
        update(recid, data, 0, data.length);
    }

    public void update(long recid, byte[] data, int offset, int length) {
        checkIfClosed();

        if (recid < FileHeader.NROOTS)
            throw new IllegalArgumentException("Argument 'recid' is invalid: " + recid);

        Location logRecid = new Location(recid);
        Location physRecid = _logMgr.fetch(logRecid);

        if (Debug.PERSISTENCE_LOG)
            Log.write("BaseRecordManager.update() recid " + recid + " length " + length);

        /*
         * Modified algorithm detects a non-existing physical row from an insert and
         * allocates a physical row. If the physical row exists, then it will be reused if
         * it has sufficient capacity and otherwise reallocated.
         */
        final Location newRecid;

        if (physRecid.getBlock() == 0L) {
            // physical row does not exist (insert as performed by the cache
            // layer defers allocation of the physical record).
            newRecid = _physMgr.insert(data, offset, length);
        } else {
            // physical row exists (record was either inserted by base recman or
            // already updated).
            newRecid = _physMgr.update(physRecid, data, offset, length);
        }

        if (newRecid.getBlock() != physRecid.getBlock() || newRecid.getOffset() != physRecid.getOffset())
            _logMgr.update(logRecid, newRecid);
    }

    /**
     * Fetches a record using standard java object serialization.
     * 
     * @param recid
     *            the recid for the record that must be fetched.
     * @return the object contained in the record.
     */
    public byte[] fetch(long recid) {
        checkIfClosed();

        if (recid < FileHeader.NROOTS)
            throw new IllegalArgumentException("Argument 'recid' is invalid: " + recid);

        /*
         * The logical row identifier (identifies a slot in the translation table).
         */
        Location logRowId = new Location(recid);

        /*
         * Translate the logical record identifier to the physical record identifier and
         * then fetch the record from the page (and the page from the store if necessary).
         */
        Location physRowId = _logMgr.fetch(logRowId);
        byte[] data = _physMgr.fetch(physRowId);

        if (Debug.PERSISTENCE_LOG)
            Log.write("BaseRecordManager.fetch() recid " + recid + " length " + (data != null ? data.length : -1));

        return data;
    }

    /**
     * Returns the number of slots available for "root" rowids. These slots can be used to
     * store special rowids, like rowids that point to other rowids. Root rowids are
     * useful for bootstrapping access to a set of data.
     */
    public int getRootCount() {
        return FileHeader.NROOTS;
    }

    /**
     * Returns the indicated root rowid.
     * 
     * @param id
     *            The root rowid identifier. The value {@link #NAME_DIRECTORY_ROOT} is
     *            reserved. The #of root ids available is reported by #getRootCount().
     * @see #getRootCount()
     */
    public long getRoot(int id) {
        checkIfClosed();

        if (id >= FileHeader.NROOTS)
            throw new IllegalArgumentException("Argument 'id' is invalid: " + id);

        return _pageman.getFileHeader().getRoot(id);
    }

    /**
     * Sets the indicated root rowid.
     * 
     * @see #getRootCount()
     * @see #getRoot(int id )
     */
    public void setRoot(int id, long rowid) {
        checkIfClosed();

        if (id >= FileHeader.NROOTS)
            throw new IllegalArgumentException("Argument 'id' is invalid: " + id);

        _pageman.getFileHeader().setRoot(id, rowid);
    }

    /**
     * Commit (make persistent) all changes since beginning of transaction.
     */
    public void commit() {
        checkIfClosed();

        _pageman.commit();
    }

    /**
     * Rollback (cancel) all changes since beginning of transaction.
     */
    public void rollback() {
        checkIfClosed();

        _pageman.rollback();
    }

    /**
     * Check if RecordManager has been closed. If so, throw an IllegalStateException.
     */
    private void checkIfClosed() throws IllegalStateException {
        if (_file == null)
            throw new IllegalStateException("RecordManager has been closed");
    }
}
