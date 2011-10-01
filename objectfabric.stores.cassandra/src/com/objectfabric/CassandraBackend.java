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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.Column;
import org.apache.cassandra.thrift.ColumnOrSuperColumn;
import org.apache.cassandra.thrift.ColumnParent;
import org.apache.cassandra.thrift.ColumnPath;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.Deletion;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.Mutation;
import org.apache.cassandra.thrift.NotFoundException;
import org.apache.cassandra.thrift.SlicePredicate;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFastFramedTransport;
import org.apache.thrift.transport.TSocket;

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.RuntimeIOException;

@SuppressWarnings("unchecked")
final class CassandraBackend extends Backend {

    /**
     * All columns in same family. TODO: make configurable.
     */
    private static final String COLUMN_FAMILY_NAME = "c";

    private static final ColumnParent COLUMN_FAMILY = new ColumnParent(COLUMN_FAMILY_NAME);

    private static final ByteBuffer COLUMN_NAME;

    private final TFastFramedTransport _transport;

    private final Cassandra.Client _cassandra;

    private static final int KEY_HEADER_LENGTH = PlatformAdapter.UID_BYTES_COUNT + 1;

    private static final int LOG_LENGTH_LENGTH = 4;

    private final ByteBuffer _key = ByteBuffer.allocate(PlatformAdapter.UID_BYTES_COUNT + 1 + 8);

    private ByteBuffer _value = ByteBuffer.allocate(LOG_LENGTH_LENGTH + (BlockIo.ID_LENGTH + BlockIo.LENGTH) * 10);

    private final TreeSet<BlockIo> _blockList = new TreeSet<BlockIo>(new BlockIoComparator());

    static {
        byte[] name = "v".getBytes();
        COLUMN_NAME = ByteBuffer.allocate(name.length);
        COLUMN_NAME.put(name);
        COLUMN_NAME.flip();
    }

    CassandraBackend(TSocket socket, boolean disableLogAheadAndSync) {
        super(1, disableLogAheadAndSync);

        _transport = new TFastFramedTransport(socket);
        TProtocol proto = new TBinaryProtocol(_transport);
        _cassandra = new Cassandra.Client(proto);
    }

    public void start(Transaction trunk, RecordManager jdbm) throws TException, InvalidRequestException {
//        Object union = trunk.getSharedVersion_objectfabric().getUnion();
//        Descriptor descriptor;
//
//        if (union instanceof Descriptor)
//            descriptor = (Descriptor) union;
//        else
//            descriptor = trunk.assignId(trunk.getSharedVersion_objectfabric());

        /*
         * One block per row with key = partition (UID + id) + block id. For logs, same
         * header, but using negative block ids. For demo only: all objects stored in same
         * partition for now. Still need to write the index of partitions to get the UID
         * used as the first part of the key.
         */
        // _key.put(descriptor.getSession().getSharedVersion_objectfabric().getUID());
        // _key.put(descriptor.getId());

        // if (Debug.ENABLED)
        // Debug.assertion(_key.position() == KEY_HEADER_LENGTH);

        _transport.open();

        // TODO
        String keyspace = "Keyspace1";
        _cassandra.set_keyspace(keyspace);

        jdbm.start();
    }

    /**
     * Removes write ahead log once updates have been performed. TODO, make asynchronous?
     */
    @Override
    void clearLog() {
        long time = System.currentTimeMillis();

        ArrayList<ByteBuffer> columns = new ArrayList<ByteBuffer>();
        columns.add(COLUMN_NAME);

        SlicePredicate predicate = new SlicePredicate();
        predicate.setColumn_names(columns);

        HashMap<ByteBuffer, Map<String, List<Mutation>>> batch = new HashMap<ByteBuffer, Map<String, List<Mutation>>>(getTransactions().length);

        for (int i = 0; i < getTransactions().length; i++) {
            // TODO: cache
            ByteBuffer key = ByteBuffer.allocate(KEY_HEADER_LENGTH + 8);
            _key.position(0);
            _key.limit(KEY_HEADER_LENGTH);
            key.put(_key);
            _key.flip();
            key.putLong(-i - 1);
            key.flip();

            Deletion deletion = new Deletion();
            deletion.setPredicate(predicate);
            deletion.setTimestamp(time);

            Mutation mutation = new Mutation();
            mutation.setDeletion(deletion);

            ArrayList<Mutation> list = new ArrayList<Mutation>();
            list.add(mutation);

            HashMap<String, List<Mutation>> families = new HashMap<String, List<Mutation>>();
            families.put(COLUMN_FAMILY_NAME, list);
            batch.put(key, families);
        }

        try {
            _cassandra.batch_mutate(batch, ConsistencyLevel.QUORUM);
        } catch (Exception e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    void close() {
        updateData();
        _transport.close();
    }

    /**
     * Recover from log in case previous process crashed before it could update objects
     * and remove the log.
     */
    @SuppressWarnings("unchecked")
    @Override
    void recover() {
        // ArrayList<ByteBuffer> keys = new ArrayList<ByteBuffer>(TXNS_IN_LOG);
        //
        // for (int i = 0; i < TXNS_IN_LOG; i++) {
        // ByteBuffer key = ByteBuffer.allocate(KEY_HEADER_LENGTH + 8);
        // _key.position(0);
        // _key.limit(KEY_HEADER_LENGTH);
        // key.put(_key);
        // _key.flip();
        // key.putLong(-i - 1);
        // keys.add(key);
        // }
        //
        // SlicePredicate predicate = new SlicePredicate();
        // predicate.setSlice_range(new SliceRange(ByteBuffer.wrap(new byte[0]),
        // ByteBuffer.wrap(new byte[0]), false, TXNS_IN_LOG));
        // Map<ByteBuffer, List<ColumnOrSuperColumn>> logs;
        //
        // try {
        // logs = _cassandra.multiget_slice(keys, COLUMN_FAMILY, predicate,
        // ConsistencyLevel.QUORUM);
        // } catch (Exception e) {
        // throw new RuntimeIOException(e);
        // }
        //
        // for (int i = 0; i < TXNS_IN_LOG; i++) {
        // List<ColumnOrSuperColumn> log = logs.get(keys.get(i));
        //
        // if (log.size() > 0) {
        // if (Debug.ENABLED)
        // Debug.assertion(log.size() == 1);
        //
        // ColumnOrSuperColumn column = log.get(0);
        //
        // }
        // }

        // if (_log.length() <= 2)
        // return;
        //
        // _log.setOffset(0);
        //
        // if (_log.readShort() != Magic.LOGFILE_HEADER) {
        // // Write was not complete, stop
        // return;
        // }
        //
        // List<BlockIo> tx = new List<BlockIo>();
        // int txSize = 0;
        //
        // for (;;) {
        // if (_log.remaining() < 4)
        // break;
        //
        // int count = _log.readInt();
        //
        // if (_log.remaining() < count * (8 + RecordFile.BLOCK_SIZE))
        // break;
        //
        // for (int i = 0; i < count; i++) {
        // if (txSize == tx.size()) {
        // BlockIo block = new BlockIo();
        // tx.add(block);
        // }
        //
        // long id = _log.readLong();
        //
        // if (Debug.ENABLED)
        // for (int t = 0; t < txSize; t++)
        // Debug.assertion(tx.get(t).getId() != id);
        //
        // BlockIo block = tx.get(txSize++);
        // block.setId(id);
        // _log.readFull(block.getData());
        // }
        //
        // if (_log.remaining() < 2)
        // break;
        //
        // if (_log.readShort() != Magic.COMMIT_END)
        // break;
        //
        // // Transaction is OK, write to file
        //
        // for (int i = 0; i < count; i++)
        // writeDirect(tx.get(i));
        //
        // txSize = 0;
        // }
        //
        // _data.sync();
    }

    /**
     * Commits a transaction to log.
     */
    @Override
    void commit() {
        _key.position(KEY_HEADER_LENGTH);
        _key.limit(_key.capacity());
        _key.putLong(-getCurrentTransaction() - 1); // Negative ids for logs
        _key.flip();

        if (Debug.ENABLED)
            Debug.assertion(_key.remaining() == _key.capacity());

        com.objectfabric.misc.List<BlockIo> blocks = getTransactions()[getCurrentTransaction()];

        _value.clear();
        _value.putInt(blocks.size());

        if (Debug.ENABLED)
            Debug.assertion(_value.position() == LOG_LENGTH_LENGTH);

        for (int i = 0; i < blocks.size(); i++) {
            if (_value.remaining() < BlockIo.ID_LENGTH + BlockIo.LENGTH) {
                ByteBuffer temp = ByteBuffer.allocate(_value.capacity() + (BlockIo.ID_LENGTH + BlockIo.LENGTH) * 10);
                _value.flip();
                temp.put(_value);
                _value = temp;
            }

            _value.putLong(blocks.get(i).getId());
            _value.put(blocks.get(i).getData());
        }

        _value.flip();

        Column column = new Column(COLUMN_NAME.duplicate());
        column.setValue(_value);
        column.setTimestamp(System.currentTimeMillis());

        try {
            _cassandra.insert(_key, COLUMN_FAMILY, column, ConsistencyLevel.QUORUM);
        } catch (Exception e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Writes updates to actual location of objects once they have been logged.
     */
    @Override
    void updateData() {
        for (int i = 0; i < getTransactions().length; i++) {
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
    }

    /**
     * Write a page. TODO merge with commit().
     */
    @Override
    void writeDirect(BlockIo block) {
        _key.position(KEY_HEADER_LENGTH);
        _key.limit(_key.capacity());
        _key.putLong(block.getId());
        _key.flip();

        if (Debug.ENABLED)
            Debug.assertion(_key.remaining() == _key.capacity());

        _value.clear();
        _value.put(block.getData());
        _value.flip();

        Column column = new Column(COLUMN_NAME.duplicate());
        column.setValue(_value);
        column.setTimestamp(System.currentTimeMillis());

        try {
            _cassandra.insert(_key, COLUMN_FAMILY, column, ConsistencyLevel.QUORUM);
        } catch (Exception e) {
            throw new RuntimeIOException(e);
        }

        if (Debug.ENABLED)
            getRecordFile().incrementWriteBlockCount();
    }

    @Override
    void read(BlockIo block) {
        _key.position(KEY_HEADER_LENGTH);
        _key.limit(_key.capacity());
        _key.putLong(block.getId());
        _key.flip();

        if (Debug.ENABLED)
            Debug.assertion(_key.remaining() == _key.capacity());

        ColumnPath path = new ColumnPath(COLUMN_FAMILY.getColumn_family());
        path.setColumn(COLUMN_NAME);
        ColumnOrSuperColumn column;

        try {
            column = _cassandra.get(_key, path, ConsistencyLevel.QUORUM);
        } catch (NotFoundException e) {
            column = null;
        } catch (Exception e) {
            throw new RuntimeIOException(e);
        }

        if (column != null) {
            System.arraycopy(column.getColumn().getValue(), 0, block.getData(), 0, block.getData().length);

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
