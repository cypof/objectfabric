/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.objectfabric;

import java.io.File;

public class SQLite extends Origin implements URIHandler {

    static {
        JVMPlatform.loadClass();
    }

    private final File _file;

    private final SQLiteQueue _queue;

    private final SQLiteLoop _readers;

    private final SQLiteLoop _writer;

    public SQLite(String path, boolean cache) {
        this(new File(path), cache);
    }

    public SQLite(File file, boolean cache) {
        this(file, cache, 1);
    }

    public SQLite(File file, boolean cache, int readers) {
        super(cache);

        if (Debug.PERSISTENCE_LOG)
            Log.write("SQLite opened");

        _file = file;
        _file.getParentFile().mkdirs();
        _queue = new SQLiteQueue(this);
        _readers = new SQLiteLoop(this, readers, false);
        _writer = new SQLiteLoop(this, 1, true);
    }

    public final File file() {
        return _file;
    }

    public void close() {
        _queue.requestClose(null);
        _readers.close();
        _writer.close();
    }

    // TODO?
    // public void clear() {
    // for (WeakReference<Remote> ref : ClientURIHandler.remotes().values()) {
    // Remote remote = ref.get();
    //
    // if (remote != null)
    // for (URI uri : remote.uris().values())
    // ((ArrayView) uri.getOrCreate(this)).reset();
    // }
    // }

    final SQLiteQueue queue() {
        return _queue;
    }

    final SQLiteLoop readers() {
        return _readers;
    }

    final SQLiteLoop writer() {
        return _writer;
    }

    @Override
    View newView(URI uri) {
        SHA1Digest digest = new SHA1Digest();
        uri.origin().sha1(digest);
        digest.update(uri.path());
        byte[] sha1 = new byte[SHA1Digest.LENGTH];
        digest.doFinal(sha1, 0);
        return new SQLiteView(this, sha1);
    }

    @Override
    Clock newClock(Watcher watcher) {
        return new SQLiteClock(watcher, this);
    }

    @Override
    public URI handle(Address address, String path) {
        return getURI(path);
    }

    @Override
    public String toString() {
        return "SQLite";
    }
}
