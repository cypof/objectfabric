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

import java.util.concurrent.atomic.AtomicInteger;

final class Peer {

    // TODO Weak map
    private static final PlatformConcurrentMap<UID, Peer> _uids = new PlatformConcurrentMap<UID, Peer>();

    // TODO break per URI, use an array
    private static final PlatformConcurrentMap<IntBox, Peer> _indexes = new PlatformConcurrentMap<IntBox, Peer>();

    private static final AtomicInteger _count = new AtomicInteger();

    private final byte[] _uid;

    private final int _index;

    private Peer(byte[] uid, int index) {
        if (uid == null)
            throw new IllegalStateException();

        if (index >>> Tick.PEER_BITS != 0)
            throw new IllegalArgumentException();

        _uid = uid;
        _index = index;
    }

    final byte[] uid() {
        return _uid;
    }

    final int index() {
        return _index;
    }

    static Peer get(UID uid) {
        Peer peer = _uids.get(uid);

        if (peer != null)
            return peer;

        peer = new Peer(uid.getBytes(), _count.getAndIncrement());
        _indexes.put(new IntBox(peer._index), peer);
        Peer previous = _uids.putIfAbsent(uid, peer);

        if (previous != null)
            return previous;

        return peer;
    }

    static Peer get(int index) {
        return _indexes.get(new IntBox(index));
    }

    final boolean higher(byte[] peer) {
        return UID.compare(_uid, peer) > 0;
    }

    @Override
    public String toString() {
        char[] chars = new char[4];
        Utils.getBytesHex(_uid, 0, 2, chars, 0);
        return "Peer (" + new String(chars) + ")";
    }

    /*
     * Custom primitive wrapper that do not reference Class, so that Java reflection can
     * be removed from the .NET version.
     */
    private static final class IntBox {

        private final int _value;

        IntBox(int value) {
            _value = value;
        }

        @Override
        public int hashCode() {
            return _value;
        }

        @Override
        public boolean equals(Object obj) {
            return _value == ((IntBox) obj)._value;
        }
    }
}
