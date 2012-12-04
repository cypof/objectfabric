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

import org.objectfabric.Actor.Message;
import org.objectfabric.Resource.NewBlock;

import com.google.gwt.core.client.JavaScriptObject;

final class IndexedDBClock extends Clock {

    private final IndexedDB _location;

    private JavaScriptObject _transaction;

    IndexedDBClock(Watcher watcher, IndexedDB location) {
        super(watcher);

        _location = location;
    }

    @Override
    void writing(Resources resources) {
        long[][] loaded = new long[resources.size()][];

        for (int i = 0; i < resources.size(); i++)
            loaded[i] = Platform.get().clone(resources.get(i).loaded());

        _transaction = _location.transaction(true);
        getClock(_transaction, IndexedDB.CLOCKS, loaded);
    }

    private native void getClock(JavaScriptObject transaction, String store, long[][] loaded) /*-{
    var store = transaction.objectStore(store);
    var cursor = store.openCursor();
    var this_ = this;

    cursor.onsuccess = function(e) {
      var result = e.target.result;
      var key = null, value = null;

      if (result) {
        key = result.key;
        value = result.value;
      }

      var done = this_.@org.objectfabric.IndexedDBClock::onclock(Ljava/lang/String;Ljava/lang/String;[[J)(key, value, loaded);

      if (!done)
        result["continue"]();
    };
    }-*/;

    private final boolean onclock(String key, String value, long[][] loaded) {
        if (key != null) {
            byte[] uid = new byte[UID.LENGTH];

            for (int i = 0; i < uid.length;) {
                int s = key.charAt(i / 2);
                uid[i++] = (byte) (s >>> 8);
                uid[i++] = (byte) s;
            }

            Peer peer = Peer.get(new UID(uid));

            long time = 0;
            time |= (long) value.charAt(0) << 0;
            time |= (long) value.charAt(1) << 16;
            time |= (long) value.charAt(2) << 32;

            long object = 0;
            object |= (long) value.charAt(3) << 0;
            object |= (long) value.charAt(4) << 16;
            object |= (long) value.charAt(5) << 32;
            object |= (long) value.charAt(6) << 48;

            if (upToDate(loaded, Tick.get(peer.index(), time))) {
                foundOne(peer, time, object);
                return true;
            }

            return false;
        }

        Peer peer = Peer.get(new UID(Platform.get().newUID()));
        long time = Clock.time(0, false);
        long object = 0;
        foundOne(peer, time, object);
        return true;
    }

    private final boolean upToDate(long[][] loaded, long tick) {
        for (int i = 0; i < loaded.length; i++)
            if (!Tick.happenedBefore(tick, loaded[i]))
                return false;

        return true;
    }

    // TODO simplify
    private final void foundOne(final Peer peer, final long time, final long object) {
        watcher().clock().init(peer, time(time, true), object);
        boolean was = watcher().actor().setScheduled();
        watcher().run();

        if (was)
            watcher().actor().setScheduled();
    }

    @Override
    void commit() {
        if (Debug.ENABLED)
            Debug.assertion((peer() == null) == (blocks().size() == 0));

        if (blocks().size() > 0) {
            NewBlock[] blocks = new NewBlock[blocks().size()];
            Buff[][] duplicates = new Buff[blocks.length][];

            for (int i = blocks.length - 1; i >= 0; i--) {
                NewBlock b = blocks[i] = blocks().removeLast();
                duplicates[i] = new Buff[b.Buffs.length];

                for (int d = 0; d < b.Buffs.length; d++)
                    duplicates[i][d] = b.Buffs[d].duplicate();

                _location.queue().write(_transaction, b.Resource.uri(), b.Tick, b.Buffs, b.Removals, false);
            }

            ThreadContext thread = ThreadContext.get();
            char[] chars = thread.PathCache;
            byte[] uid = peer().uid();

            for (int i = 0; i < uid.length;)
                chars[i / 2] = (char) (uid[i++] << 8 | (uid[i++] & 0xff));

            String key = new String(chars, 0, uid.length / 2);

            chars[0] = (char) (time() >>> 0);
            chars[1] = (char) (time() >>> 16);
            chars[2] = (char) (time() >>> 32);
            chars[3] = (char) (object() >>> 0);
            chars[4] = (char) (object() >>> 16);
            chars[5] = (char) (object() >>> 32);
            chars[6] = (char) (object() >>> 48);
            init(null, 0, 0);

            setClock(_transaction, IndexedDB.CLOCKS, key, new String(chars, 0, 7), blocks, duplicates);
        }
    }

    private native void setClock(JavaScriptObject transaction, String store, String key, String value, NewBlock[] blocks, Buff[][] duplicates) /*-{
    var request = transaction.objectStore(store).put(value, key);
    var this_ = this;

    request.onsuccess = function(e) {
      this_.@org.objectfabric.IndexedDBClock::onack([Lorg/objectfabric/Resource$NewBlock;[[Lorg/objectfabric/Buff;)(blocks, duplicates);
    };
    }-*/;

    private final void onack(final NewBlock[] blocks, final Buff[][] duplicates) {
        watcher().actor().addAndRun(new Message() {

            @Override
            void run() {
                for (int i = 0; i < blocks.length; i++) {
                    IndexedDBView view = (IndexedDBView) blocks[i].Resource.uri().getOrCreate(_location);
                    view.add(blocks[i].Tick, blocks[i].Removals);

                    publish(blocks[i].Resource, blocks[i].Tick, duplicates[i], blocks[i].Removals, _location);

                    for (int d = 0; d < duplicates[i].length; d++)
                        duplicates[i][d].recycle();

                    blocks[i].Resource.ack(_location, blocks[i].Tick);
                }
            }
        });
    }
}
