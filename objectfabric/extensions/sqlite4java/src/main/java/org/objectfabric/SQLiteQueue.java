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

import org.objectfabric.CloseCounter.Callback;
import org.objectfabric.SQLiteLoop.Query;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

final class SQLiteQueue extends BlockQueue implements Runnable {

    private final SQLite _location;

    SQLiteQueue(SQLite location) {
        _location = location;

        if (Debug.THREADS)
            ThreadAssert.exchangeGive(this, this);

        onStarted();
    }

    @Override
    void onClose(Callback callback) {
        Object key;

        if (Debug.ENABLED) {
            ThreadAssert.suspend(key = new Object());
            ThreadAssert.resume(this, false);
        }

        if (Debug.THREADS) {
            ThreadAssert.exchangeTake(this);
            ThreadAssert.removePrivate(this);
        }

        if (Debug.ENABLED)
            ThreadAssert.resume(key);

        super.onClose(callback);
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
            final List<Block> list = new List<Block>();
            int room = _location.writer().room();

            for (int i = 0; i < room; i++) {
                Block block = nextBlock();

                if (block == null)
                    break;

                list.add(block);

                if (Debug.THREADS)
                    for (int t = 0; t < block.Buffs.length; t++)
                        ThreadAssert.exchangeGive(block, block.Buffs[t]);
            }

            if (list.size() > 0) {
                _location.writer().add(new Query() {

                    @Override
                    int statements() {
                        return list.size();
                    }

                    @Override
                    void run(SQLiteConnection db) throws SQLiteException {
                        for (int i = 0; i < list.size(); i++) {
                            Block block = list.get(i);

                            if (Debug.THREADS)
                                ThreadAssert.exchangeTake(block);

                            write(db, block.URI, block.Tick, block.Buffs, block.Removals);

                            for (int b = 0; b < block.Buffs.length; b++)
                                block.Buffs[b].recycle();
                        }
                    }

                    @Override
                    void ack() {
                        for (int i = 0; i < list.size(); i++) {
                            Block b = list.get(i);
                            SQLiteQueue.this.ack(b.URI, b.Tick, b.Removals);
                        }

                        // Might be room now
                        requestRun();
                    }
                });
            }

            if (Debug.ENABLED)
                ThreadAssert.suspend(this);

            onRunEnded(false);
        }
    }

    final void write(SQLiteConnection db, URI uri, long tick, Buff[] buffs, long[] removals) throws SQLiteException {
        if (Debug.PERSISTENCE_LOG)
            Log.write("SQLite write " + uri + " - " + Tick.toString(tick));

        if (Stats.ENABLED)
            Stats.Instance.BlockWriteCount.incrementAndGet();

        SQLiteView view = (SQLiteView) uri.getOrCreate(_location);
        SQLiteStatement st = db.prepare(Shared.REPLACE_BLOCK);

        try {
            st.bind(1, view.sha1());
            st.bind(2, Tick.time(tick));
            st.bind(3, Peer.get(Tick.peer(tick)).uid());

            int capacity = 0;

            for (int i = 0; i < buffs.length; i++)
                capacity += buffs[i].remaining();

            byte[] array = new byte[capacity];
            int offset = 0;

            for (int i = 0; i < buffs.length; i++) {
                int length = buffs[i].remaining();
                buffs[i].getImmutably(array, offset, length);
                offset += length;
            }

            st.bind(4, array);
            st.step();
        } finally {
            st.dispose();
        }

        if (removals != null)
            for (int i = 0; i < removals.length; i++)
                if (!Tick.isNull(removals[i]))
                    delete(db, view, removals[i]);
    }

    private final void delete(SQLiteConnection db, SQLiteView view, long tick) throws SQLiteException {
        SQLiteStatement st = db.prepare(Shared.DELETE_BLOCK);

        try {
            st.bind(1, view.sha1());
            st.bind(2, Tick.time(tick));
            st.bind(3, Peer.get(Tick.peer(tick)).uid());
            st.step();
        } finally {
            st.dispose();
        }
    }

    private final void ack(URI uri, long tick, long[] removals) {
        Object key;

        if (Debug.THREADS)
            ThreadAssert.suspend(key = new Object());

        SQLiteView view = (SQLiteView) uri.getOrCreate(_location);
        uri.onAck(view, tick);
        view.add(tick, removals);

        if (Debug.THREADS)
            ThreadAssert.resume(key);
    }
}
