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

package part13.cassandra;

import java.nio.ByteBuffer;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.Compression;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

public class InitCassendra {

    public static void main(String[] args) throws Exception {
        TTransport tr = new TFramedTransport(new TSocket("localhost", 9160));
        TProtocol proto = new TBinaryProtocol(tr);
        Cassandra.Client client = new Cassandra.Client(proto);
        tr.open();
        //String cql = "CREATE keyspace Keyspace1 WITH replication_factor = 1 AND strategy_class = 'SimpleStrategy'";
        String cql = "CREATE KEYSPACE Keyspace1 WITH strategy_class = SimpleStrategy AND strategy_options:replication_factor = 1;";
        client.execute_cql_query(ByteBuffer.wrap(cql.getBytes()), Compression.NONE);
        cql = "USE Keyspace1;";
        client.execute_cql_query(ByteBuffer.wrap(cql.getBytes()), Compression.NONE);
        cql = "CREATE COLUMNFAMILY c (k bytea PRIMARY KEY, v bytea)";
        client.execute_cql_query(ByteBuffer.wrap(cql.getBytes()), Compression.NONE);
        tr.close();
    }
}
