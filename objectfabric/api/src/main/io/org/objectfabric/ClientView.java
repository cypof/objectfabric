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

class ClientView extends ArrayView {

    private Permission _permission;

    ClientView(Remote remote) {
        super(remote);
    }

    private final Connection getConnection() {
        return ((Remote) location()).connection();
    }

    //

    @Override
    void open(final URI uri) {
        ((Remote) location()).onOpen(uri);
    }

    @Override
    void close(final URI uri) {
        ((Remote) location()).onClose(uri);
    }

    //

    final void readPermission(URI uri, Permission permission) {
        _permission = permission;

        Object key;

        if (Debug.THREADS)
            ThreadAssert.suspend(key = new Object());

        uri.onPermission(permission);

        if (Debug.THREADS)
            ThreadAssert.resume(key);
    }

    final void readUnresolved(URI uri) {
        Object key;

        if (Debug.THREADS)
            ThreadAssert.suspend(key = new Object());

        uri.onUnresolved();

        if (Debug.THREADS)
            ThreadAssert.resume(key);
    }

    final void readKnown(URI uri, long[] ticks) {
        synchronized (this) {
            clone(ticks);
        }

        Object key;

        if (Debug.THREADS)
            ThreadAssert.suspend(key = new Object());

        uri.onKnown(this, ticks);
        Connection connection = getConnection();

        // Re-send requests in case of reconnection
        if (connection != null)
            for (int i = 0; i < ticks.length; i++)
                if (!Tick.isNull(ticks[i]))
                    if (InFlight.awaits(uri, ticks[i]))
                        connection.postGet(uri, ticks[i]);

        // Send new local blocks
        uri.getKnown(this);

        if (Debug.THREADS)
            ThreadAssert.resume(key);
    }

    final void readBlock(URI uri, long tick, Connection connection, Buff[] buffs, long[] removals, boolean requested) {
        add(tick, removals);
        connection.onBlock(uri, this, tick, buffs, removals, requested, false);
    }

    final void readAck(URI uri, long tick) {
        Object key;

        if (Debug.THREADS)
            ThreadAssert.suspend(key = new Object());

        uri.onAck(this, tick);

        if (Debug.THREADS)
            ThreadAssert.resume(key);
    }

    //

    @Override
    final void onKnown(URI uri, long[] ticks) {
        if (_permission == Permission.WRITE)
            getUnknown(uri, ticks);
    }

    @Override
    final void getBlock(URI uri, long tick) {
        if (_permission != Permission.REJECT) {
            Connection connection = getConnection();

            if (connection != null)
                connection.postGet(uri, tick);
        }
    }

    @Override
    final void onBlock(URI uri, long tick, Buff[] buffs, long[] removals, boolean requested) {
        if (_permission == Permission.WRITE) {
            Connection connection = getConnection();

            if (connection != null) // Always say not requested
                connection.enqueueBlock(uri, tick, buffs, removals, false);
        }
    }
}
