/**
 * Copyright (c) ObjectFabric Inc. All rights reserved.
 *
 * This file is part of ObjectFabric (objectfabric.com).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.objectfabric.transports.http;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import com.objectfabric.Connection;
import com.objectfabric.Privileged;
import com.objectfabric.Strings;
import com.objectfabric.misc.Bits;
import com.objectfabric.misc.ClosedConnectionException;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.Queue;
import com.objectfabric.misc.ThreadAssert;
import com.objectfabric.misc.Utils;
import com.objectfabric.transports.filters.Filter;
import com.objectfabric.transports.filters.FilterFactory;
import com.objectfabric.transports.http.HTTP.HTTPFilter;

/**
 * Created by the HTTP filter to represent a connection with a client. An HTTP connection
 * uses multiple socket connections, typically two, which are managed separately.
 */
final class HTTPSession extends Privileged implements Filter {

    private static final byte[] HEX = new byte[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    private static final int HEX_LENGTH_FIELD = 6;

    private static final int BIN_LENGTH_FIELD = 2;

    private final HTTP _http;

    private final byte[] _id;

    private final boolean _enableCrossOriginResourceSharing;

    private final byte _encoding;

    private Filter _next;

    //

    static final class FilterState {

        public static final int STATE_NEW = 0;

        public static final int STATE_STARTED = 1;

        public static final int STATE_IDLE = 2;

        public static final int STATE_RUNNING = 3;

        public static final int STATE_DISPOSE_REQUESTED = 4;

        public static final int STATE_DISPOSED = 5;

        //

        public final HTTPFilter Filter;

        public final int State;

        public FilterState(HTTPFilter filter, int state) {
            Filter = filter;
            State = state;
        }
    }

    //

    private static final FilterState NEW = new FilterState(null, FilterState.STATE_NEW);

    private static final FilterState STARTED_DISCONNECTED = new FilterState(null, FilterState.STATE_STARTED);

    private static final FilterState IDLE_DISCONNECTED = new FilterState(null, FilterState.STATE_IDLE);

    private static final FilterState DISPOSED = new FilterState(null, FilterState.STATE_DISPOSED);

    // Read side

    private final AtomicReference<FilterState> _reader = new AtomicReference<FilterState>(IDLE_DISCONNECTED);

    private int _offset, _length;

    // Write side

    private final AtomicReference<FilterState> _writer = new AtomicReference<FilterState>(NEW);

    private static final int MAX_RESPONSE_LENGTH = (int) 1e6; // TODO tune

    private boolean _wroteIds;

    private int _totalWritten;

    public HTTPSession(HTTP http, byte[] id, boolean enableCrossOriginResourceSharing, byte encoding) {
        _http = http;
        _id = id;
        _enableCrossOriginResourceSharing = enableCrossOriginResourceSharing;
        _encoding = encoding;
    }

    public byte[] getId() {
        return _id;
    }

    // Filter

    public void init(List<FilterFactory> factories, int index, boolean clientSide) {
        if (Debug.ENABLED)
            Debug.assertion(!clientSide);

        _next = factories.get(index + 1).createFilter(false);
        _next.init(factories, index + 1, clientSide);
        _next.setPrevious(this);
        _next.onReadStarted();

        // Start heartbeat and timeout

        Connection connection = _next.getConnection();

        if (connection != null) {
            connection.sendHeartbeatEvery(CometTransport.SERVER_HEARTBEAT);
            connection.enableTimeout(CometTransport.SERVER_TIMEOUT);
        }
    }

    public Filter getNext() {
        return _next;
    }

    public void setNext(Filter value) {
        _next = value;
    }

    public Filter getPrevious() {
        FilterState current = _writer.get();

        if (current.Filter != null)
            return current.Filter;

        return null;
    }

    public void setPrevious(Filter value) {
        throw new UnsupportedOperationException();
    }

    public Connection getConnection() {
        return _next.getConnection();
    }

    public void close() {
        _http.remove(this);

        if (Debug.ENABLED)
            setNoTransaction(true);

        closeRead();
        closeWrite();

        if (Debug.ENABLED)
            setNoTransaction(false);
    }

    /*
     * Read side.
     */

    private void closeRead() {
        for (;;) {
            FilterState current = _reader.get();

            switch (current.State) {
                case FilterState.STATE_IDLE: {
                    if (casReader(current, DISPOSED)) {
                        if (current.Filter != null)
                            current.Filter.close();

                        readClosed();
                        return;
                    }

                    break;
                }
                case FilterState.STATE_RUNNING: {
                    if (casReader(current, new FilterState(current.Filter, FilterState.STATE_DISPOSE_REQUESTED)))
                        return;

                    break;
                }
                case FilterState.STATE_DISPOSE_REQUESTED:
                case FilterState.STATE_DISPOSED:
                    return;
                default:
                    throw new AssertionError();
            }
        }
    }

    private void readClosed() {
        _next.onReadStopped(new ClosedConnectionException());
    }

    public boolean readAndReturnIfDone(HTTPFilter filter, ByteBuffer buffer) {
        boolean proceed = false;

        while (!proceed) {
            FilterState current = _reader.get();

            switch (current.State) {
                case FilterState.STATE_IDLE: {
                    if (casReader(current, filter.RUNNING))
                        proceed = true;

                    break;
                }
                case FilterState.STATE_RUNNING:
                case FilterState.STATE_DISPOSE_REQUESTED:
                    throw new RuntimeException(Strings.CONCURRENT_ACCESS);
                case FilterState.STATE_DISPOSED:
                    return true;
                default:
                    throw new AssertionError();
            }
        }

        boolean done = readAndReturnIfDone(buffer);

        for (;;) {
            FilterState current = _reader.get();

            switch (current.State) {
                case FilterState.STATE_RUNNING: {
                    if (Debug.ENABLED)
                        Debug.assertion(current == filter.RUNNING);

                    if (casReader(current, filter.IDLE))
                        return done;

                    break;
                }
                case FilterState.STATE_DISPOSE_REQUESTED: {
                    if (casReader(current, DISPOSED)) {
                        current.Filter.close();
                        readClosed();
                    }

                    return true;
                }
                default:
                    throw new AssertionError();
            }
        }
    }

    private final boolean casReader(FilterState expected, FilterState update) {
        boolean result = _reader.compareAndSet(expected, update);

        if (Debug.COMMUNICATIONS_LOG_HTTP) {
            String a = getString(expected.State) + " (" + expected.Filter + ")";
            String b = getString(update.State) + " (" + update.Filter + ")";
            Log.write("casReader: " + a + " -> " + b);
        }

        return result;
    }

    private final boolean readAndReturnIfDone(ByteBuffer buffer) {
        if (Debug.ENABLED)
            ThreadAssert.resume(_reader);

        boolean result = readAndReturnIfDoneImpl(buffer);

        if (Debug.ENABLED)
            ThreadAssert.suspend(_reader);

        return result;
    }

    private final boolean readAndReturnIfDoneImpl(ByteBuffer buffer) {
        if (buffer.remaining() == 0)
            return false;

        if (_offset == 0) {
            _length = (buffer.get() & 0xff) << 8;
            _offset++;
        }

        if (buffer.remaining() == 0)
            return false;

        if (_offset == 1) {
            _length |= buffer.get() & 0xff;
            _offset++;
        }

        if (Debug.ENABLED)
            Debug.assertion(buffer.remaining() <= _length);

        int before = buffer.position();
        getNext().read(buffer);
        _offset += buffer.position() - before;

        if (_offset == 2 + _length) {
            _offset = 0;
            return true;
        }

        return false;
    }

    /*
     * Write side.
     */

    /**
     * The connection on which initial data is sent becomes the writer. It first sends the
     * session id, which allows a secondary connection which becomes the reader.
     */
    public void readInitialConnection(HTTPFilter filter, ByteBuffer buffer, boolean first) {
        if (Debug.ENABLED) {
            Debug.assertion(_reader.get() == IDLE_DISCONNECTED);
            Debug.assertion(_writer.get() == NEW);

            if (first) {
                if (Debug.THREADS) {
                    ThreadAssert.assertCurrentIsEmpty();
                    ThreadAssert.addPrivate(_reader);
                }

                ThreadAssert.suspend(_reader);

                if (Debug.THREADS) {
                    ThreadAssert.assertCurrentIsEmpty();
                    ThreadAssert.addPrivate(_writer);
                }

                ThreadAssert.suspend(_writer);
            }
        }

        boolean done = readAndReturnIfDone(buffer);

        if (done) {
            _writer.set(new FilterState(filter, FilterState.STATE_STARTED));
            _next.onWriteStarted();
            requestWrite();
        }
    }

    public void requestWrite() {
        FilterState current = _writer.get();

        if (current.Filter != null)
            current.Filter.requestWrite();
    }

    private void closeWrite() {
        for (;;) {
            FilterState current = _writer.get();

            switch (current.State) {
                case FilterState.STATE_STARTED:
                case FilterState.STATE_IDLE: {
                    if (casWriter(current, DISPOSED)) {
                        if (current.Filter != null)
                            current.Filter.close();

                        writeClosed();
                        return;
                    }

                    break;
                }
                case FilterState.STATE_RUNNING: {
                    if (casWriter(current, new FilterState(current.Filter, FilterState.STATE_DISPOSE_REQUESTED)))
                        return;

                    break;
                }
                case FilterState.STATE_NEW:
                case FilterState.STATE_DISPOSE_REQUESTED:
                case FilterState.STATE_DISPOSED:
                    return;
            }
        }
    }

    private void writeClosed() {
        _next.onWriteStopped(new ClosedConnectionException());
    }

    public boolean write(HTTPFilter filter, ByteBuffer buffer, Queue<ByteBuffer> headers) {
        boolean proceed = false;

        while (!proceed) {
            FilterState current = _writer.get();

            switch (current.State) {
                case FilterState.STATE_NEW:
                    return true;
                case FilterState.STATE_STARTED: {
                    if (casWriter(current, filter.RUNNING)) {
                        if (_enableCrossOriginResourceSharing)
                            HTTP.addHeader(HTTP.OK_CHUNKED_CROSS_ORIGIN, buffer, headers);
                        else
                            HTTP.addHeader(HTTP.OK_CHUNKED, buffer, headers);

                        proceed = true;
                    }

                    break;
                }
                case FilterState.STATE_IDLE: {
                    if (_totalWritten > MAX_RESPONSE_LENGTH) {
                        if (casWriter(current, STARTED_DISCONNECTED)) {
                            _totalWritten = 0;
                            HTTP.addHeader(HTTP.EOF, buffer, headers);
                            current.Filter.endRequest();
                            return true;
                        }
                    }

                    if (casWriter(current, filter.RUNNING))
                        proceed = true;

                    break;
                }
                case FilterState.STATE_RUNNING:
                case FilterState.STATE_DISPOSE_REQUESTED: {
                    throw new RuntimeException(Strings.CONCURRENT_ACCESS);
                }
                case FilterState.STATE_DISPOSED:
                    return true;
            }
        }

        if (Debug.ENABLED)
            ThreadAssert.resume(_writer);

        boolean done;

        switch (_encoding) {
            case CometTransport.ENCODING_NONE:
                done = writeChunk(buffer);
                break;
            case CometTransport.ENCODING_PADDING:
                done = padChunk(buffer);
                break;
            case CometTransport.ENCODING_1_127:
                done = encodeChunk(buffer);
                break;
            default:
                throw new RuntimeException("");
        }

        if (Debug.ENABLED)
            ThreadAssert.suspend(_writer);

        for (;;) {
            FilterState current = _writer.get();

            switch (current.State) {
                case FilterState.STATE_RUNNING: {
                    if (Debug.ENABLED)
                        Debug.assertion(filter == current.Filter);

                    if (casWriter(current, filter.IDLE))
                        return done;

                    break;
                }
                case FilterState.STATE_DISPOSE_REQUESTED: {
                    if (casWriter(current, DISPOSED)) {
                        current.Filter.close();
                        writeClosed();
                        return true;
                    }

                    break;
                }
                default:
                    throw new AssertionError();
            }
        }
    }

    private final boolean casWriter(FilterState expected, FilterState update) {
        boolean result = _writer.compareAndSet(expected, update);

        if (Debug.COMMUNICATIONS_LOG_HTTP) {
            String a = getString(expected.State) + " (" + expected.Filter + ")";
            String b = getString(update.State) + " (" + update.Filter + ")";
            Log.write("casWriter: " + a + " -> " + b);
        }

        return result;
    }

    private boolean writeChunk(ByteBuffer buffer) {
        final int initial = buffer.position();
        final int chunkStart = initial + HEX_LENGTH_FIELD;
        buffer.position(chunkStart);

        writeUID(buffer);

        buffer.limit(buffer.limit() - 2); // For new line at end
        boolean done = getNext().write(buffer, null);
        buffer.limit(buffer.limit() + 2);

        if (buffer.position() == chunkStart) {
            /*
             * Empty.
             */
            buffer.position(initial);
        } else {
            int chunkEnd = buffer.position();
            int length = chunkEnd - chunkStart;
            buffer.position(initial);
            writeHexLength(buffer, length);

            if (Debug.ENABLED)
                Debug.assertion(buffer.position() == chunkStart);

            buffer.position(chunkEnd);
            buffer.put(HTTP.NEW_LINE);
            _totalWritten += buffer.position();

            if (Debug.COMMUNICATIONS_LOG_HTTP)
                Log.write(this + ": Written " + length);
        }

        return done;
    }

    /**
     * Pads buffer with zeros to reach minimum length.
     * {@link CometTransport#MIN_CHUNK_SIZE}.
     */
    private boolean padChunk(ByteBuffer buffer) {
        final int initial = buffer.position();
        final int chunkStart = initial + HEX_LENGTH_FIELD;
        buffer.position(chunkStart + BIN_LENGTH_FIELD);

        writeUID(buffer);

        buffer.limit(buffer.limit() - 2); // For new line at end
        boolean done = getNext().write(buffer, null);
        buffer.limit(buffer.limit() + 2);

        int chunkEnd = buffer.position();
        int length = chunkEnd - chunkStart;

        if (chunkEnd == chunkStart + BIN_LENGTH_FIELD) {
            /*
             * Empty.
             */
            buffer.position(initial);
        } else {
            int padded = length;

            if (padded < CometTransport.MIN_CHUNK_SIZE) {
                padded = CometTransport.MIN_CHUNK_SIZE;

                // Remove previous data from padding for security
                for (int i = chunkEnd; i < CometTransport.MIN_CHUNK_SIZE; i++)
                    buffer.put(i, (byte) 0);

                chunkEnd = chunkStart + CometTransport.MIN_CHUNK_SIZE;
            }

            buffer.position(initial);
            writeHexLength(buffer, padded);

            if (Debug.ENABLED)
                Debug.assertion(buffer.position() == chunkStart);

            buffer.put((byte) (length >>> 8));
            buffer.put((byte) (length & 0xff));
            buffer.position(chunkEnd);
            buffer.put(HTTP.NEW_LINE);
            _totalWritten += buffer.position();

            if (Debug.COMMUNICATIONS_LOG_HTTP)
                Log.write(this + ": Padded " + length + " -> " + (chunkEnd - chunkStart));
        }

        return done;
    }

    /**
     * {@link CometTransport#ENCODING_1_127}.
     */
    private boolean encodeChunk(ByteBuffer buffer) {
        final int initial = buffer.position();
        final int chunkStart = initial + HEX_LENGTH_FIELD;
        buffer.position(chunkStart);

        writeUID(buffer);

        // Reserve space at end of buffer for encoding
        {
            int blocks = buffer.remaining() / 8;
            buffer.limit(chunkStart + blocks * 6);
        }

        final boolean done = getNext().write(buffer, null);
        final int chunkEnd = buffer.position();

        if (chunkEnd == chunkStart) {
            /*
             * Empty.
             */
            buffer.position(initial);
        } else {
            int blocks = (chunkEnd - chunkStart) / 6 + 1;

            // TODO try only on first packet
            blocks = Math.max(blocks, CometTransport.MIN_CHUNK_SIZE / 8);

            int readIndex = chunkStart + blocks * 6;
            int writeIndex = chunkStart + blocks * 8;
            final int encodingEnd = writeIndex;
            buffer.limit(encodingEnd + 2); // For new line at end

            for (;;) {
                readIndex -= 6;
                writeIndex -= 8;

                if (readIndex < chunkStart)
                    break;

                int lz = Bits.set(0, 6), gz = Bits.set(0, 6);

                for (int i = 5; i >= 0; i--) {
                    boolean pad = readIndex + i >= chunkEnd;
                    byte value = pad ? 0 : buffer.get(readIndex + i);

                    if (value < 0) {
                        lz = Bits.set(lz, 5 - i);
                        value = (byte) (-value - 1);
                    }

                    if (value > 0)
                        gz = Bits.set(gz, 5 - i);
                    else
                        value = pad ? CometTransport.ENCODING_BYTE_PADDING : CometTransport.ENCODING_BYTE_ZERO;

                    buffer.put(writeIndex + 2 + i, value);
                }

                buffer.put(writeIndex + 0, (byte) lz);
                buffer.put(writeIndex + 1, (byte) gz);
            }

            //

            buffer.position(initial);
            writeHexLength(buffer, encodingEnd - chunkStart);

            //

            buffer.position(encodingEnd);
            buffer.put(HTTP.NEW_LINE);

            if (Debug.ENABLED)
                Debug.assertion(buffer.remaining() == 0);

            _totalWritten += encodingEnd - chunkStart;

            if (Debug.COMMUNICATIONS_LOG_HTTP)
                Log.write(this + ": Encoded " + (encodingEnd - chunkStart));
        }

        if (Debug.ENABLED)
            for (int i = initial; i < buffer.position(); i++)
                Debug.assertion(buffer.get(i) > 0 && buffer.get(i) <= 127);

        return done;
    }

    int ref = 1;

    private void writeUID(ByteBuffer buffer) {
        if (!_wroteIds) {
            _wroteIds = true;
            buffer.put(_id);
            buffer.put(PlatformAdapter.createUID()); // UID seed for GWT
        }
    }

    private static void writeHexLength(ByteBuffer buffer, int padded) {
        byte hex1 = HEX[(padded >>> 12) & 0xf];
        byte hex2 = HEX[(padded >>> 8) & 0xf];
        byte hex3 = HEX[(padded >>> 4) & 0xf];
        byte hex4 = HEX[(padded >>> 0) & 0xf];

        if (Debug.ENABLED) {
            String ref = Utils.padLeft(Integer.toHexString(padded), 4, '0');
            String text = new String(new char[] { (char) hex1, (char) hex2, (char) hex3, (char) hex4 });
            Debug.assertion(ref.equals(text));
        }

        buffer.put(hex1);
        buffer.put(hex2);
        buffer.put(hex3);
        buffer.put(hex4);
        buffer.put(HTTP.NEW_LINE);
    }

    /**
     * @param buffer
     */
    public boolean write(ByteBuffer buffer) {
        throw new UnsupportedOperationException();
    }

    /*
     * Common.
     */

    public void onFilterDisconnected(HTTPFilter filter) {
        for (;;) {
            FilterState reader = _reader.get();

            if (filter == reader.Filter) {
                switch (reader.State) {
                    case FilterState.STATE_IDLE: {
                        if (casReader(reader, IDLE_DISCONNECTED))
                            return;

                        break;
                    }
                    case FilterState.STATE_RUNNING:
                        throw new RuntimeException(Strings.CONCURRENT_ACCESS);
                    case FilterState.STATE_DISPOSE_REQUESTED:
                    case FilterState.STATE_DISPOSED:
                        return;
                }
            } else {
                FilterState writer = _writer.get();

                if (filter == writer.Filter) {
                    switch (writer.State) {
                        case FilterState.STATE_NEW:
                            throw new AssertionError();
                        case FilterState.STATE_STARTED: {
                            if (!_wroteIds) {
                                // Without id, client cannot reconnect anyway, close
                                close();
                                return;
                            }

                            if (casWriter(writer, STARTED_DISCONNECTED))
                                return;

                            break;
                        }
                        case FilterState.STATE_IDLE: {
                            if (casWriter(writer, STARTED_DISCONNECTED)) {
                                _totalWritten = 0;
                                return;
                            }

                            break;
                        }
                        case FilterState.STATE_RUNNING:
                            throw new RuntimeException(Strings.CONCURRENT_ACCESS);
                        case FilterState.STATE_DISPOSE_REQUESTED:
                        case FilterState.STATE_DISPOSED:
                            return;
                    }
                } else
                    break;
            }
        }
    }

    // Rest of Filter interface

    public void onReadStarted() {
        throw new UnsupportedOperationException();
    }

    public void onReadStopped(Exception e) {
        throw new UnsupportedOperationException();
    }

    public void onWriteStarted() {
        throw new UnsupportedOperationException();
    }

    public void onWriteStopped(Exception e) {
        throw new UnsupportedOperationException();
    }

    public void read(ByteBuffer buffer) {
        throw new UnsupportedOperationException();
    }

    public boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers) {
        throw new UnsupportedOperationException();
    }

    // Debug

    private static final String getString(int state) {
        switch (state) {
            case FilterState.STATE_NEW:
                return "NEW";
            case FilterState.STATE_STARTED:
                return "STARTED";
            case FilterState.STATE_IDLE:
                return "IDLE";
            case FilterState.STATE_RUNNING:
                return "RUNNING";
            case FilterState.STATE_DISPOSE_REQUESTED:
                return "DISPOSE_REQUESTED";
            case FilterState.STATE_DISPOSED:
                return "DISPOSED";
            default:
                throw new AssertionError();
        }
    }
}
