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

import java.util.TreeSet;

import com.objectfabric.misc.Debug;
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
final class FileBackend extends Backend {

    /**
     * By default, we keep 10 transactions in the log file before synchronizing it with
     * the main database file.
     */
    static final int TXNS_IN_LOG = 10;

    private final PlatformFile _data;

    private final PlatformFile _log;

    private final TreeSet<BlockIo> _blockList = new TreeSet<BlockIo>(new BlockIoComparator());

    FileBackend(PlatformFile data, PlatformFile log, boolean disableLogAheadAndSync) {
        super(TXNS_IN_LOG, disableLogAheadAndSync);

        if (data == null || log == null)
            throw new NullPointerException();

        _data = data;
        _log = log;
    }

    @Override
    void clearLog() {
        _log.setLength(0);
        _log.writeShort(Magic.LOGFILE_HEADER);
        _log.sync();
    }

    @Override
    void close() {
        updateData();
        _data.close();
        _log.close();
    }

    @SuppressWarnings("unchecked")
    @Override
    void recover() {
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

            if (_log.remaining() < count * (8 + BlockIo.LENGTH))
                break;

            for (int i = 0; i < count; i++) {
                if (txSize == tx.size()) {
                    BlockIo block = new BlockIo();
                    tx.add(block);
                }

                long id = _log.readLong();

                if (Debug.ENABLED)
                    for (int t = 0; t < txSize; t++)
                        Debug.assertion(tx.get(t).getId() != id);

                BlockIo block = tx.get(txSize++);
                block.setId(id);
                _log.readFull(block.getData());
            }

            if (_log.remaining() < 2)
                break;

            if (_log.readShort() != Magic.COMMIT_END)
                break;

            // Transaction is OK, write to file

            for (int i = 0; i < count; i++)
                writeDirect(tx.get(i));

            txSize = 0;
        }

        _data.sync();
    }

    @Override
    void commit() {
        List<BlockIo> blocks = getTransactions()[getCurrentTransaction()];
        _log.writeInt(blocks.size());

        for (int i = 0; i < blocks.size(); i++) {
            _log.writeLong(blocks.get(i).getId());
            _log.write(blocks.get(i).getData());
        }

        _log.writeShort(Magic.COMMIT_END);
        _log.sync();
    }

    @Override
    void updateData() {
        for (int i = 0; i < TXNS_IN_LOG; i++) {
            if (getTransactions()[i] == null)
                continue;

            // Add each block to the blockList, replacing the old copy of this
            // block if necessary, thus avoiding writing the same block twice
            for (int t = 0; t < getTransactions()[i].size(); t++) {
                BlockIo block = getTransactions()[i].get(t);

                if (_blockList.contains(block))
                    block.decrementTransactionCount();
                else
                    _blockList.add(block);
            }

            getTransactions()[i] = null;
        }

        for (BlockIo block : _blockList) {
            writeDirect(block);
            block.decrementTransactionCount();

            if (!block.isInTransaction())
                getRecordFile().releaseFromTransaction(block, true);
        }

        _blockList.clear();
        _data.sync();
    }

    @Override
    void writeDirect(BlockIo block) {
        long offset = block.getId() * BlockIo.LENGTH;
        _data.setOffset(offset);
        _data.write(block.getData());

        if (Debug.ENABLED)
            getRecordFile().incrementWriteBlockCount();
    }

    @Override
    void read(BlockIo block) {
        long offset = block.getId() * BlockIo.LENGTH;

        if (offset < _data.length()) {
            _data.setOffset(offset);
            _data.readOrZero(block.getData());

            if (Debug.ENABLED)
                getRecordFile().incrementFetchBlockCount();
        } else {
            for (int i = block.getData().length - 1; i >= 0; i--)
                block.getData()[i] = 0;

            if (Debug.ENABLED)
                getRecordFile().incrementExtendBlockCount();
        }
    }
}
