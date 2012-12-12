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

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.client.Uint8ArrayNative;
import com.google.gwt.typedarrays.shared.ArrayBuffer;

final class IndexedDBQueue extends BlockQueue implements Runnable {

    private static final int MAX_ONGOING = 10;

    private final IndexedDB _location;

    private int _ongoing;

    IndexedDBQueue(IndexedDB location) {
        _location = location;

        onStarted();
    }

    @Override
    protected void enqueue() {
        Platform.get().execute(this);
    }

    @Override
    public void run() {
        if (onRunStarting()) {
            if (Debug.ENABLED)
                ThreadAssert.resume(this, false);

            runMessages(false);

            while (_ongoing < MAX_ONGOING) {
                Block block = nextBlock();

                if (block == null)
                    break;

                if (Debug.PERSISTENCE_LOG)
                    Log.write("IndexedDB write " + block.URI + " - " + Tick.toString(block.Tick));

                if (Stats.ENABLED)
                    Stats.Instance.BlockWriteCount.incrementAndGet();

                write(_location.transaction(false), block.URI, block.Tick, block.Buffs, block.Removals, true);
            }

            if (Debug.ENABLED)
                ThreadAssert.suspend(this);

            onRunEnded(false);
        }
    }

    final void write(JavaScriptObject transaction, URI uri, long tick, Buff[] buffs, long[] removals, boolean callback) {
        IndexedDBView view = (IndexedDBView) uri.getOrCreate(_location);
        ArrayBuffer buffer=null;

        if (buffs.length == 1) {
            GWTBuff buff = (GWTBuff) buffs[0];
  //          buffer = buff.slice();
        } else {
            int capacity = 0;

            for (int i = 0; i < buffs.length; i++)
                capacity += buffs[i].remaining();

            Uint8Array array = ((GWTPlatform) Platform.get()).newUint8Array(capacity);
            int position = 0;

            for (int i = 0; i < buffs.length; i++) {
                GWTBuff buff = (GWTBuff) buffs[i];
                array.set(buff.subarray(), position);
                position += buff.remaining();
            }

//            buffer = array.buffer();
        }

        JavaScriptObject request = write(transaction, IndexedDB.BLOCKS, view.getKey(tick), buffer);

        if (callback)
            callback(request, view, uri, new long[] { tick }, buffs, removals);

        if (removals != null)
            for (int i = 0; i < removals.length; i++)
                if (!Tick.isNull(removals[i]))
                    delete(_location.db(), IndexedDB.BLOCKS, view.getKey(removals[i]));

        _ongoing++;
    }

    private native JavaScriptObject write(JavaScriptObject transaction, String store, String key, ArrayBuffer value) /*-{
    return transaction.objectStore(store).put(value, key);
    }-*/;

    private native void callback(JavaScriptObject request, IndexedDBView view, URI uri, long[] tick, Buff[] buffs, long[] removals) /*-{
    var this_ = this;

    request.onsuccess = function(e) {
      this_.@org.objectfabric.IndexedDBQueue::onsuccess(Lorg/objectfabric/IndexedDBView;Lorg/objectfabric/URI;[J[Lorg/objectfabric/Buff;[J)(view, uri, tick , buffs, removals);
    };
    }-*/;

    private final void onsuccess(IndexedDBView view, URI uri, long[] tick, Buff[] buffs, long[] removals) {
        _ongoing--;

        for (int i = 0; i < buffs.length; i++)
            buffs[i].recycle();

        uri.onAck(view, tick[0]);
        view.add(tick[0], removals);

        // In case blocks left in queue
        requestRun();
    }

    private native void delete(JavaScriptObject db, String store, String key) /*-{
    var transaction = db.transaction(store, "readwrite");
    transaction.objectStore(store)["delete"](key);
    }-*/;
}
