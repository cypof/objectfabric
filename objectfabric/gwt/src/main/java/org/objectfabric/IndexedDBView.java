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

import org.objectfabric.InFlight.Provider;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.ArrayBuffer;

final class IndexedDBView extends ArrayView {

    private final String _uriKey;

    IndexedDBView(Location location, String uriKey) {
        super(location);

        _uriKey = uriKey;
    }

    final String getKey(long tick) {
        String tickKey = IndexedDB.getKey(tick);
        return _uriKey + tickKey;
    }

    final long getTick(String key) {
        return IndexedDB.getTick(key.substring(_uriKey.length()));
    }

    private final IndexedDB cache() {
        return (IndexedDB) location();
    }

    @Override
    final void getKnown(URI uri) {
        long[] ticks = copy();

        if (ticks != null) {
            if (ticks.length != 0 || !location().isCache())
                uri.onKnown(this, ticks);
        } else
            list(uri, null);
    }

    @Override
    final void onKnown(URI uri, long[] ticks) {
        if (isNull())
            list(uri, ticks);
        else
            getUnknown(uri, ticks);
    }

    private final void list(final URI uri, final long[] compare) {
        if (cache().db() == null) {
            cache().runWhenStarted(new Runnable() {

                @Override
                public void run() {
                    listImpl(uri, compare);
                }
            });
        } else
            listImpl(uri, compare);
    }

    private final void listImpl(final URI uri, long[] compare) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("IndexedDBView openCursor " + uri);

        if (Stats.ENABLED)
            Stats.Instance.BlockListCount.incrementAndGet();

        ArrayList<String> keys = new ArrayList<String>();
        cursor(uri, cache().db(), IndexedDB.BLOCKS, _uriKey, keys, compare);
    }

    private native void cursor(URI uri, JavaScriptObject db, String store, String key, ArrayList<String> keys, long[] compare) /*-{
    var transaction = db.transaction(store);
    var store = transaction.objectStore(store);
    var cursor = store.openCursor(window.IDBKeyRange.lowerBound(key));
    var this_ = this;

    cursor.onsuccess = function(e) {
      var result = e.target.result;
      var key = null;

      if (result)
        key = result.key;

      var done = this_.@org.objectfabric.IndexedDBView::step(Lorg/objectfabric/URI;Ljava/lang/String;Ljava/util/ArrayList;[J)(uri, key, keys, compare);

      if (!done)
        result["continue"]();
    };
    }-*/;

    @SuppressWarnings("null")
    private final boolean step(URI uri, String key, ArrayList<String> keys, long[] compare) {
        boolean done = key == null;

        if (!done)
            for (int i = 0; i < SHA1Digest.LENGTH / 2; i++)
                if (key.charAt(i) != _uriKey.charAt(i))
                    done = true;

        if (!done)
            keys.add(key);
        else {
            long[] ticks = null;

            if (keys.size() > 0) {
                for (int i = 0; i < keys.size(); i++)
                    ticks = Tick.add(ticks, getTick(keys.get(i)));
            } else
                ticks = Tick.EMPTY;

            onLoad(uri, ticks, compare);
        }

        return done;
    }

    @Override
    void getBlock(URI uri, long tick) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("IndexedDB read " + uri + " - " + Tick.toString(tick));

        Read read = read(cache().db(), IndexedDB.BLOCKS, getKey(tick), uri, tick);

        if (!InFlight.starting(uri, tick, read))
            read.cancel(uri, tick);
    }

    private native Read read(JavaScriptObject db, String store, String key, URI uri, Long tick) /*-{
    var transaction = db.transaction(store);
    var request = transaction.objectStore(store).get(key);
    var this_ = this;

    request.onsuccess = function(e) {
      this_.@org.objectfabric.IndexedDBView::onsuccess(Lorg/objectfabric/URI;Ljava/lang/Long;Lcom/google/gwt/typedarrays/shared/ArrayBuffer;)(uri, tick, e.target.result);
    };

    return transaction;
    }-*/;

    private final void onsuccess(URI uri, Long tick, ArrayBuffer buffer) {
        GWTBuff buff = new GWTBuff(Uint8Array.create(buffer));

        if (Debug.ENABLED)
            buff.lock(buff.limit());

        if (Stats.ENABLED)
            Stats.Instance.BlockReadCount.incrementAndGet();

        Buff[] buffs = new Buff[] { buff };
        Exception exception = uri.onBlock(this, tick, buffs, null, true, null, false, null);

        if (exception != null) {
            // TODO make sure exception is related to parsing
            Log.write("Corrupted block " + exception.toString());
            // TODO Make option or callback to clean corrupted
            // file.delete();
        }
    }

    static final class Read extends JavaScriptObject implements Provider {

        protected Read() {
        }

        @Override
        public void cancel(URI uri, long tick) {
            cancel(this);
        }

        private native void cancel(Read read)/*-{
      read.abort();
        }-*/;
    }

    @Override
    final void onBlock(URI uri, long tick, Buff[] buffs, long[] removals, boolean requested) {
        cache().queue().enqueueBlock(uri, tick, buffs, removals, requested);
    }
}
