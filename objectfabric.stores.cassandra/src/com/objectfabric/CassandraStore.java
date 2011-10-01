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

import java.util.concurrent.Executor;

import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TSocket;

import com.objectfabric.misc.PlatformThreadPool;

/**
 * Stores objects in a Cassandra NoSQL store. (Work in progress)
 */
public class CassandraStore extends BinaryStore {

    public CassandraStore(TSocket socket) {
        this(socket, false, PlatformThreadPool.getInstance());
    }

    public CassandraStore(TSocket socket, boolean disableLogAheadAndSync, Executor executor) {
        super(getBackend(socket, disableLogAheadAndSync), false, executor, false);
    }

    private static final CassandraBackend getBackend(TSocket socket, boolean disableLogAheadAndSync) {
        return new CassandraBackend(socket, disableLogAheadAndSync);
    }

    public void start(Transaction trunk) throws TException, InvalidRequestException {
        ((CassandraBackend) getBackend()).start(trunk, getRecordManager());
        start();
    }
}
