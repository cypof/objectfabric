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

import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.almworks.sqlite4java.SQLiteBusyException;
import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;

final class SQLiteLoop {

    private static final int MAX_BATCH = 100; // TODO tune

    static abstract class Query {

        int statements() {
            return 1;
        }

        abstract void run(SQLiteConnection db) throws SQLiteException;

        void ack() {
        }
    }

    private final SQLite _location;

    private final Run[] _runs;

    private final LinkedBlockingQueue<Query> _queue = new LinkedBlockingQueue<Query>();

    private final AtomicInteger _ongoing;

    private final HashMap<Peer, Peer> _walks;

    private volatile boolean _running = true;

    SQLiteLoop(SQLite location, int threads, boolean count) {
        _location = location;
        _runs = new Run[threads];

        for (int i = 0; i < threads; i++) {
            _runs[i] = new Run();
            _runs[i].start();
        }

        if (count) {
            _ongoing = new AtomicInteger();
            _walks = new HashMap<Peer, Peer>();
        } else {
            _ongoing = null;
            _walks = null;
        }
    }

    final void close() {
        _running = false;

        for (int i = 0; i < _runs.length; i++) {
            _runs[i].interrupt();

            try {
                _runs[i].join();
            } catch (InterruptedException e) {
            }
        }
    }

    final int room() {
        return MAX_BATCH - _ongoing.get();
    }

    final void add(Query query) {
        _queue.offer(query);

        if (_ongoing != null)
            _ongoing.addAndGet(query.statements());
    }

    final HashMap<Peer, Peer> walks() {
        return _walks;
    }

    private final class Run extends Thread {

        Run() {
            setName("SQLiteQueue");
            setDaemon(true);
        }

        @Override
        public void run() {
            SQLiteConnection db = new SQLiteConnection(_location.file());
            List<Query> toAck = new List<Query>();

            try {
                db.open(true);

                for (;;) {
                    try {
                        db.exec(Shared.INIT);
                        break;
                    } catch (SQLiteBusyException e) {
                        Thread.sleep(1);
                    }
                }

                while (_running) {
                    Query query = _queue.take();

                    if (_ongoing == null)
                        query.run(db);
                    else {
                        if (_walks.size() == 0) {
                            /*
                             * Immediate because clock adds reads at beginning of
                             * transaction, which can lead to deadlock if done from
                             * multiple processes.
                             */
                            db.exec("BEGIN IMMEDIATE");

                            if (Debug.ENABLED)
                                Debug.assertion(toAck.size() == 0);
                        }

                        while (query != null) {
                            query.run(db);
                            toAck.add(query);
                            query = _queue.poll();
                        }

                        if (_walks.size() == 0) {
                            for (;;) {
                                try {
                                    db.exec("COMMIT");
                                    break;
                                } catch (SQLiteBusyException e) {
                                    Thread.sleep(1);
                                }
                            }

                            int count = 0;

                            for (int i = 0; i < toAck.size(); i++)
                                count += toAck.get(i).statements();

                            _ongoing.addAndGet(-count);

                            for (int i = 0; i < toAck.size(); i++)
                                toAck.get(i).ack();

                            toAck.clear();
                        }
                    }
                }
            } catch (Exception e) {
                if (!(e instanceof InterruptedException))
                    Log.write(e);
            } finally {
                db.dispose();
            }
        }
    }
}
