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
        this(file, file + LOG_EXTENSION, false);
    }

    /**
     * Put the log file on another disk for best performance.
     */
    public FileStore(String file, String log) throws IOException {
        this(file, log, false);
    }

    /**
     * disableLogAheadAndSync disables the write ahead log mechanism and skip
     * FileDescriptor.sync(). It should only be used when durability and consistency do
     * not matter, e.g. for off-line batch insertions.
     */
    public FileStore(String file, String log, boolean disableLogAheadAndSync) throws IOException {
        this(file, log, disableLogAheadAndSync, PlatformThreadPool.getInstance());
    }

    /**
     * By default read and write operations are performed on ObjectFabric's thread pool.
     * You can specify another executor.
     */
    public FileStore(String file, String log, boolean disableLogAheadAndSync, Executor executor) throws IOException {
        super(getBackend(file, log, disableLogAheadAndSync), true, executor);
    }

    private static final FileBackend getBackend(String file, String log, boolean disableLogAheadAndSync) throws IOException {
        return new FileBackend(new PlatformFile(file), new PlatformFile(log), disableLogAheadAndSync);
    }
}
