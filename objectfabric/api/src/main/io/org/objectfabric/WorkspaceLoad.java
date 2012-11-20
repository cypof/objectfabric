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

/**
 * Workspace state has to be loaded from cache or remote location as JavaScript platform
 * cannot generate reliable UIDs. This unfortunately adds some complexity, but also
 * happens to be an optimization. For eventual consistency it allows reuse of Peer UIDs,
 * and for serialization it allows TObject ids from several application runs to be from
 * the same Range. TODO: formalize this using interval tree clocks or something.
 */
@SuppressWarnings("serial")
abstract class WorkspaceLoad extends AtomicInteger {

    private static final class State {

        long Tick;

        byte[] Range;

        byte Id;
    }

    private static final PlatformConcurrentQueue<State> _saved = new PlatformConcurrentQueue<State>();

    private final URIResolver _resolver;

    WorkspaceLoad(URIResolver resolver) {
        super(1);

        if (resolver == null)
            throw new IllegalArgumentException();

        _resolver = resolver;
    }

    final void run() {
        State state = _saved.poll();

        if (state != null)
            onResponse(state.Tick, state.Range, state.Id);
        else {
            request(_resolver.uriHandlers());
            request(_resolver.caches());

            for (Location location : _resolver.origins().keySet())
                request(location);

            // For initial value 1, makes sure not done before last request sent
            onResponseNull();
        }
    }

    static final void recycle(long tick, byte[] range, byte id) {
        State state = new State();
        state.Tick = tick;
        state.Range = range;
        state.Id = id;
        _saved.add(state);
    }

    private final void request(Object[] array) {
        if (array != null)
            for (int i = 0; i < array.length; i++)
                if (array[i] instanceof Location)
                    request((Location) array[i]);
    }

    private final void request(Location location) {
        for (;;) {
            int value = get();

            if (isDone(value))
                break;

            if (compareAndSet(value, value + 1)) {
                location.start(this);
                break;
            }
        }
    }

    abstract void done(long tick, byte[] range, byte id);

    final boolean isDone() {
        return isDone(get());
    }

    private final boolean isDone(int value) {
        return value < 0;
    }

    final void onResponse(final long tick, final byte[] range, final byte id) {
        if (Debug.ENABLED)
            Debug.assertion(get() == -1 || get() > 0);

        for (;;) {
            int value = get();

            if (isDone(value)) { // Put back
                WorkspaceSave save = new WorkspaceSave() {

                    @Override
                    void run(Callback callback) {
                        callback.run(tick, range, id);
                    }

                    @Override
                    void done() {
                    }
                };

                save.run(_resolver);
                break;
            }

            if (compareAndSet(value, -1)) {
                done(tick, range, id);
                break;
            }
        }
    }

    final void onResponseNull() {
        if (Debug.ENABLED)
            Debug.assertion(get() == -1 || get() > 0);

        for (;;) {
            int value = get();

            if (isDone(value))
                break;

            int update = value - 1;

            if (compareAndSet(value, update)) {
                if (update == 0) {
                    Peer peer = Peer.get(new UID(Platform.get().newUID()));
                    done(Tick.get(peer.index(), 1), Platform.get().newUID(), (byte) 0);
                }

                break;
            }
        }
    }
}
