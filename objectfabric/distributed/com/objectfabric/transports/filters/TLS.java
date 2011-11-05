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

package com.objectfabric.transports.filters;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;

import com.objectfabric.Connection;
import com.objectfabric.Reader;
import com.objectfabric.Strings;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.Queue;

/**
 * Implements Transport Layer Security, for e.g. secure sockets and https.
 */
public class TLS implements FilterFactory {

    private final SSLContext _ssl;

    // Debug :
    // System.setProperty("javax.net.debug", "all");

    public TLS(SSLContext ssl) {
        _ssl = ssl;
    }

    public Filter createFilter(boolean clientSide) {
        SSLEngine engine = _ssl.createSSLEngine();
        engine.setUseClientMode(clientSide);
        return new TLSFilter(engine);
    }

    private static final class TLSFilter implements Filter {

        private static final class Context {

            public ByteBuffer ReadBuffer, WritePacketBuffer, WriteApplicationBuffer;
        }

        private static final ThreadLocal<Context> _context = new ThreadLocal<Context>() {

            @Override
            protected Context initialValue() {
                return new Context();
            }
        };

        private static final ConcurrentLinkedQueue<ByteBuffer> _underflows = new ConcurrentLinkedQueue<ByteBuffer>();

        private final SSLEngine _engine;

        private final Queue<ByteBuffer> _headers = new Queue<ByteBuffer>();

        private ByteBuffer _underflow;

        private Filter _previous, _next;

        private boolean _handshakeDone;

        public TLSFilter(SSLEngine engine) {
            _engine = engine;
        }

        public void init(List<FilterFactory> factories, int index, boolean clientSide) {
            _next = factories.get(index + 1).createFilter(clientSide);
            _next.init(factories, index + 1, clientSide);
            _next.setPrevious(this);
        }

        public Filter getPrevious() {
            return _previous;
        }

        public void setPrevious(Filter value) {
            _previous = value;
        }

        public Filter getNext() {
            return _next;
        }

        public void setNext(Filter value) {
            _next = value;
        }

        public Connection getConnection() {
            return _next.getConnection();
        }

        //

        public void close() {
            _previous.close();
        }

        public void requestWrite() {
            _previous.requestWrite();
        }

        //

        public void onReadStarted() {
            _next.onReadStarted();
        }

        public void onReadStopped(Exception e) {
            _next.onReadStopped(e);
        }

        public void onWriteStarted() {
            _next.onWriteStarted();
        }

        public void onWriteStopped(Exception e) {
            _next.onWriteStopped(e);
        }

        public void read(ByteBuffer buffer) {
            Context context = _context.get();

            if (context.ReadBuffer == null) {
                int capacity = _engine.getSession().getApplicationBufferSize();
                // TODO use direct buffers
                context.ReadBuffer = ByteBuffer.allocate(capacity);
            }

            final int startPosition = Reader.LARGEST_UNSPLITABLE;

            for (;;) {
                context.ReadBuffer.position(startPosition);
                context.ReadBuffer.limit(context.ReadBuffer.capacity());
                SSLEngineResult result;

                try {
                    if (_underflow == null)
                        result = _engine.unwrap(buffer, context.ReadBuffer);
                    else {
                        if (_underflow.remaining() >= buffer.remaining())
                            _underflow.put(buffer);
                        else {
                            byte[] bytes = new byte[_underflow.remaining()];
                            buffer.get(bytes);
                            _underflow.put(bytes);
                        }

                        _underflow.flip();
                        result = _engine.unwrap(_underflow, context.ReadBuffer);
                    }
                } catch (SSLException ex) {
                    throw new RuntimeException(ex);
                }

                if (Debug.COMMUNICATIONS_LOG_TLS)
                    log("Unwrap: ", result);

                switch (result.getStatus()) {
                    case OK:
                        if (_underflow != null && _underflow.remaining() == 0) {
                            _underflow.clear();
                            _underflows.add(_underflow);
                            _underflow = null;
                        }

                        break;
                    case BUFFER_OVERFLOW:
                        throw new AssertionError();
                    case BUFFER_UNDERFLOW:
                        if (_underflow != null) {
                            _underflow.limit(_underflow.capacity());
                            _underflow.position(_underflow.limit());
                        }

                        if (buffer.remaining() != 0) {
                            if (_underflow == null) {
                                _underflow = _underflows.poll();

                                if (_underflow == null) {
                                    int capacity = _engine.getSession().getPacketBufferSize();
                                    // TODO use direct buffers
                                    _underflow = ByteBuffer.allocate(capacity);
                                }
                            }

                            _underflow.put(buffer);
                        }

                        break;
                    case CLOSED:
                        throw new RuntimeException(Strings.SSLENGINE_CLOSED);
                }

                switch (result.getHandshakeStatus()) {
                    case NOT_HANDSHAKING:
                        if (context.ReadBuffer.position() == startPosition)
                            return;

                        break;
                    case FINISHED:
                        _handshakeDone = true;
                        requestWrite();
                        break;
                    case NEED_TASK: { // TODO: Thread pool?
                        Runnable task;

                        while ((task = _engine.getDelegatedTask()) != null)
                            task.run();

                        break;
                    }
                    case NEED_WRAP: {
                        requestWrite();
                        return;
                    }
                    case NEED_UNWRAP:
                        if (buffer.remaining() == 0)
                            return;

                        break;
                }

                context.ReadBuffer.limit(context.ReadBuffer.position());
                context.ReadBuffer.position(startPosition);

                _next.read(context.ReadBuffer);

                if (Debug.ENABLED)
                    Debug.assertion(context.ReadBuffer.position() == context.ReadBuffer.limit());
            }
        }

        public boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers) {
            Context context = _context.get();

            if (context.WritePacketBuffer == null) {
                int capacity = _engine.getSession().getPacketBufferSize();
                // TODO use direct buffers
                context.WritePacketBuffer = ByteBuffer.allocate(capacity);

                capacity = _engine.getSession().getApplicationBufferSize();
                // TODO use direct buffers
                context.WriteApplicationBuffer = ByteBuffer.allocate(capacity);
            }

            context.WritePacketBuffer.clear();
            boolean done = write(context.WritePacketBuffer, context.WriteApplicationBuffer);
            context.WritePacketBuffer.flip();

            if (context.WritePacketBuffer.remaining() > 0)
                headers.add(context.WritePacketBuffer);

            return done;
        }

        private boolean write(ByteBuffer packet, ByteBuffer application) {
            for (;;) {
                boolean done = true;

                if (_handshakeDone && _headers.size() == 0) {
                    application.clear();
                    done = _next.write(application, _headers);
                    application.flip();
                }

                SSLEngineResult result;

                try {
                    if (_headers.size() == 0)
                        result = _engine.wrap(application, packet);
                    else {
                        ByteBuffer[] array = new ByteBuffer[_headers.size() + 1];

                        for (int i = 0; i < array.length - 1; i++)
                            array[i] = _headers.get(i);

                        array[array.length - 1] = application;
                        result = _engine.wrap(array, packet);
                    }
                } catch (SSLException ex) {
                    throw new RuntimeException(ex);
                }

                if (Debug.COMMUNICATIONS_LOG_TLS)
                    log("Wrap: ", result);

                switch (result.getStatus()) {
                    case OK:
                        break;
                    case BUFFER_OVERFLOW:
                    case BUFFER_UNDERFLOW:
                        throw new AssertionError();
                    case CLOSED:
                        throw new RuntimeException(Strings.SSLENGINE_CLOSED);
                }

                switch (result.getHandshakeStatus()) {
                    case NEED_TASK: { // TODO: Thread pool?
                        Runnable task;

                        while ((task = _engine.getDelegatedTask()) != null)
                            task.run();

                        break;
                    }
                    case NEED_WRAP:
                        requestWrite();
                        return false;
                    case FINISHED:
                        _handshakeDone = true;
                        requestWrite();
                        return done;
                    case NEED_UNWRAP:
                        return true;
                    case NOT_HANDSHAKING:
                        return done;
                }
            }
        }
    }

    // Debug

    private static boolean resultOnce = true;

    private static void log(String str, SSLEngineResult result) {
        if (!Debug.COMMUNICATIONS_LOG_TLS)
            throw new IllegalStateException();

        if (resultOnce) {
            resultOnce = false;
            System.out.println("The format of the SSLEngineResult is: \n" + "\t\"getStatus() / getHandshakeStatus()\" +\n" + "\t\"bytesConsumed() / bytesProduced()\"\n");
        }

        HandshakeStatus hsStatus = result.getHandshakeStatus();
        Log.write(str + result.getStatus() + "/" + hsStatus + ", " + result.bytesConsumed() + "/" + result.bytesProduced() + " bytes");

        if (hsStatus == HandshakeStatus.FINISHED)
            Log.write("\t...ready for application data");
    }
}
