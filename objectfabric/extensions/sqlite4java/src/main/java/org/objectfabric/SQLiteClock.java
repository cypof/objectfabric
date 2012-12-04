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
import org.objectfabric.SQLiteLoop.Query;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

final class SQLiteClock extends Clock {

    private final SQLite _location;

    SQLiteClock(Watcher watcher, SQLite location) {
        super(watcher);

        _location = location;
    }

    @Override
    void writing(Resources resources) {
        final long[][] loaded = new long[resources.size()][];

        for (int i = 0; i < resources.size(); i++)
            loaded[i] = Platform.get().clone(resources.get(i).loaded());

        _location.writer().add(new Query() {

            @Override
            void run(SQLiteConnection db) throws SQLiteException {
                SQLiteStatement st = db.prepare(Shared.SELECT_CLOCKS);
                Peer peer = null;
                long time = 0, object = 0;
                boolean foundOne = false;

                try {
                    while (st.step()) {
                        peer = Peer.get(new UID(st.columnBlob(0)));
                        time = st.columnLong(1);
                        object = st.columnLong(2);

                        long tick = Tick.get(peer.index(), time);

                        if (upToDate(loaded, tick)) {
                            foundOne = true;
                            break;
                        }
                    }
                } finally {
                    st.dispose();
                }

                if (!foundOne) {
                    peer = Peer.get(new UID(Platform.get().newUID()));
                    time = Clock.time(0, false);
                    object = 0;
                }

                _location.writer().walks().put(peer, peer);

                final Peer peer_ = peer;
                final long time_ = time;
                final long object_ = object;

                watcher().actor().addAndRun(new Message() {

                    @Override
                    void run() {
                        watcher().clock().init(peer_, time(time_, true), object_);
                    }
                });
            }
        });
    }

    private final boolean upToDate(long[][] loaded, long tick) {
        for (int i = 0; i < loaded.length; i++)
            if (!Tick.happenedBefore(tick, loaded[i]))
                return false;

        return true;
    }

    @Override
    void commit() {
        if (Debug.ENABLED)
            Debug.assertion((peer() == null) == (blocks().size() == 0));

        if (blocks().size() > 0) {
            final NewBlock[] blocks = new NewBlock[blocks().size()];
            final Buff[][] duplicates = new Buff[blocks.length][];

            for (int i = blocks.length - 1; i >= 0; i--) {
                blocks[i] = blocks().removeLast();
                duplicates[i] = new Buff[blocks[i].Buffs.length];

                for (int d = 0; d < blocks[i].Buffs.length; d++) {
                    duplicates[i][d] = blocks[i].Buffs[d].duplicate();

                    if (Debug.THREADS)
                        ThreadAssert.exchangeGive(duplicates, duplicates[i][d]);
                }
            }

            final Peer peer = peer();
            final long time = time();
            final long object = object();
            init(null, 0, 0);

            _location.writer().add(new Query() {

                @Override
                void run(SQLiteConnection db) throws SQLiteException {
                    if (Debug.THREADS)
                        ThreadAssert.exchangeTake(duplicates);

                    SQLiteStatement st = db.prepare(Shared.REPLACE_CLOCK);

                    try {
                        st.bind(1, peer.uid());
                        st.bind(2, time);
                        st.bind(3, object);
                        st.step();
                    } finally {
                        st.dispose();
                    }

                    for (int i = 0; i < blocks.length; i++) {
                        _location.queue().write(db, blocks[i].Resource.uri(), blocks[i].Tick, duplicates[i], blocks[i].Removals);

                        if (Debug.THREADS)
                            for (int d = 0; d < duplicates[i].length; d++)
                                ThreadAssert.exchangeGive(watcher().actor(), duplicates[i][d]);
                    }

                    _location.writer().walks().remove(peer);
                }

                @Override
                void ack() {
                    watcher().actor().addAndRun(new Message() {

                        @Override
                        void run() {
                            for (int i = 0; i < blocks.length; i++) {
                                SQLiteView view = (SQLiteView) blocks[i].Resource.uri().getOrCreate(_location);
                                view.add(blocks[i].Tick, blocks[i].Removals);

                                publish(blocks[i].Resource, blocks[i].Tick, duplicates[i], blocks[i].Removals, _location);

                                for (int d = 0; d < duplicates[i].length; d++)
                                    duplicates[i][d].recycle();

                                blocks[i].Resource.ack(_location, blocks[i].Tick);
                            }
                        }
                    });
                }
            });
        }
    }
}
