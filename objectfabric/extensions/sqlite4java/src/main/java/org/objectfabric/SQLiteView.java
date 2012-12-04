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

import org.objectfabric.SQLiteLoop.Query;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

final class SQLiteView extends ArrayView {

    private final byte[] _sha1;

    SQLiteView(Location location, byte[] sha1) {
        super(location);

        _sha1 = sha1;
    }

    final byte[] sha1() {
        return _sha1;
    }

    private final SQLite db() {
        return (SQLite) location();
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
        boolean load;

        synchronized (this) {
            load = isNull();
        }

        if (load)
            list(uri, ticks);
        else
            getUnknown(uri, ticks);
    }

    private final void list(final URI uri, final long[] compare) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("SQLite list blocks " + uri);

        if (Stats.ENABLED)
            Stats.Instance.BlockListCount.incrementAndGet();

        db().readers().add(new Query() {

            @Override
            void run(SQLiteConnection db) throws SQLiteException {
                SQLiteStatement st = db.prepare(Shared.LIST_BLOCKS);

                try {
                    long[] ticks = null;
                    st.bind(1, _sha1);

                    while (st.step()) {
                        long time = st.columnLong(0);
                        byte[] peer = st.columnBlob(1);
                        ticks = Tick.add(ticks, Tick.get(Peer.get(new UID(peer)).index(), time));
                    }

                    if (ticks == null)
                        ticks = Tick.EMPTY;

                    onLoad(uri, ticks, compare);
                } finally {
                    st.dispose();
                }
            }
        });
    }

    @Override
    void getBlock(final URI uri, final long tick) {
        if (!contains(tick))
            return;

        db().readers().add(new Query() {

            @Override
            void run(SQLiteConnection db) throws SQLiteException {
                if (Debug.PERSISTENCE_LOG)
                    Log.write("SQLite read block " + uri + " - " + Tick.toString(tick));

                if (Stats.ENABLED)
                    Stats.Instance.BlockReadCount.incrementAndGet();

                if (InFlight.starting(uri, tick)) {
                    SQLiteStatement st = db.prepare(Shared.SELECT_BLOCK);
                    List<JVMBuff> list = new List<JVMBuff>();

                    try {
                        st.bind(1, _sha1);
                        st.bind(2, Tick.time(tick));
                        st.bind(3, Peer.get(Tick.peer(tick)).uid());

                        if (st.step()) {
                            byte[] block = st.columnBlob(0);

                            JVMBuff buff = JVMBuff.getWithPosition(0);
                            int offset = 0;

                            for (;;) {
                                int length = Math.min(buff.remaining(), block.length - offset);
                                buff.putImmutably(block, offset, length);
                                offset += length;
                                buff.limit(buff.position() + length);
                                list.add(buff);

                                if (offset == block.length)
                                    break;

                                buff = JVMBuff.getWithPosition(Buff.getLargestUnsplitable());
                            }
                        }
                    } finally {
                        st.dispose();
                    }

                    if (list.size() > 0) {
                        JVMBuff[] buffs = new JVMBuff[list.size()];
                        list.copyToFixed(buffs);

                        if (Debug.ENABLED) {
                            for (int i = 0; i < buffs.length; i++) {
                                buffs[i].lock(buffs[i].limit());

                                if (Debug.THREADS)
                                    ThreadAssert.exchangeGive(buffs, buffs[i]);
                            }
                        }

                        Exception exception = uri.onBlock(SQLiteView.this, tick, buffs, null, true, null, false, null);

                        if (Debug.THREADS)
                            ThreadAssert.exchangeTake(buffs);

                        if (exception != null) {
                            // TODO make sure exception is related to parsing
                            Log.write("Corrupted block " + exception.toString());
                            // TODO Make option or callback to clean corrupted
                            // file.delete();
                        }

                        for (int i = 0; i < buffs.length; i++)
                            buffs[i].recycle();
                    }
                }
            }
        });
    }

    @Override
    final void onBlock(URI uri, long tick, Buff[] buffs, long[] removals, boolean requested) {
        db().queue().enqueueBlock(uri, tick, buffs, removals, requested);
    }
}
