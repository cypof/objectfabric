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

import org.objectfabric.CloseCounter.Callback;
import org.objectfabric.InFlight.Provider;

@SuppressWarnings({ "serial", "rawtypes" })
abstract class Connection extends BlockQueue implements Runnable, Provider {

    // Per resource commands

    static final byte COMMAND_PERMISSION = 0;

    static final byte COMMAND_GET_KNOWN = 1;

    // TODO send delta instead of full array?
    static final byte COMMAND_ON_KNOWN = 2;

    static final byte COMMAND_GET_BLOCK = 3;

    static final byte COMMAND_CANCEL_BLOCK = 4;

    static final byte COMMAND_ON_BLOCK = 5;

    static final byte COMMAND_ACK_BLOCK = 6;

    static final byte COMMAND_SUBSCRIBE = 7;

    static final byte COMMAND_UNSUBSCRIBE = 8;

    static final byte COMMAND_UNRESOLVED = 9;

    // Shared commands

    static final byte COMMAND_HEADERS = 10;

    static final byte COMMAND_ADDRESS = 11;

    private static final Permission[] PERMISSIONS = Permission.values();

    //

    private final Location _location;

    private Session _session;

    private Headers _headers;

    // Read thread

    private Address _address;

    private final ImmutableReader _reader = new ImmutableReader(new List<Object>());

    private final byte[] _leftover = new byte[Buff.getLargestUnsplitable()];

    private int _leftoverSize = -1;

    // Write thread

    private final ImmutableWriter _writer = new ImmutableWriter(new List<Object>());

    private final Queue<Buff> _buffs = new Queue<Buff>();

    private final PlatformConcurrentQueue<Write> _writes = new PlatformConcurrentQueue<Write>();

    private final PlatformMap<URI, ServerView> _subscribed;

    private static final int WRITE_IDLE = 0, WRITE_ONGOING = 1, WRITE_ONGOING_INTERRUPTED = 2;

    private volatile int _writeStatus;

    Connection(Location location, Headers nativeHeaders) {
        _location = location;
        _headers = nativeHeaders;

        if (Debug.THREADS) {
            ThreadAssert.exchangeGiveList(_reader, _reader.getThreadContextObjects());
            ThreadAssert.exchangeGiveList(_writer, _writer.getThreadContextObjects());
            ThreadAssert.exchangeGive(_writer, this);
        }

        post(new Write() {

            @Override
            void run(Connection connection) {
                connection._writer.putByte(TObject.SERIALIZATION_VERSION);
            }
        }, false);

        if (_location instanceof Remote) {
            final Remote remote = (Remote) _location;
            final Headers headers = remote.headers();

            if (headers != null)
                postHeaders(headers, false);

            post(new Write() {

                @Override
                void run(Connection connection) {
                    connection.write(COMMAND_ADDRESS, null, 0, null);
                }

                @Override
                int runEx(Connection connection, Queue<Buff> queue, int room) {
                    Serialization.writeAddress(connection._writer, remote.address());
                    return room;
                }
            }, false);

            _subscribed = null;
        } else
            _subscribed = new PlatformMap<URI, ServerView>();

        requestRun();
    }

    @Override
    void onClose(Callback callback) {
        if (_subscribed != null)
            for (ServerView view : _subscribed.values())
                view.unsubscribe(this);

        closeRead();
        closeWrite();

        super.onClose(callback);
    }

    //

    final Location location() {
        return _location;
    }

    final Address address() {
        return _address;
    }

    final PlatformMap<URI, ServerView> subscribed() {
        return _subscribed;
    }

    final ImmutableWriter writer() {
        return _writer;
    }

    //

    final void post(Write write) {
        post(write, true);
    }

    final void post(Write write, boolean push) {
        _writes.add(write);

        if (Stats.ENABLED)
            Stats.Instance.ConnectionQueues.incrementAndGet();

        if (push)
            requestRun();
    }

    final void postHeaders(final Headers headers, boolean push) {
        post(new Write() {

            @Override
            void run(Connection connection) {
                connection.write(COMMAND_HEADERS, null, 0, null);
            }

            @Override
            int runEx(Connection connection, Queue<Buff> queue, int room) {
                Serialization.writeHeaders(connection._writer, headers.asStrings());
                return room;
            }
        }, push);
    }

    final void postPermission(final URI uri, final Permission permission, boolean push) {
        post(new Write() {

            @Override
            void run(Connection connection) {
                connection.write(COMMAND_PERMISSION, uri.path(), 0, null);
            }

            @Override
            int runEx(Connection connection, Queue<Buff> queue, int room) {
                if (!connection._writer.canWriteByte()) {
                    connection._writer.interrupt(null);
                    return 0;
                }

                connection._writer.writeByte((byte) permission.ordinal());
                return room;
            }
        }, push);
    }

    final void postSubscribe(final URI uri) {
        post(new Write() {

            @Override
            void run(Connection connection) {
                connection.write(Connection.COMMAND_SUBSCRIBE, uri.path(), 0, null);
            }
        });
    }

    final void postUnsubscribe(final URI uri) {
        post(new Write() {

            @Override
            void run(Connection connection) {
                connection.write(Connection.COMMAND_UNSUBSCRIBE, uri.path(), 0, null);
            }
        });
    }

    final void postKnown(final URI uri, final long[] ticks) {
        post(new Write() {

            @Override
            void run(Connection connection) {
                connection.write(Connection.COMMAND_ON_KNOWN, uri.path(), 0, ticks);
            }
        });
    }

    final void postGet(final URI uri, final long tick) {
        post(new Write() {

            @Override
            void run(Connection connection) {
                if (InFlight.starting(uri, tick, connection))
                    connection.write(COMMAND_GET_BLOCK, uri.path(), tick, null);
            }
        });

        if (Stats.ENABLED)
            Stats.Instance.BlockRequestsSent.incrementAndGet();
    }

    @Override
    public final void cancel(final URI uri, final long tick) {
        post(new Write() {

            @Override
            void run(Connection connection) {
                connection.write(COMMAND_CANCEL_BLOCK, uri.path(), tick, null);
            }
        });
    }

    final void postAck(final URI uri, final long tick) {
        post(new Write() {

            @Override
            void run(Connection connection) {
                connection.write(COMMAND_ACK_BLOCK, uri.path(), tick, null);
            }
        });
    }

    /*
     * IO reader thread.
     */

    final boolean resumeRead() {
        boolean started;

        if (Debug.ENABLED) {
            started = Helper.instance().startRead(this);

            if (started) {
                ThreadAssert.resume(_reader, false);

                if (Debug.THREADS)
                    ThreadAssert.exchangeTake(_reader);
            }
        }

        boolean result = !isClosingOrClosed();

        if (!result) {
            if (Debug.ENABLED)
                Helper.instance().assertReadClosing(this);

            if (Debug.THREADS && started)
                ThreadAssert.removePrivateList(_reader.getThreadContextObjects());
        }

        return result;
    }

    final void read(Buff buff) {
        int initialLimit;

        if (Debug.ENABLED)
            initialLimit = buff.limit();

        if (!Debug.RANDOMIZE_TRANSFER_LENGTHS)
            readImpl(buff);
        else {
            int limit = buff.limit();

            while (buff.position() < limit) {
                int rand = Platform.get().randomInt(limit - buff.position() + 1);
                buff.limit(Math.min(buff.position() + rand, limit));
                readImpl(buff);
                Debug.assertion(buff.remaining() == 0);
            }
        }

        if (Debug.ENABLED) {
            Debug.assertion(buff.limit() == initialLimit);
            Debug.assertion(buff.remaining() == 0);
        }
    }

    final void suspendRead() {
        if (Debug.ENABLED) {
            if (Helper.instance().stopRead(this))
                ThreadAssert.suspend(_reader);
            else if (Debug.THREADS)
                ThreadAssert.removePrivateList(_reader.getThreadContextObjects());
        }
    }

    private final void closeRead() {
        if (Debug.ENABLED) {
            if (Helper.instance().closeRead(this)) {
                Object key = new Object();
                ThreadAssert.suspend(key);
                ThreadAssert.resume(_reader, false);

                if (Debug.THREADS) {
                    ThreadAssert.exchangeTake(_reader);
                    ThreadAssert.removePrivateList(_reader.getThreadContextObjects());
                }

                ThreadAssert.resume(key);
            }
        }
    }

    private final void readImpl(Buff buff) {
        if (Debug.ENABLED)
            Debug.assertion(buff.position() >= Buff.getLargestUnsplitable());

        if (buff.remaining() > 0) {
            _reader.setBuff(buff);

            if (_leftoverSize < 0)
                _reader.startRead();
            else {
                buff.position(buff.position() - _leftoverSize);
                buff.putImmutably(_leftover, 0, _leftoverSize);
            }

            if (Debug.ENABLED)
                buff.lock(buff.limit());

            readImpl();

            _leftoverSize = buff.remaining();
            buff.getImmutably(_leftover, 0, _leftoverSize);
            buff.position(buff.limit());
        }
    }

    private static final int STEP_READ_CODE = 0;

    private static final int STEP_READ_URI = 1;

    private static final int STEP_READ_COMMAND = 2;

    private final void readImpl() {
        for (;;) {
            int step = 0;
            byte code = -1;
            String path = null;
            URI uri = null;

            if (_reader.interrupted()) {
                step = _reader.resumeInt();
                code = _reader.resumeByte();
                path = (String) _reader.resume();
                uri = (URI) _reader.resume();
            }

            switch (step) {
                case STEP_READ_CODE: {
                    if (!_reader.canReadByte()) {
                        _reader.interrupt(uri);
                        _reader.interrupt(path);
                        _reader.interruptByte(code);
                        _reader.interruptInt(STEP_READ_CODE);
                        return;
                    }

                    code = _reader.readByte(Writer.DEBUG_TAG_CONNECTION);

                    if (Debug.COMMUNICATIONS_LOG)
                        Log.write("Read command: " + getCommandString(code));
                }
                case STEP_READ_URI: {
                    if (code < COMMAND_HEADERS) { // TODO ew
                        path = _reader.readString();

                        if (_reader.interrupted()) {
                            _reader.interrupt(uri);
                            _reader.interrupt(path);
                            _reader.interruptByte(code);
                            _reader.interruptInt(STEP_READ_URI);
                            return;
                        }

                        if (_location instanceof Server)
                            uri = ((Server) _location).resolver().resolve(_address, path);
                        else
                            uri = ((Origin) _location).uris().get(path);
                    }
                }
                case STEP_READ_COMMAND: {
                    switch (code) {
                        case COMMAND_PERMISSION: {
                            if (!_reader.canReadByte()) {
                                _reader.interrupt(uri);
                                _reader.interrupt(path);
                                _reader.interruptByte(code);
                                _reader.interruptInt(STEP_READ_COMMAND);
                                return;
                            }

                            byte ordinal = _reader.readByte();

                            if (uri != null) {
                                ClientView view = (ClientView) uri.getOrCreate(_location);
                                view.readPermission(uri, PERMISSIONS[ordinal]);
                            }

                            break;
                        }
                        case COMMAND_GET_KNOWN: {
                            // TODO
                            break;
                        }
                        case COMMAND_ON_KNOWN: {
                            long[] ticks = Serialization.readTicks(_reader);

                            if (_reader.interrupted()) {
                                _reader.interrupt(uri);
                                _reader.interrupt(path);
                                _reader.interruptByte(code);
                                _reader.interruptInt(STEP_READ_COMMAND);
                                return;
                            }

                            if (uri != null) {
                                if (ticks == null)
                                    ticks = Tick.EMPTY;

                                onKnown(uri, ticks);
                            }

                            break;
                        }
                        case COMMAND_GET_BLOCK: {
                            long tick = Serialization.readTick(_reader);

                            if (_reader.interrupted()) {
                                _reader.interrupt(uri);
                                _reader.interrupt(path);
                                _reader.interruptByte(code);
                                _reader.interruptInt(STEP_READ_COMMAND);
                                return;
                            }

                            if (uri != null) {
                                ServerView view = (ServerView) uri.getOrCreate(_location);
                                view.readGetBlock(uri, tick, this);
                            }

                            if (Stats.ENABLED)
                                Stats.Instance.BlockRequestsReceived.incrementAndGet();

                            break;
                        }
                        case COMMAND_CANCEL_BLOCK: {
                            long tick = Serialization.readTick(_reader);

                            if (_reader.interrupted()) {
                                _reader.interrupt(uri);
                                _reader.interrupt(path);
                                _reader.interruptByte(code);
                                _reader.interruptInt(STEP_READ_COMMAND);
                                return;
                            }

                            if (uri != null) {
                                ServerView view = (ServerView) uri.getOrCreate(_location);
                                view.readCancelBlock(uri, tick, this);
                            }

                            break;
                        }
                        case COMMAND_ON_BLOCK: {
                            View view = null;

                            if (uri != null)
                                view = uri.getOrCreate(_location);

                            Serialization.readBlock(_reader, this, uri, view);

                            if (_reader.interrupted()) {
                                _reader.interrupt(uri);
                                _reader.interrupt(path);
                                _reader.interruptByte(code);
                                _reader.interruptInt(STEP_READ_COMMAND);
                                return;
                            }

                            break;
                        }
                        case COMMAND_ACK_BLOCK: {
                            long tick = Serialization.readTick(_reader);

                            if (_reader.interrupted()) {
                                _reader.interrupt(uri);
                                _reader.interrupt(path);
                                _reader.interruptByte(code);
                                _reader.interruptInt(STEP_READ_COMMAND);
                                return;
                            }

                            if (uri != null) {
                                ClientView view = (ClientView) uri.getOrCreate(_location);
                                view.readAck(uri, tick);
                            }

                            if (Stats.ENABLED)
                                Stats.Instance.AckReceived.incrementAndGet();

                            break;
                        }
                        case COMMAND_SUBSCRIBE: {
                            if (uri != null) {
                                if (_session != null) {
                                    final URI uri_ = uri;

                                    _session.onRequest(uri, new PermissionCallback() {

                                        @Override
                                        public void set(Permission permission) {
                                            if (permission == Permission.NONE)
                                                postPermission(uri_, permission, true);
                                            else
                                                onPermission(uri_, permission);
                                        }
                                    });
                                } else
                                    onPermission(uri, Permission.WRITE);
                            } else {
                                final String path_ = path;

                                post(new Write() {

                                    @Override
                                    void run(Connection connection) {
                                        connection.write(COMMAND_UNRESOLVED, path_, 0, null);
                                    }
                                });
                            }

                            break;
                        }
                        case COMMAND_UNSUBSCRIBE: {
                            if (uri != null) {
                                ServerView view = (ServerView) uri.getOrCreate(_location);
                                view.readUnsubscribe(uri, this);
                            }

                            break;
                        }
                        case COMMAND_UNRESOLVED: {
                            if (uri != null) {
                                ClientView view = (ClientView) uri.getOrCreate(_location);
                                view.readUnresolved(uri);
                            }

                            break;
                        }
                        case COMMAND_HEADERS: {
                            _headers = Serialization.readHeaders(_reader);

                            if (_reader.interrupted()) {
                                _reader.interrupt(uri);
                                _reader.interrupt(path);
                                _reader.interruptByte(code);
                                _reader.interruptInt(STEP_READ_COMMAND);
                                return;
                            }

                            break;
                        }
                        case COMMAND_ADDRESS: {
                            _address = Serialization.readAddress(_reader);

                            if (_reader.interrupted()) {
                                _reader.interrupt(uri);
                                _reader.interrupt(path);
                                _reader.interruptByte(code);
                                _reader.interruptInt(STEP_READ_COMMAND);
                                return;
                            }

                            _session = onConnection(_headers);
                            _headers = null;
                            break;
                        }
                        default:
                            throw new IllegalStateException();
                    }
                }
            }
        }
    }

    protected Session onConnection(Headers headers) {
        return null;
    }

    private final void onPermission(URI uri, Permission permission) {
        ((ServerView) uri.getOrCreate(_location)).onPermission(uri, this, permission);
    }

    void onKnown(URI uri, long[] ticks) {
        ClientView view = (ClientView) uri.getOrCreate(_location);
        view.readKnown(uri, ticks);
    }

    final void onBlock(URI uri, View view, long tick, Buff[] buffs, long[] removals, boolean requested, boolean ack) {
        Exception exception = uri.onBlock(view, tick, buffs, removals, requested, this, ack, null);

        if (exception != null)
            Log.write(exception);
    }

    /*
     * IO writer thread.
     */

    @Override
    protected void enqueue() {
        Platform.get().execute(this);
    }

    @Override
    public void run() {
        if (onRunStarting()) {
            if (Debug.ENABLED)
                ThreadAssert.resume(_writer, false);

            if (Debug.THREADS)
                ThreadAssert.exchangeTake(_writer);

            runMessages(false);

            if (_writeStatus == WRITE_IDLE)
                write();

            if (Debug.ENABLED)
                ThreadAssert.suspend(_writer);

            onRunEnded(false);
        }
    }

    abstract void write();

    final Queue<Buff> fill(int limit) {
        boolean done = fillImpl(limit);

        if (_buffs.size() > 0) {
            _writeStatus = done ? WRITE_ONGOING : WRITE_ONGOING_INTERRUPTED;
            return _buffs;
        }

        return null;
    }

    final void writeComplete() {
        if (_writeStatus == WRITE_ONGOING)
            _writeStatus = WRITE_IDLE;
        else if (_writeStatus == WRITE_ONGOING_INTERRUPTED)
            _writeStatus = WRITE_IDLE;
        else
            throw new IllegalStateException();

        requestRun();
    }

    private final void closeWrite() {
        Object key;

        if (Debug.ENABLED) {
            ThreadAssert.suspend(key = new Object());
            ThreadAssert.resume(_writer, false);
        }

        if (Debug.THREADS)
            ThreadAssert.exchangeTake(_writer);

        if (Stats.ENABLED) {
            for (;;) {
                Write message = _writes.poll();

                if (message == null)
                    break;

                Stats.Instance.ConnectionQueues.decrementAndGet();
            }
        }

        recycleBlocks();

        while (_buffs.size() > 0)
            _buffs.poll().recycle();

        if (Debug.THREADS) {
            ThreadAssert.removePrivateList(_writer.getThreadContextObjects());
            ThreadAssert.removePrivate(this);
        }

        if (Debug.ENABLED)
            ThreadAssert.resume(key);
    }

    //

    private final boolean fillImpl(int limit) {
        int room = limit;

        for (int i = 0; i < _buffs.size(); i++)
            room -= _buffs.get(i).remaining();

        boolean done = write(_buffs, room);

        if (Debug.ENABLED) {
            for (int i = 0; i < _buffs.size(); i++) {
                Debug.assertion(_buffs.get(i).getDuplicates() > 0);
                Debug.assertion(_buffs.get(i).remaining() != 0);
            }

            int total = 0;

            for (int i = 0; i < _buffs.size(); i++)
                total += _buffs.get(i).remaining();

            Debug.assertion(total <= limit);
        }

        return done;
    }

    private final boolean write(Queue<Buff> queue, int room) {
        Buff buff = Buff.getOrCreate();
        _writer.setBuff(buff);

        for (;;) {
            if (!Debug.RANDOMIZE_TRANSFER_LENGTHS)
                room = writeImpl(queue, room);
            else {
                for (;;) {
                    int position = buff.position();
                    int room1 = Platform.get().randomInt(room - 1) + 1;
                    int room2 = writeImpl(queue, room1);
                    room -= room1 - room2;
                    room -= Serialization.enqueueWritten(queue, buff);

                    if (_writer.interrupted() && room1 > Buff.getLargestUnsplitable())
                        break;

                    if (!_writer.interrupted() && buff.position() == position)
                        break;
                }
            }

            boolean exit = !_writer.interrupted() || buff.limit() < buff.capacity();
            int position = buff.position();
            buff.reset();
            buff.limit(position);

            if (Debug.ENABLED)
                buff.lock(buff.limit());

            if (buff.remaining() > 0) {
                queue.add(buff);
                room -= buff.remaining();
            } else
                buff.recycle();

            if (exit) {
                _writer.setBuff(null);
                return !_writer.interrupted();
            }

            buff = Buff.getOrCreate();
            _writer.setBuff(buff);
        }
    }

    private static final int STEP_RUN = 0;

    private static final int STEP_RUN_EX = 1;

    @SuppressWarnings("fallthrough")
    private final int writeImpl(Queue<Buff> queue, int room) {
        Buff buff = _writer.getBuff();
        buff.limit(Math.min(buff.position() + room, buff.capacity()));

        for (;;) {
            int step = STEP_COMMAND;
            Write write;

            if (_writer.interrupted()) {
                step = _writer.resumeInt();
                write = (Write) _writer.resume();
            } else {
                write = _writes.poll();

                if (Stats.ENABLED && write != null)
                    Stats.Instance.ConnectionQueues.decrementAndGet();

                if (write == null)
                    write = nextBlock();

                if (write == null)
                    return room;

                if (Debug.THREADS)
                    ThreadAssert.exchangeTake(this);
            }

            switch (step) {
                case STEP_RUN: {
                    write.run(this);

                    if (_writer.interrupted()) {
                        _writer.interrupt(write);
                        _writer.interruptInt(STEP_RUN);
                        return room;
                    }
                }
                case STEP_RUN_EX: {
                    room = write.runEx(this, queue, room);

                    if (_writer.interrupted()) {
                        _writer.interrupt(write);
                        _writer.interruptInt(STEP_RUN_EX);
                        return room;
                    }
                }
            }
        }
    }

    private static final int STEP_COMMAND = 0;

    private static final int STEP_URI = 1;

    private static final int STEP_BLOCK = 2;

    private static final int STEP_SET = 3;

    final void write(byte command, String path, long tick, long[] ticks) {
        int step = STEP_COMMAND;

        if (_writer.interrupted())
            step = _writer.resumeInt();

        switch (step) {
            case STEP_COMMAND: {
                if (!_writer.canWriteByte()) {
                    _writer.interruptInt(STEP_COMMAND);
                    return;
                }

                _writer.writeByte(command, Writer.DEBUG_TAG_CONNECTION);

                if (Debug.COMMUNICATIONS_LOG)
                    Log.write("Write command: " + getCommandString(command));
            }
            case STEP_URI: {
                if (path != null) {
                    _writer.writeString(path);

                    if (_writer.interrupted()) {
                        _writer.interruptInt(STEP_URI);
                        return;
                    }
                }
            }
            case STEP_BLOCK: {
                if (tick != 0) {
                    Serialization.writeTick(_writer, tick);

                    if (_writer.interrupted()) {
                        _writer.interruptInt(STEP_BLOCK);
                        return;
                    }
                }
            }
            case STEP_SET: {
                if (ticks != null) {
                    Serialization.writeTicks(_writer, ticks);

                    if (_writer.interrupted()) {
                        _writer.interruptInt(STEP_SET);
                        return;
                    }
                }
            }
        }
    }

    static abstract class Write {

        abstract void run(Connection connection);

        int runEx(Connection connection, Queue<Buff> queue, int room) {
            return room;
        }
    }

    // Debug

    public static String getCommandString(int code) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        switch (code) {
            case COMMAND_GET_KNOWN:
                return "GET_KNOWN";
            case COMMAND_PERMISSION:
                return "PERMISSION";
            case COMMAND_ON_KNOWN:
                return "ON_KNOWN";
            case COMMAND_GET_BLOCK:
                return "GET_BLOCK";
            case COMMAND_CANCEL_BLOCK:
                return "CANCEL_BLOCK";
            case COMMAND_ON_BLOCK:
                return "ON_BLOCK";
            case COMMAND_ACK_BLOCK:
                return "ACK_BLOCK";
            case COMMAND_SUBSCRIBE:
                return "SUBSCRIBE";
            case COMMAND_UNSUBSCRIBE:
                return "UNSUBSCRIBE";
            case COMMAND_UNRESOLVED:
                return "UNRESOLVED";
            case COMMAND_HEADERS:
                return "HEADERS";
            case COMMAND_ADDRESS:
                return "ADDRESS";
            default:
                throw new IllegalStateException();
        }
    }
}
