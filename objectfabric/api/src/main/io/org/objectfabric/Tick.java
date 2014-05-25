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

final class Tick { // TODO try persistent sets?

    static final int PEER_BITS = 24, TIME_BITS = 40;

    static final long TIME_MASK = (1L << TIME_BITS) - 1;

    static final long[] EMPTY = new long[0];

    private final long _value;

    Tick(long value) {
        _value = value;
    }

    @Override
    public int hashCode() {
        return (int) (_value ^ (_value >>> 32));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Tick)
            return _value == ((Tick) obj)._value;

        return false;
    }

    //

    static long get(int peer, long time) {
        if (Debug.ENABLED) {
            Debug.assertion((long) peer >>> PEER_BITS == 0);
            Debug.assertion(time >>> TIME_BITS == 0);
        }

        return (long) peer << TIME_BITS | time;
    }

    static int peer(long tick) {
        if (Debug.ENABLED)
            Debug.assertion(!Tick.isNull(tick));

        return (int) (tick >>> TIME_BITS);
    }

    static long time(long tick) {
        if (Debug.ENABLED)
            Debug.assertion(!Tick.isNull(tick));

        return tick & TIME_MASK;
    }

    static boolean happenedBefore(long tick, long[] map) {
        long max = getMax(map, peer(tick));
        return max != 0 && time(max) >= time(tick);
    }

    static int hashTick(long tick) {
        if (Debug.ENABLED)
            Debug.assertion(!isNull(tick));

        int hash = (int) (tick ^ (tick >>> 32));
        return hashPeer(hash);
    }

    static int hashPeer(int peer) {
        // TODO bench
        return TKeyed.rehash(peer);
    }

    /*
     * Set
     */

    static final long REMOVED = -1;

    static boolean isNull(long tick) {
        if (Debug.ENABLED)
            if (tick <= 0)
                Debug.assertion(tick == 0 || tick == -1);

        return tick <= 0;
    }

    static boolean contains(long[] set, long tick) {
        if (set != null) {
            if (Debug.ENABLED)
                checkSet(set);

            return index(set, tick) >= 0;
        }

        return false;
    }

    static int indexOf(long[] set, long tick) {
        if (set != null) {
            if (Debug.ENABLED)
                checkSet(set);

            return index(set, tick);
        }

        return -1;
    }

    private static int index(long[] set, long tick) {
        if (set.length > 0) {
            int index = hashTick(tick) & (set.length - 1);

            for (int i = OpenMap.attemptsStart(set.length); i >= 0; i--) {
                if (set[index] == 0)
                    break;

                if (set[index] == tick)
                    return index;

                index = (index + 1) & (set.length - 1);
            }
        }

        return -1;
    }

    static long[] add(long[] set, long tick) {
        if (set == null || set.length == 0)
            set = new long[OpenMap.CAPACITY];

        int hash = hashTick(tick);

        while (tryToAdd(set, tick, hash) < 0) {
            long[] previous = set;

            for (;;) {
                set = new long[set.length << OpenMap.TIMES_TWO_SHIFT];

                if (rehash(previous, set))
                    break;
            }
        }

        if (Debug.ENABLED)
            checkSet(set);

        return set;
    }

    static int tryToAdd(long[] set, long tick, int hash) {
        if (Debug.ENABLED)
            Debug.assertion(index(set, tick) < 0);

        int index = hash & (set.length - 1);

        for (int i = OpenMap.attemptsStart(set.length); i >= 0; i--) {
            if (set[index] <= 0) {
                if (Debug.ENABLED)
                    Debug.assertion(set[index] == 0 || set[index] == REMOVED);

                set[index] = tick;
                return index;
            }

            if (Debug.ENABLED)
                Debug.assertion(set[index] != tick);

            index = (index + 1) & (set.length - 1);
        }

        return -1;
    }

    static boolean rehash(long[] previous, long[] set) {
        for (int i = previous.length - 1; i >= 0; i--)
            if (!Tick.isNull(previous[i]))
                if (tryToAdd(set, previous[i], hashTick(previous[i])) < 0)
                    return false;

        return true;
    }

    static int remove(long[] set, long tick) {
        if (Debug.ENABLED) // Can't check set as might be empty
            Debug.assertion(set.length > 0);

        int index = hashTick(tick) & (set.length - 1);

        for (int i = OpenMap.attemptsStart(set.length); i >= 0; i--) {
            if (set[index] == tick) {
                set[index] = REMOVED;
                return index;
            }

            index = (index + 1) & (set.length - 1);
        }

        return -1;
    }

    // Debug
    static void checkSet(long[] set) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Debug.assertion(Utils.nextPowerOf2(set.length) == set.length);
        boolean empty = true;

        for (int i = 0; i < set.length; i++) {
            if (!Tick.isNull(set[i])) {
                Debug.assertion(index(set, set[i]) == i);
                empty = false;
            }

            for (int j = 0; j < i; j++)
                Debug.assertion(set[i] <= 0 || set[j] <= 0 || set[i] != set[j]);
        }

        if (set.length > 0)
            Debug.assertion(!empty);
    }

    /*
     * Peer to max tick map.
     */

    static long getMax(long[] map, int peer) {
        if (Debug.ENABLED)
            checkMap(map, true);

        return getMax_(map, peer);
    }

    private static long getMax_(long[] map, int peer) {
        int index = hashPeer(peer) & (map.length - 1);

        for (int i = OpenMap.attemptsStart(map.length); i >= 0; i--) {
            if (map[index] == 0)
                break;

            if (peer(map[index]) == peer)
                return map[index];

            index = (index + 1) & (map.length - 1);
        }

        return 0;
    }

    static long[] putMax(long[] map, long tick, boolean allowIncrease) {
        if (map == null)
            map = new long[OpenMap.CAPACITY];
        else if (Debug.ENABLED)
            checkMap(map, true);

        int hash = hashPeer(peer(tick));

        while (!tryToPutMax(map, tick, hash, allowIncrease)) {
            long[] previous = map;

            for (;;) {
                map = new long[map.length << OpenMap.TIMES_TWO_SHIFT];

                if (rehashMax(previous, map))
                    break;
            }
        }

        if (Debug.ENABLED)
            checkMap(map, false);

        return map;
    }

    private static boolean tryToPutMax(long[] map, long tick, int hash, boolean allowIncrease) {
        int index = hash & (map.length - 1);

        for (int i = OpenMap.attemptsStart(map.length); i >= 0; i--) {
            if (map[index] == 0) {
                map[index] = tick;
                return true;
            }

            if (peer(map[index]) == peer(tick)) {
                if (Debug.ENABLED)
                    Debug.assertion(allowIncrease);

                if (time(map[index]) < time(tick))
                    map[index] = tick;

                return true;
            }

            index = (index + 1) & (map.length - 1);
        }

        return false;
    }

    private static boolean rehashMax(long[] previous, long[] map) {
        for (int i = previous.length - 1; i >= 0; i--)
            if (!Tick.isNull(previous[i]))
                if (!tryToPutMax(map, previous[i], hashPeer(peer(previous[i])), false))
                    return false;

        return true;
    }

    // Debug

    static String toString(long tick) {
        char[] peer = new char[4];
        Utils.getBytesHex(Peer.get(peer(tick)).uid(), 0, 2, peer, 0);
        long bits = time(tick);
        bits = bits ^ (bits >>> 32);
        bits = bits ^ (bits >>> 16);
        char[] time = new char[10];
        Utils.getTimeHex(bits, time);
        return new String(peer) + "-" + new String(time, 6, 4) + " (" + Utils.getTickHex(tick) + ")";
    }

    static void checkMap(long[] map, boolean canBeEmpty) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Debug.assertion(map.length > 0 && Utils.nextPowerOf2(map.length) == map.length);
        boolean empty = true;

        for (int i = 0; i < map.length; i++) {
            if (map[i] != 0) {
                Debug.assertion(map[i] > 0);
                Debug.assertion(time(getMax_(map, peer(map[i]))) == time(map[i]));
                empty = false;
            }

            for (int j = 0; j < i; j++)
                Debug.assertion(map[i] == 0 || map[j] == 0 || peer(map[i]) != peer(map[j]));
        }

        if (!canBeEmpty)
            Debug.assertion(!empty);
    }
}
