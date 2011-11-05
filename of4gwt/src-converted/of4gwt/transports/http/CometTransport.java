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

package of4gwt.transports.http;

import of4gwt.Schedulable;
import of4gwt.misc.Debug;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.ThreadAssert;

/**
 * HTTP requires two socket connections for true bidirectional communication as the Comet
 * request cannot send data while it is waiting for server chunks. This class implements a
 * simple protocol where each request has a type byte to specify if it is an initial
 * connection or on which side it is. Data is encoded if clients require it.
 */
abstract class CometTransport extends Schedulable {

    /**
     * Otherwise browsers wait for several chunks to arrive instead of invoking client
     * callback immediately. Max seems to be Chrome, does not work with 1000.
     * <nl>
     * TODO: try to do only for first packet
     */
    public static final int MIN_CHUNK_SIZE = 1024;

    public static final int MAX_CHUNK_SIZE = 65535; // For lengths encoding (HEX & binary)

    /**
     * Offset of fields in a request.
     */
    public static final int FIELD_TYPE = 0;

    public static final int FIELD_REQUEST_ENCODING = 1;

    public static final int FIELD_RESPONSE_ENCODING = 2;

    public static final int FIELD_ID = 3;

    //

    /*
     * Values are irrelevant but must pass transports without encoding. Using uncommon
     * values allows the HTTP filter to reject non-ObjectFabric requests faster.
     */

    public static final byte TYPE_CONNECTION = 42;

    public static final byte TYPE_SERVER_TO_CLIENT = 43;

    public static final byte TYPE_CLIENT_TO_SERVER = 44;

    public static final byte TYPE_HEARTBEAT = 45;

    public static final byte TYPE_MIN = 42, TYPE_MAX = 45;

    //

    /**
     * If client can send binary data, e.g. a native app or a browser supporting XHR 2, no
     * need to encode.
     */
    public static final byte ENCODING_NONE = 42;

    /**
     * Simply pad the end of buffer to reach {@link #MIN_CHUNK_SIZE}.
     */
    public static final byte ENCODING_PADDING = 43;

    /**
     * Default encoding for POST data is to remove negative bytes so that browsers UTF8
     * encoding leaves data alone.
     */
    public static final byte ENCODING_0_127 = 44;

    /**
     * Special case for IE (for requests and responses) and Firefox 3 (for requests), that
     * cannot transfer zeros. For IE, it seems that directive "charset=x-user-defined" is
     * not fully respected for responses. Base64 would be good too but needs a way to send
     * a continuous stream, so it would need to identify block ends etc.
     */
    public static final byte ENCODING_1_127 = 45;

    //

    public static final byte ENCODING_BYTE_ZERO = 42;

    public static final byte ENCODING_BYTE_PADDING = 43;

    //

    // TODO: make configurable
    public static final int SERVER_HEARTBEAT = 15;

    public static final int CLIENT_TIMEOUT = 20;

    public static final int CLIENT_HEARTBEAT = 55;

    public static final int SERVER_TIMEOUT = 60;

    protected interface HTTPRequestBase {

        void setCallback(HTTPRequestCallback value);

        void close();

        byte[] getBuffer();

        void connect();
    }

    protected interface HTTPRequestCallback {

        int onWrite();

        void onRead(byte[] buffer, int offset, int limit);

        void onDone();

        void onError(Exception e);
    }

    private final HTTPRequestBase _reader, _writer;

    private byte[] _id;

    private byte[] _seed = new byte[PlatformAdapter.UID_BYTES_COUNT];

    private int _headerOffset;

    protected CometTransport() {
        _reader = createRequest(true);

        if (Debug.ENABLED) {
            if (Debug.THREADS) {
                ThreadAssert.assertCurrentIsEmpty();
                ThreadAssert.addPrivate(_reader);
            }

            ThreadAssert.suspend(_reader);
        }

        _reader.setCallback(new HTTPRequestCallback() {

            public int onWrite() {
                return write(_reader.getBuffer(), true);
            }

            public void onRead(byte[] buffer, int offset, int limit) {
                readChunk(buffer, offset, limit);
            }

            public void onDone() {
            }

            public void onError(Exception e) {
                CometTransport.this.onError(e);
            }
        });

        _writer = createRequest(false);

        if (Debug.ENABLED) {
            if (Debug.THREADS) {
                ThreadAssert.assertCurrentIsEmpty();
                ThreadAssert.addPrivate(_writer);
            }

            ThreadAssert.suspend(_writer);
        }

        _writer.setCallback(new HTTPRequestCallback() {

            public int onWrite() {
                if (Debug.ENABLED)
                    assertScheduled();

                onRunStarting();
                return write(_writer.getBuffer(), false);
            }

            public void onRead(byte[] buffer, int offset, int length) {
                throw new UnsupportedOperationException();
            }

            public void onDone() {
                onRunEnded();
            }

            public void onError(Exception e) {
                CometTransport.this.onError(e);
            }
        });
    }

    //

    protected abstract HTTPRequestBase createRequest(boolean serverToClient);

    protected abstract void read(byte[] buffer, int offset, int limit);

    protected abstract int write(byte[] buffer, int offset, int limit);

    protected abstract void onError(Exception e);

    //

    protected void connect() {
        onStarting();
        _reader.connect();
    }

    protected void close() {
        _reader.close();
        _writer.close();
    }

    //

    private final void readChunk(byte[] buffer, int offset, int limit) {
        if (Debug.ENABLED)
            ThreadAssert.resume(_reader);

        final int ID_START = 0;
        final int ID_END = PlatformAdapter.UID_BYTES_COUNT;
        final int SEED_START = ID_END;
        final int SEED_END = ID_END + PlatformAdapter.UID_BYTES_COUNT;

        boolean headerDone = _headerOffset == SEED_END;
        boolean started = false;

        if (!headerDone) {
            if (_headerOffset >= ID_START && _headerOffset < ID_END) {
                if (_id == null)
                    _id = new byte[PlatformAdapter.UID_BYTES_COUNT];

                int read = Math.min(ID_END - _headerOffset, limit - offset);
                PlatformAdapter.arraycopy(buffer, offset, _id, _headerOffset - ID_START, read);
                offset += read;
                _headerOffset += read;
            }

            if (_headerOffset >= SEED_START && _headerOffset < SEED_END) {
                int read = Math.min(SEED_END - _headerOffset, limit - offset);
                PlatformAdapter.arraycopy(buffer, offset, _seed, _headerOffset - SEED_START, read);
                offset += read;
                _headerOffset += read;
            }

            if (_headerOffset == SEED_END) {
                PlatformAdapter.initializeUIDGenerator(_seed);
                _seed = null;
                headerDone = started = true;
            }
        }

        if (headerDone)
            read(buffer, offset, limit);

        if (started)
            onStarted();

        if (Debug.ENABLED)
            ThreadAssert.suspend(_reader);
    }

    final void requestRunAccessor() {
        requestRun();
    }
    
    @Override
    protected void startRun() {
        _writer.connect();
    }
    
    private final int write(byte[] buffer, boolean connection) {
        if (Debug.ENABLED)
            ThreadAssert.resume(_writer);

        int result = writeImpl(buffer, connection);

        if (Debug.ENABLED)
            ThreadAssert.suspend(_writer);

        return result;
    }

    private final int writeImpl(byte[] buffer, boolean connection) {
        byte command;

        if (connection)
            command = _id == null ? TYPE_CONNECTION : TYPE_SERVER_TO_CLIENT;
        else
            command = TYPE_CLIENT_TO_SERVER;

        buffer[FIELD_TYPE] = command;
        int start = FIELD_ID;

        if (_id != null) {
            for (int i = 0; i < PlatformAdapter.UID_BYTES_COUNT; i++)
                buffer[start + i] = _id[i];

            start += PlatformAdapter.UID_BYTES_COUNT;
        }

        if (command == TYPE_SERVER_TO_CLIENT)
            return start;

        int written = write(buffer, start + 2, Math.min(buffer.length, MAX_CHUNK_SIZE));

        if (written < 0)
            written = -written - 1;
        else
            requestRun();

        if (command == TYPE_CLIENT_TO_SERVER && written == 0)
            return 0;

        buffer[start + 0] = (byte) (written >> 8);
        buffer[start + 1] = (byte) (written & 0xff);

        return start + 2 + written;
    }
}
