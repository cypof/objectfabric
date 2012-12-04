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

import java.util.Iterator;

import org.objectfabric.Actor.Message;

@SuppressWarnings({ "rawtypes", "unchecked" })
class ServerView extends ArrayView {

    private final PlatformConcurrentMap _map = new PlatformConcurrentMap();

    private final PlatformConcurrentQueue<Connection> _pending = new PlatformConcurrentQueue<Connection>();

    ServerView(Server server) {
        super(server);
    }

    final void onPermission(final URI uri, final Connection connection, Permission permission) {
        _map.put(connection, permission);
        long[] ticks = copy();

        if (ticks != null) {
            connection.postPermission(uri, permission, false);
            connection.postKnown(uri, ticks);
        } else {
            _pending.add(connection);
            Object key;

            if (Debug.THREADS)
                ThreadAssert.suspend(key = new Object());

            uri.getKnown(this);

            if (Debug.THREADS)
                ThreadAssert.resume(key);
        }

        connection.addAndRun(new Message() {

            @Override
            void run() {
                connection.subscribed().put(uri, ServerView.this);
            }
        });
    }

    //

    final void readUnsubscribe(final URI uri, final Connection connection) {
        unsubscribe(connection);

        connection.addAndRun(new Message() {

            @Override
            void run() {
                connection.subscribed().remove(uri);
            }
        });
    }

    final void unsubscribe(Connection connection) {
        _map.remove(connection);
    }

    final void readGetBlock(URI uri, long tick, Connection connection) {
        if (_map.containsKey(connection)) {
            Get get = new Get(uri, tick);

            for (;;) {
                Object expect = _map.get(get);

                if (expect == null) {
                    if (_map.putIfAbsent(get, connection) == null) {
                        Object key;

                        if (Debug.THREADS)
                            ThreadAssert.suspend(key = new Object());

                        uri.getBlock(this, tick);

                        if (Debug.THREADS)
                            ThreadAssert.resume(key);

                        break;
                    }
                } else {
                    Object update = InFlight.add(expect, connection);

                    if (update == expect || _map.replace(get, expect, update))
                        break;
                }
            }
        }
    }

    final void readCancelBlock(URI uri, long tick, Connection connection) {
        Get get = new Get(uri, tick);

        for (;;) {
            Object expect = _map.get(get);

            if (expect == null)
                break;

            if (tryCancel(get, expect, connection))
                break;
        }
    }

    private final boolean tryCancel(Get get, Object expect, Connection connection) {
        if (expect == connection) {
            if (!_map.remove(get, expect))
                return false;

            Object key;

            if (Debug.THREADS)
                ThreadAssert.suspend(key = new Object());

            get.URI.cancelBlock(this, get.Tick);

            if (Debug.THREADS)
                ThreadAssert.resume(key);

            return true;
        }

        Object[] connections = (Object[]) expect;
        int index = InFlight.indexOf(connections, connection);

        if (index >= 0)
            if (!_map.replace(get, expect, InFlight.sub(connections, index)))
                return false;

        return true;
    }

    private static final class Get {

        final URI URI;

        final long Tick;

        Get(URI uri, long tick) {
            URI = uri;
            Tick = tick;
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof Get) {
                Get other = (Get) object;
                return URI == other.URI && Tick == other.Tick;
            }

            return false;
        }

        @Override
        public int hashCode() {
            return URI.hashCode() ^ (int) (Tick ^ (Tick >>> 32));
        }
    }

    final void readBlock(URI uri, long tick, Connection connection, Buff[] buffs, long[] removals, boolean requested) {
        Permission permission = (Permission) _map.get(connection);

        if (permission == Permission.WRITE) {
            if (add(tick, removals)) {
                for (Object other : _map.keySet())
                    if (other != connection && other instanceof Connection)
                        ((Connection) other).enqueueBlock(uri, tick, buffs, removals, requested);

                connection.onBlock(uri, this, tick, buffs, removals, requested, true);
            }
        }
    }

    //

    @Override
    final void onKnown(URI uri, long[] ticks) {
        long[] updated = merge(ticks);

        for (Iterator<Connection> i = _pending.iterator(); i.hasNext();) {
            Connection connection = i.next();
            connection.postPermission(uri, (Permission) _map.get(connection), false);

            if (updated == null)
                connection.postKnown(uri, ticks);

            i.remove();
        }

        // TODO increase buffs counters by connection count in one step
        if (updated != null)
            for (Object connection : _map.keySet())
                if (connection instanceof Connection)
                    ((Connection) connection).postKnown(uri, updated);
    }

    @Override
    final void getBlock(URI uri, long tick) {
    }

    @Override
    final void onBlock(URI uri, long tick, Buff[] buffs, long[] removals, boolean requested) {
        if (requested) {
            if (Debug.ENABLED)
                Debug.assertion(removals == null);

            Object connections = _map.remove(new Get(uri, tick));

            if (connections instanceof Connection) {
                Connection connection = (Connection) connections;
                connection.enqueueBlock(uri, tick, buffs, null, requested);
            }

            if (connections instanceof Connection[]) {
                Connection[] array = (Connection[]) connections;

                for (int i = 0; i < array.length; i++)
                    array[i].enqueueBlock(uri, tick, buffs, null, requested);
            }
        } else if (add(tick, removals))
            for (Object connection : _map.keySet())
                if (connection instanceof Connection)
                    ((Connection) connection).enqueueBlock(uri, tick, buffs, removals, requested);
    }
}
