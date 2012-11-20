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

@SuppressWarnings("rawtypes")
abstract class Serialization {

    private Serialization() {
    }

    private static final int STEP_PEER = 0;

    private static final int STEP_TIME = 1;

    @SuppressWarnings("fallthrough")
    static void writeTicks(ImmutableWriter writer, long[] ticks) {
        int step = STEP_PEER;
        int index = 0;

        if (writer.interrupted()) {
            step = writer.resumeInt();
            index = writer.resumeInt();
        }

        if (ticks != null && ticks.length > 0) {
            for (; index < ticks.length; index++) {
                if (!Tick.isNull(ticks[index])) {
                    switch (step) {
                        case STEP_PEER: {
                            writer.writeBinary(Peer.get(Tick.peer(ticks[index])).uid());

                            if (writer.interrupted()) {
                                writer.interruptInt(index);
                                writer.interruptInt(STEP_PEER);
                                return;
                            }
                        }
                        case STEP_TIME: {
                            if (!writer.canWriteLong()) {
                                writer.interruptInt(index);
                                writer.interruptInt(STEP_TIME);
                                return;
                            }

                            writer.writeLong(Tick.time(ticks[index]));
                            step = STEP_PEER;
                        }
                    }
                }
            }
        }

        writer.writeBinary(null);

        if (writer.interrupted()) {
            writer.interruptInt(index);
            writer.interruptInt(0);
            return;
        }
    }

    @SuppressWarnings({ "fallthrough", "null" })
    static long[] readTicks(ImmutableReader reader) {
        int step = STEP_PEER;
        Peer peer = null;
        long[] ticks = Tick.EMPTY;

        if (reader.interrupted()) {
            step = reader.resumeInt();
            peer = (Peer) reader.resume();
            ticks = (long[]) reader.resume();
        }

        for (;;) {
            switch (step) {
                case STEP_PEER: {
                    byte[] bytes = reader.readBinary();

                    if (reader.interrupted()) {
                        reader.interrupt(ticks);
                        reader.interrupt(peer);
                        reader.interruptInt(STEP_PEER);
                        return ticks;
                    }

                    if (bytes == null)
                        return ticks;

                    peer = Peer.get(new UID(bytes));
                }
                case STEP_TIME: {
                    if (!reader.canReadLong()) {
                        reader.interrupt(ticks);
                        reader.interrupt(peer);
                        reader.interruptInt(STEP_TIME);
                        return ticks;
                    }

                    long tick = Tick.get(peer.index(), reader.readLong());
                    ticks = Tick.add(ticks, tick);
                    step = STEP_PEER;
                }
            }
        }
    }

    static void writeTick(ImmutableWriter writer, long tick) {
        boolean peerDone = false;

        if (writer.interrupted())
            peerDone = writer.resumeBoolean();

        if (!peerDone) {
            writer.writeBinary(Peer.get(Tick.peer(tick)).uid());

            if (writer.interrupted()) {
                writer.interruptBoolean(false);
                return;
            }
        }

        if (!writer.canWriteLong()) {
            writer.interruptBoolean(true);
            return;
        }

        writer.writeLong(Tick.time(tick));
    }

    static long readTick(ImmutableReader reader) {
        Peer peer = null;

        if (reader.interrupted())
            peer = (Peer) reader.resume();

        if (peer == null) {
            byte[] bytes = reader.readBinary();

            if (reader.interrupted()) {
                reader.interrupt(peer);
                return 0;
            }

            peer = Peer.get(new UID(bytes));
        }

        if (!reader.canReadLong()) {
            reader.interrupt(peer);
            return 0;
        }

        long tick = reader.readLong();
        return Tick.get(peer.index(), tick);
    }

    //

    private static final int BLOCK_PEER_TICK = 0;

    private static final int BLOCK_LENGTH = 1;

    private static final int BLOCK_BUFFS = 2;

    private static final int BLOCK_REMOVALS = 3;

    private static final int BLOCK_REQUESTED = 4;

    @SuppressWarnings("fallthrough")
    static final int writeBlock(ImmutableWriter writer, Queue<Buff> queue, int room, Buff[] buffs, long[] removals, boolean requested) {
        int step = BLOCK_LENGTH;

        if (writer.interrupted())
            step = writer.resumeInt();

        switch (step) {
            case BLOCK_LENGTH: {
                if (!writer.canWriteInteger()) {
                    writer.interruptInt(BLOCK_LENGTH);
                    return room;
                }

                int length = 0;

                for (int i = 0; i < buffs.length; i++)
                    length += buffs[i].remaining();

                if (Debug.ENABLED)
                    Debug.assertion(length > 0);

                writer.writeInteger(length);
            }
            case BLOCK_BUFFS: {
                room = writeBuffs(writer, queue, buffs, room);

                if (writer.interrupted()) {
                    writer.interruptInt(BLOCK_BUFFS);
                    return room;
                }
            }
            case BLOCK_REMOVALS: {
                writeTicks(writer, removals);

                if (writer.interrupted()) {
                    writer.interruptInt(BLOCK_REMOVALS);
                    return room;
                }
            }
            case BLOCK_REQUESTED: {
                if (!writer.canWriteBoolean()) {
                    writer.interruptInt(BLOCK_REQUESTED);
                    return room;
                }

                writer.writeBoolean(requested);
            }
        }

        return room;
    }

    @SuppressWarnings({ "fallthrough", "null" })
    static final void readBlock(ImmutableReader reader, Connection connection, URI uri, View view) {
        int step = BLOCK_PEER_TICK;
        long tick = 0;
        int length = 0;
        Buff[] buffs = null;
        long[] removals = null;

        if (reader.interrupted()) {
            step = reader.resumeInt();
            tick = reader.resumeLong();
            length = reader.resumeInt();
            buffs = (Buff[]) reader.resume();
            removals = (long[]) reader.resume();
        }

        switch (step) {
            case BLOCK_PEER_TICK: {
                tick = Serialization.readTick(reader);

                if (reader.interrupted()) {
                    reader.interrupt(removals);
                    reader.interrupt(buffs);
                    reader.interruptInt(length);
                    reader.interruptLong(tick);
                    reader.interruptInt(BLOCK_PEER_TICK);
                    return;
                }
            }
            case BLOCK_LENGTH: {
                if (!reader.canReadInteger()) {
                    reader.interrupt(removals);
                    reader.interrupt(buffs);
                    reader.interruptInt(length);
                    reader.interruptLong(tick);
                    reader.interruptInt(BLOCK_LENGTH);
                    return;
                }

                length = reader.readInteger();

                if (length == 0)
                    throw new RuntimeException();
            }
            case BLOCK_BUFFS: {
                buffs = readBuffs(reader, length);

                if (reader.interrupted()) {
                    reader.interrupt(removals);
                    reader.interrupt(buffs);
                    reader.interruptInt(length);
                    reader.interruptLong(tick);
                    reader.interruptInt(BLOCK_BUFFS);
                    return;
                }
            }
            case BLOCK_REMOVALS: {
                removals = Serialization.readTicks(reader);

                if (reader.interrupted()) {
                    reader.interrupt(removals);
                    reader.interrupt(buffs);
                    reader.interruptInt(length);
                    reader.interruptLong(tick);
                    reader.interruptInt(BLOCK_REMOVALS);
                    return;
                }
            }
            case BLOCK_REQUESTED: {
                if (!reader.canReadBoolean()) {
                    reader.interrupt(removals);
                    reader.interrupt(buffs);
                    reader.interruptInt(length);
                    reader.interruptLong(tick);
                    reader.interruptInt(BLOCK_REQUESTED);
                    return;
                }

                boolean requested = reader.readBoolean();
                Object key;

                if (Debug.THREADS) {
                    for (int i = 0; i < buffs.length; i++)
                        ThreadAssert.exchangeGive(buffs, buffs[i]);

                    ThreadAssert.suspend(key = new Object());
                }

                if (Stats.ENABLED)
                    Stats.Instance.BlockReceived.incrementAndGet();

                if (view instanceof ServerView)
                    ((ServerView) view).readBlock(uri, tick, connection, buffs, removals, requested);

                if (view instanceof ClientView)
                    ((ClientView) view).readBlock(uri, tick, connection, buffs, removals, requested);

                if (Debug.THREADS) {
                    ThreadAssert.resume(key);
                    ThreadAssert.exchangeTake(buffs);
                }

                for (int i = 0; i < buffs.length; i++)
                    buffs[i].recycle();
            }
        }
    }

    //

    static int enqueueWritten(Queue<Buff> queue, Buff buff) {
        int position = buff.position();
        buff.reset();
        int marker = buff.position();

        if (position != marker) {
            Buff duplicate = buff.duplicate();
            duplicate.limit(position);
            queue.add(duplicate);
            buff.position(position);
            buff.mark();
        }

        return position - marker;
    }

    private static int writeBuffs(ImmutableWriter writer, Queue<Buff> queue, Buff[] buffs, int room) {
        Buff buff = writer.getBuff();
        int index = 0;

        if (writer.interrupted())
            index = writer.resumeInt();
        else
            room -= enqueueWritten(queue, buff);

        for (; index < buffs.length; index++) {
            if (room == 0) {
                writer.interruptInt(index);
                return 0;
            }

            if (room < buffs[index].remaining()) {
                int split = buffs[index].position() + room;
                Buff duplicate = buffs[index].duplicate();
                duplicate.limit(split);
                queue.add(duplicate);
                buffs[index].position(split);

                writer.interruptInt(index);
                return 0;
            }

            queue.add(buffs[index]);
            room -= buffs[index].remaining();

            if (room < buff.remaining())
                buff.limit(buff.position() + room);
        }

        return room;
    }

    @SuppressWarnings("unchecked")
    private static Buff[] readBuffs(ImmutableReader reader, int length) {
        List<Buff> buffs;
        int remaining = 0;

        if (reader.interrupted()) {
            buffs = (List) reader.resume();
            remaining = reader.resumeInt();
        } else {
            buffs = new List<Buff>();
            remaining = length;
        }

        Buff buff = reader.getBuff();

        if (buff.remaining() > 0) {
            Buff duplicate = buff.duplicate();
            buffs.add(duplicate);

            if (remaining <= duplicate.remaining()) {
                int split = buff.position() + remaining;
                buff.position(split);
                duplicate.limit(split);
                Buff[] array = new Buff[buffs.size()];
                buffs.copyToFixed(array);
                return array;
            }

            buff.position(buff.limit());
            remaining -= duplicate.remaining();
        }

        reader.interruptInt(remaining);
        reader.interrupt(buffs);
        return null;
    }

    //

    private static final int ADDRESS_SCHEME = 0;

    private static final int ADDRESS_HOST = 1;

    private static final int ADDRESS_PORT = 2;

    @SuppressWarnings("fallthrough")
    static void writeAddress(ImmutableWriter writer, Address address) {
        int step = ADDRESS_SCHEME;

        if (writer.interrupted())
            step = writer.resumeInt();

        switch (step) {
            case ADDRESS_SCHEME: {
                writer.writeString(address.Scheme);

                if (writer.interrupted()) {
                    writer.interruptInt(ADDRESS_SCHEME);
                    return;
                }
            }
            case ADDRESS_HOST: {
                writer.writeString(address.Host);

                if (writer.interrupted()) {
                    writer.interruptInt(ADDRESS_HOST);
                    return;
                }
            }
            case ADDRESS_PORT: {
                if (!writer.canWriteShort()) {
                    writer.interruptInt(ADDRESS_PORT);
                    return;
                }

                writer.writeShort((short) address.Port);
            }
        }
    }

    @SuppressWarnings("fallthrough")
    static Address readAddress(ImmutableReader reader) {
        int step = ADDRESS_SCHEME;
        String scheme = null, host = null;

        if (reader.interrupted()) {
            step = reader.resumeInt();
            scheme = (String) reader.resume();
            host = (String) reader.resume();
        }

        switch (step) {
            case ADDRESS_SCHEME: {
                scheme = reader.readString();

                if (reader.interrupted()) {
                    reader.interrupt(host);
                    reader.interrupt(scheme);
                    reader.interruptInt(ADDRESS_SCHEME);
                    return null;
                }
            }
            case ADDRESS_HOST: {
                host = reader.readString();

                if (reader.interrupted()) {
                    reader.interrupt(host);
                    reader.interrupt(scheme);
                    reader.interruptInt(ADDRESS_HOST);
                    return null;
                }
            }
            case ADDRESS_PORT: {
                if (!reader.canReadShort()) {
                    reader.interrupt(host);
                    reader.interrupt(scheme);
                    reader.interruptInt(ADDRESS_PORT);
                    return null;
                }

                int port = reader.readShort() & 0xffff;
                return new Address(scheme, host, port);
            }
            default:
                throw new IllegalStateException();
        }
    }

    //

    @SuppressWarnings("fallthrough")
    static void writeHeaders(ImmutableWriter writer, String[] headers) {
        int index = 0;

        if (writer.interrupted())
            index = writer.resumeInt();

        if (headers != null) {
            for (; index < headers.length; index++) {
                if (Debug.ENABLED)
                    Debug.assertion(headers[index] != null);

                writer.writeString(headers[index]);

                if (writer.interrupted()) {
                    writer.interruptInt(index);
                    return;
                }
            }
        }

        writer.writeString(null);

        if (writer.interrupted()) {
            writer.interruptInt(index);
            return;
        }
    }

    @SuppressWarnings("unchecked")
    static Headers readHeaders(ImmutableReader reader) {
        String name = null;
        Headers headers = null;

        if (reader.interrupted()) {
            name = (String) reader.resume();
            headers = (Headers) reader.resume();
        }

        for (;;) {
            String s = reader.readString();

            if (reader.interrupted()) {
                reader.interrupt(headers);
                reader.interrupt(name);
                return null;
            }

            if (s == null)
                return headers;

            if (name == null)
                name = s;
            else {
                if (headers == null)
                    headers = new Headers();

                headers.add(name, s);
                name = null;
            }
        }
    }

    //

    private static final int SUSPENSION_PEER = 0;

    private static final int SUSPENSION_TIME = 1;

    private static final int SUSPENSION_RANGE = 2;

    private static final int SUSPENSION_ID = 3;

    @SuppressWarnings("fallthrough")
    static void writeWorkspace(ImmutableWriter writer, long tick, byte[] range, byte id) {
        int step = SUSPENSION_PEER;

        if (writer.interrupted())
            step = writer.resumeInt();

        switch (step) {
            case SUSPENSION_PEER: {
                writer.writeBinary(Peer.get(Tick.peer(tick)).uid());

                if (writer.interrupted()) {
                    writer.interruptInt(SUSPENSION_PEER);
                    return;
                }
            }
            case SUSPENSION_TIME: {
                if (!writer.canWriteLong()) {
                    writer.interruptInt(SUSPENSION_TIME);
                    return;
                }

                writer.writeLong(Tick.time(tick));
            }
            case SUSPENSION_RANGE: {
                writer.writeBinary(range);

                if (writer.interrupted()) {
                    writer.interruptInt(SUSPENSION_RANGE);
                    return;
                }
            }
            case SUSPENSION_ID: {
                if (!writer.canWriteByte()) {
                    writer.interruptInt(SUSPENSION_ID);
                    return;
                }

                writer.writeByte(id);
            }
        }
    }

    static final class WorkspaceState {

        Peer Peer;

        long Time;

        byte[] Range;

        byte Id;
    }

    @SuppressWarnings("fallthrough")
    static WorkspaceState readWorkspace(ImmutableReader reader) {
        int step = SUSPENSION_PEER;
        WorkspaceState state;

        if (reader.interrupted()) {
            step = reader.resumeInt();
            state = (WorkspaceState) reader.resume();
        } else
            state = new WorkspaceState();

        switch (step) {
            case SUSPENSION_PEER: {
                byte[] value = reader.readBinary();

                if (reader.interrupted()) {
                    reader.interrupt(state);
                    reader.interruptInt(SUSPENSION_PEER);
                    return null;
                }

                state.Peer = Peer.get(new UID(value));
            }
            case SUSPENSION_TIME: {
                if (!reader.canReadLong()) {
                    reader.interrupt(state);
                    reader.interruptInt(SUSPENSION_TIME);
                    return null;
                }

                state.Time = reader.readLong();
            }
            case SUSPENSION_RANGE: {
                state.Range = reader.readBinary();

                if (reader.interrupted()) {
                    reader.interrupt(state);
                    reader.interruptInt(SUSPENSION_RANGE);
                    return null;
                }
            }
            case SUSPENSION_ID: {
                if (!reader.canReadByte()) {
                    reader.interrupt(state);
                    reader.interruptInt(SUSPENSION_ID);
                    return null;
                }

                state.Id = reader.readByte();
                return state;
            }
            default:
                throw new IllegalStateException();
        }
    }
}
