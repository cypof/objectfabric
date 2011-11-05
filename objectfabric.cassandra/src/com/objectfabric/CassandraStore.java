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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.Compression;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

import com.objectfabric.misc.PlatformThreadPool;

/**
 * Stores objects in a Cassandra NoSQL store. (Work in progress)
 */
public class CassandraStore extends BinaryStore {

    public CassandraStore(String host, int port) {
        this(host, port, false, PlatformThreadPool.getInstance());
    }

    public CassandraStore(String host, int port, boolean disableLogAheadAndSync, Executor executor) {
        super(getBackend(host, port, disableLogAheadAndSync), false, executor);
    }

    private static final CassandraBackend getBackend(String host, int port, boolean disableLogAheadAndSync) {
        return new CassandraBackend(new TSocket(host, port), disableLogAheadAndSync);
    }

    public void start(Transaction trunk) throws IOException {
        try {
            ((CassandraBackend) getBackend()).start(trunk, getRecordManager());
        } catch (TException e) {
            throw new IOException(e);
        } catch (InvalidRequestException e) {
            // Requests are hard coded, must not happen
            throw new AssertionError(e);
        }

        start();
    }

    public static final void createNew(String host, int port) throws Exception {
        TTransport tr = new TFramedTransport(new TSocket(host, port));
        TProtocol proto = new TBinaryProtocol(tr);
        Cassandra.Client client = new Cassandra.Client(proto);
        tr.open();
        // String cql =
        // "CREATE keyspace Keyspace1 WITH replication_factor = 1 AND strategy_class = 'SimpleStrategy'";
        String cql = "CREATE KEYSPACE Keyspace1 WITH strategy_class = SimpleStrategy AND strategy_options:replication_factor = 1;";
        client.execute_cql_query(ByteBuffer.wrap(cql.getBytes()), Compression.NONE);
        cql = "USE Keyspace1;";
        client.execute_cql_query(ByteBuffer.wrap(cql.getBytes()), Compression.NONE);
        cql = "CREATE COLUMNFAMILY c (k bytea PRIMARY KEY, v bytea)";
        client.execute_cql_query(ByteBuffer.wrap(cql.getBytes()), Compression.NONE);
        tr.close();
    }
}
