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

final class InFlight {

    // TODO tune concurrency
    private static final PlatformConcurrentMap<Key, Object> _map = new PlatformConcurrentMap<Key, Object>();

    private static final int BLOCK = 0, ACK = 1;

    private InFlight() {
    }

    static void get(URI uri, long tick, Object requester) {
        Key get = new Key(uri, tick, BLOCK);

        for (;;) {
            Get expect = (Get) _map.get(get);

            if (expect == null) {
                expect = new Get(requester);

                if (_map.putIfAbsent(get, expect) == null) {
                    uri.startGetBlock(requester, tick);
                    break;
                }
            } else {
                Object update = add(expect.From, requester);

                if (update == expect.From || _map.replace(get, expect, new Get(update, expect.Sent)))
                    break;
            }
        }
    }

    static boolean awaits(URI uri, long tick) {
        return _map.containsKey(new Key(uri, tick, BLOCK));
    }

    static boolean sending(URI uri, long tick, Connection connection) {
        Key key = new Key(uri, tick, BLOCK);

        for (;;) {
            Get get = (Get) _map.get(key);

            if (get == null)
                return false;

            Get update = new Get(get.From, add(get.Sent, connection));

            if (Debug.ENABLED)
                Debug.assertion(update != get);

            if (_map.replace(key, get, update))
                return true;
        }
    }

    static void cancel(URI uri, long tick, Object requester) {
        Key key = new Key(uri, tick, BLOCK);

        for (;;) {
            Get expect = (Get) _map.get(key);

            if (expect == null)
                break;

            if (tryCancel(key, expect, requester))
                break;
        }
    }

    private static boolean tryCancel(Key key, Get expect, Object requester) {
        if (expect.From == requester) {
            if (!_map.remove(key, expect))
                return false;

            expect.cancel(key.URI, key.Tick, null);
            return true;
        }

        Object[] from = (Object[]) expect.From;
        int index = indexOf(from, requester);

        if (index >= 0) {
            Get update = new Get(sub(from, index), expect.Sent);

            if (!_map.replace(key, expect, update))
                return false;
        }

        return true;
    }

    static Get onBlock(URI uri, long tick, Connection connection) {
        Get get = (Get) _map.remove(new Key(uri, tick, BLOCK));

        if (get != null) {
            get.cancel(uri, tick, connection);
            return get;
        }

        return null;
    }

    static void needsAck(URI uri, long tick, Connection connection) {
        Object previous = _map.put(new Key(uri, tick, ACK), connection);

        if (Debug.ENABLED)
            Debug.assertion(previous == null);
    }

    static void onAck(URI uri, long tick) {
        Connection connection = (Connection) _map.remove(new Key(uri, tick, ACK));

        if (connection != null)
            connection.postAck(uri, tick);
    }

    static boolean idle() {
        return _map.isEmpty();
    }

    private static final class Key {

        final URI URI;

        final long Tick;

        final int Type;

        Key(URI uri, long tick, int type) {
            URI = uri;
            Tick = tick;
            Type = type;
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof Key) {
                Key other = (Key) object;
                return URI == other.URI && Tick == other.Tick && Type == other.Type;
            }

            return false;
        }

        @Override
        public int hashCode() {
            return URI.hashCode() ^ (int) (Tick ^ (Tick >>> 32)) ^ Type;
        }
    }

    static final class Get {

        final Object From;

        final Object Sent;

        Get(Object from) {
            this(from, null);
        }

        Get(Object from, Object sent) {
            From = from;
            Sent = sent;
        }

        final void cancel(URI uri, long tick, Connection skip) {
            if (Sent instanceof Connection) {
                if (Sent != skip)
                    ((Connection) Sent).postCancel(uri, tick);

                return;
            }

            if (Sent instanceof Connection[]) {
                Connection[] array = (Connection[]) Sent;

                for (int i = 0; i < array.length; i++)
                    if (array[i] != skip)
                        array[i].postCancel(uri, tick);
            }
        }
    }

    static Object add(Object current, Object add) {
        if (current == null)
            return add;

        if (current instanceof Object[]) {
            Object[] array = (Object[]) current;

            for (int i = 0; i < array.length; i++)
                if (array[i] == add)
                    return current;

            Object[] update = new Object[array.length + 1];
            Platform.arraycopy(array, 0, update, 0, array.length);
            update[update.length - 1] = add;
            return update;
        }

        if (current == add)
            return current;

        return new Object[] { current, add };
    }

    static int indexOf(Object[] array, Object item) {
        for (int i = 0; i < array.length; i++)
            if (array[i] == item)
                return i;

        return -1;
    }

    static Object sub(Object[] array, int index) {
        if (Debug.ENABLED)
            Debug.assertion(array.length > 1);

        Object[] update = new Object[array.length - 1];
        Platform.arraycopy(array, 0, update, 0, index);
        Platform.arraycopy(array, index + 1, update, index, array.length - 1 - index);
        return update.length == 1 ? update[0] : update;
    }
}
