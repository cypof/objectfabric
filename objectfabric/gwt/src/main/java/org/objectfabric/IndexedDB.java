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

import java.util.ArrayList;

import com.google.gwt.core.client.JavaScriptObject;

public class IndexedDB extends Origin implements URIHandler {

    static final String DEFAULT_DB = "objectfabric_cache", BLOCKS = "blocks", CLOCKS = "clocks";

    private static final int TICK_LENGTH = 11;

    static {
        GWTPlatform.loadClass();
    }

    private final IndexedDBQueue _queue = new IndexedDBQueue(this);

    private ArrayList<Runnable> _startBacklog = new ArrayList<Runnable>();

    private JavaScriptObject _db;

    public IndexedDB() {
        this(DEFAULT_DB, true);
    }

    public IndexedDB(String name, boolean cache) {
        super(cache);

        if (Debug.PERSISTENCE_LOG)
            Log.write("IndexedDBCache opened");

        open(name, BLOCKS, CLOCKS, this);
    }

    final IndexedDBQueue queue() {
        return _queue;
    }

    final JavaScriptObject db() {
        return _db;
    }

    @Override
    public URI handle(Address address, String path) {
        return getURI(path);
    }

    public static native boolean isSupported() /*-{
    var indexedDB = window.indexedDB || window.webkitIndexedDB || window.mozIndexedDB || window.msIndexedDB;

    if (indexedDB)
      return true;

    return false;
    }-*/;

    private static native void open(String name, String blocks, String clocks, IndexedDB cache) /*-{
    var indexedDB = window.indexedDB || window.webkitIndexedDB || window.mozIndexedDB || window.msIndexedDB;
    window.IDBKeyRange = window.IDBKeyRange || window.webkitIDBKeyRange;

    if (indexedDB) {
      var version = 1;
      var request = indexedDB.open(name, version);

      request.onupgradeneeded = function(e) {
        var db = e.target.result;

        if(!db.objectStoreNames.contains(blocks))
            db.createObjectStore(blocks);

        if(!db.objectStoreNames.contains(clocks))
            db.createObjectStore(clocks);
      }

      request.onsuccess = function(e) {
        var db = e.target.result;

        if (db.version != version && db.setVersion) {
          var vRequest = db.setVersion(v);

          vRequest.onsuccess = function(e) {
            if(!db.objectStoreNames.contains(blocks))
                db.createObjectStore(blocks);
    
            if(!db.objectStoreNames.contains(clocks))
                db.createObjectStore(clocks);

            cache.@org.objectfabric.IndexedDB::onsuccess(Lcom/google/gwt/core/client/JavaScriptObject;)(db);
          };
        }
        else
          cache.@org.objectfabric.IndexedDB::onsuccess(Lcom/google/gwt/core/client/JavaScriptObject;)(db);
      };

      request.onerror = function(event) {
        @org.objectfabric.Log::write(Ljava/lang/String;)("" + request.error);
      };
    } else {
       throw @java.lang.UnsupportedOperationException::new(Ljava/lang/String;)("IndexedDB not supported, test with isSupported() first");
    }
    }-*/;

    final void runWhenStarted(Runnable runnable) {
        _startBacklog.add(runnable);
    }

    private final void onsuccess(JavaScriptObject db) {
        _db = db;

        if (_startBacklog.size() > 0)
            for (Runnable runnable : _startBacklog)
                runnable.run();

        _startBacklog = null;
    }

    @Override
    final View newView(URI uri) {
        SHA1Digest digest = new SHA1Digest();
        uri.origin().sha1(digest);
        digest.update(uri.path());
        ThreadContext thread = ThreadContext.get();
        byte[] sha1 = thread.Sha1;
        digest.doFinal(sha1, 0);
        char[] chars = thread.PathCache;

        for (int i = 0; i < sha1.length;)
            chars[i / 2] = (char) (sha1[i++] << 8 | sha1[i++]);

        return new IndexedDBView(this, new String(chars, 0, SHA1Digest.LENGTH / 2));
    }

    @Override
    Clock newClock(Watcher watcher) {
        return new IndexedDBClock(watcher, this);
    }

    //

    static String getKey(long tick) {
        ThreadContext thread = ThreadContext.get();
        char[] chars = thread.PathCache;

        long time = Tick.time(tick);
        chars[0] = (char) (time >>> 0);
        chars[1] = (char) (time >>> 16);
        chars[2] = (char) (time >>> 32);

        byte[] peer = Peer.get(Tick.peer(tick)).uid();

        for (int i = 0; i < peer.length;)
            chars[3 + i / 2] = (char) (peer[i++] << 8 | (peer[i++] & 0xff));

        String value = new String(chars, 0, TICK_LENGTH);

        if (Debug.ENABLED)
            Debug.assertion(getTick(value) == tick);

        return value;
    }

    static long getTick(String key) {
        long time = 0;
        time |= (long) key.charAt(0) << 0;
        time |= (long) key.charAt(1) << 16;
        time |= (long) key.charAt(2) << 32;

        byte[] peer = new byte[UID.LENGTH];

        for (int i = 0; i < peer.length;) {
            int s = key.charAt(3 + i / 2);
            peer[i++] = (byte) (s >>> 8);
            peer[i++] = (byte) s;
        }

        return Tick.get(Peer.get(new UID(peer)).index(), time);
    }

    //

    /**
     * Clears the cache and deletes backing DB.
     * 
     * @throws RuntimeException
     *             if a file or folder cannot be deleted.
     */
    final void clear() { // TODO
    }

    //

    final JavaScriptObject transaction(boolean both) {
        return both ? transaction(_db, BLOCKS, CLOCKS) : transaction(_db, BLOCKS);
    }

    private final native JavaScriptObject transaction(JavaScriptObject db, String a, String b) /*-{
    return db.transaction([ a, b ], "readwrite");
    }-*/;

    private final native JavaScriptObject transaction(JavaScriptObject db, String store) /*-{
    return db.transaction(store, "readwrite");
    }-*/;

    //

    @Override
    public String toString() {
        return "IndexedDB";
    }
}
