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
import java.util.concurrent.Executor;

import com.objectfabric.misc.PlatformFile;
import com.objectfabric.misc.PlatformThreadPool;

/**
 * High performance file-based store (Above 50K writes/s and 10K reads/s on a good SSD).
 * This store is based on an a modified version of the JDBM open source persistence engine
 * (http://jdbm.sourceforge.net). It stores objects in a scalable BTree, and uses a
 * write-ahead log mechanism and java.io.FileDescriptor.sync() for transactional
 * consistency. Objects are serialized using the same format as network operations.
 */
public class FileStore extends BinaryStore {

    public static final String LOG_EXTENSION = ".log";

    public FileStore(String file) throws IOException {
        this(file, false);
    }

    /**
     * disableLogAheadAndSync disables the write ahead log mechanism and skip
     * FileDescriptor.sync(). It should only be used when durability and consistency do
     * not matter, e.g. for off-line batch insertions.
     */
    public FileStore(String file, boolean disableLogAheadAndSync) throws IOException {
        this(file, disableLogAheadAndSync, PlatformThreadPool.getInstance());
    }

    /**
     * By default read and write operations are performed on ObjectFabric's thread pool.
     * You can specify another executor.
     */
    public FileStore(String file, boolean disableLogAheadAndSync, Executor executor) throws IOException {
        this(file, disableLogAheadAndSync, executor, false);
    }

    /**
     * terminateProcessOnException kills the process if an exception occurs. On a local
     * disk, the only error that usually occur is running out of disk space. In some cases
     * the only useful reaction is to log the exception and terminate the process.
     */
    public FileStore(String file, boolean disableLogAheadAndSync, Executor executor, boolean terminateProcessOnException) throws IOException {
        super(getBackend(file, disableLogAheadAndSync), true, executor, terminateProcessOnException);
    }

    private static final FileBackend getBackend(String file, boolean disableLogAheadAndSync) throws IOException {
        PlatformFile data = new PlatformFile(file);
        PlatformFile log = new PlatformFile(file + LOG_EXTENSION);
        return new FileBackend(data, log, disableLogAheadAndSync);
    }
}
