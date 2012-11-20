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

abstract class ArrayView extends View {

    // TODO persistent or concurrent map?
    private long[] _ticks;

    ArrayView(Location location) {
        super(location);
    }

    @Override
    void getKnown(URI uri) {
        long[] ticks = copy();

        if (ticks != null)
            uri.onKnown(this, ticks);
    }

    //

    final boolean isNull() {
        if (Debug.ENABLED)
            Platform.get().assertLock(this, true);

        return _ticks == null;
    }

    final void clone(long[] value) {
        if (Debug.ENABLED)
            Platform.get().assertLock(this, true);

        _ticks = Platform.get().clone(value);
    }

    final void reset() {
        if (Debug.ENABLED)
            Platform.get().assertLock(this, false);

        synchronized (this) {
            _ticks = null;
        }
    }

    //

    final long[] copy() {
        if (Debug.ENABLED)
            Platform.get().assertLock(this, false);

        synchronized (this) {
            if (_ticks != null)
                return Platform.get().clone(_ticks);

            return null;
        }
    }

    final void getUnknown(URI uri, long[] ticks) {
        if (Debug.ENABLED)
            Platform.get().assertLock(this, false);

        boolean all = false;
        long[] list = null;
        int count = 0;

        synchronized (this) {
            if (_ticks == null || _ticks.length == 0)
                all = true;
            else {
                for (int i = 0; i < ticks.length; i++) {
                    if (!Tick.isNull(ticks[i])) {
                        if (!Tick.contains(_ticks, ticks[i])) {
                            if (list == null)
                                list = new long[ticks.length];

                            list[count++] = ticks[i];
                        }
                    }
                }
            }
        }

        if (all) {
            for (int i = 0; i < ticks.length; i++)
                if (!Tick.isNull(ticks[i]))
                    uri.getBlock(this, ticks[i]);
        } else if (list != null) {
            for (int i = 0; i < count; i++)
                uri.getBlock(this, list[i]);
        }
    }

    final boolean add(long tick, long[] removals) {
        if (Debug.ENABLED)
            Platform.get().assertLock(this, false);

        synchronized (this) {
            if (!Tick.contains(_ticks, tick)) {
                _ticks = Tick.add(_ticks, tick);

                if (removals != null)
                    for (int i = 0; i < removals.length; i++)
                        if (!Tick.isNull(removals[i]))
                            Tick.remove(_ticks, removals[i]);

                return true;
            }

            return false;
        }
    }

    final long[] merge(long[] ticks) {
        if (Debug.ENABLED)
            Platform.get().assertLock(this, false);

        synchronized (this) {
            if (_ticks == null) {
                _ticks = Platform.get().clone(ticks);
                return ticks;
            }

            boolean updated = false;

            for (int i = 0; i < ticks.length; i++) {
                if (!Tick.isNull(ticks[i])) {
                    if (!Tick.contains(_ticks, ticks[i])) {
                        _ticks = Tick.add(_ticks, ticks[i]);
                        updated = true;
                    }
                }
            }

            return updated ? Platform.get().clone(_ticks) : null;
        }
    }
}
