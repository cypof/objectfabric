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

package com.objectfabric.transports;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Test;

import com.objectfabric.Connection;
import com.objectfabric.TestsHelper;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.NIOConnection;
import com.objectfabric.misc.NIOListener;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformThread;
import com.objectfabric.misc.Queue;
import com.objectfabric.misc.SeparateVM;
import com.objectfabric.transports.filters.Filter;
import com.objectfabric.transports.filters.FilterFactory;
import com.objectfabric.transports.http.HTTP;
import com.objectfabric.transports.http.NIOTestHTTPClient;

public class NIOTestHTTP extends TestsHelper {

    public static final int WRITES = (int) 1e6;

    private static final ConcurrentHashMap<HTTPSession, HTTPSession> _sessions = new ConcurrentHashMap<HTTPSession, HTTPSession>();

    @Test
    public void runJava1() throws Exception {
        run(1, true, false);
    }

    @Test
    public void runJava8() throws Exception {
        run(8, true, false);
    }

    public void run(int count, boolean launchClients, boolean debug) throws Exception {
        Debug.ProcessName = "Server";

        final HTTP factory = new HTTP();

        NIOListener server = new NIOListener() {

            @Override
            public NIOConnection createConnection() {
                Log.write("Connection");
                return new HTTPSession(factory);
            }
        };

        server.start(NIOTest.PORT);
        ArrayList<Process> clients = new ArrayList<Process>();

        if (launchClients) {
            for (int i = 0; i < count; i++) {
                Process client = SeparateVM.start(NIOTestHTTPClient.class.getName(), debug, debug);
                clients.add(client);
            }
        }

        while (_sessions.size() < count)
            PlatformThread.sleep(1);

        for (HTTPSession session : _sessions.keySet()) {
            session._algo.start();
            requestRun(session);
        }

        while (_sessions.size() > 0)
            PlatformThread.sleep(1);

        if (launchClients)
            for (Process client : clients)
                client.waitFor();

        server.stop();
        PlatformAdapter.reset();
    }

    public static final class HTTPSession extends NIOConnection {

        private final HTTP _factory;

        private final Filter _filter;

        private final DataGen _algo = new DataGen(NIOTestHTTPClient.WRITES, WRITES);

        public HTTPSession(HTTP factory) {
            _factory = factory;
            _filter = _factory.createFilter(false);

            _filter.setPrevious(new FilterBase() {

                @Override
                public void close() {
                    HTTPSession.this.close();
                }

                @Override
                public void requestWrite() {
                    HTTPSession.this.requestRun();
                }
            });

            List<FilterFactory> list = new List<FilterFactory>();

            list.add(factory);

            list.add(new FilterFactory() {

                public Filter createFilter(boolean clientSide) {
                    return new FilterBase() {

                        @Override
                        public void init(List<FilterFactory> factories, int index, boolean clientSide_) {
                        }

                        @Override
                        public Filter getNext() {
                            return null;
                        }

                        @Override
                        public void setPrevious(Filter value) {
                        }

                        @Override
                        public Connection getConnection() {
                            return null;
                        }

                        @Override
                        public void onReadStarted() {
                            super.onReadStarted();

                            _sessions.put(HTTPSession.this, HTTPSession.this);
                        }

                        @Override
                        public void read(ByteBuffer buffer) {
                            _algo.read(buffer.array(), buffer.position(), buffer.limit());
                            buffer.position(buffer.limit());

                            if (_algo.isDone()) {
                                HTTPSession removed = _sessions.remove(HTTPSession.this);

                                if (removed != null)
                                    Log.write("Session: Success!!");
                            }
                        }

                        @Override
                        public boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers) {
                            int length = _algo.write(buffer.array(), buffer.position(), buffer.limit());
                            buffer.position(buffer.position() + (length >= 0 ? length : -length - 1));

                            if (_algo.isDone()) {
                                HTTPSession removed = _sessions.remove(HTTPSession.this);

                                if (removed != null)
                                    Log.write("Session: Success!!");
                            }

                            return length < 0;
                        }
                    };
                }
            });

            _filter.init(list, 0, false);
            _algo.setConnection(this);
        }

        @Override
        protected void onWriteStopped(Exception e) {
            super.onWriteStopped(e);

            Log.write("Disconnection");
        }

        @Override
        protected void read(ByteBuffer buffer) {
            _filter.read(buffer);
        }

        @Override
        protected boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers) {
            return _filter.write(buffer, headers);
        }

        private class FilterBase implements Filter {

            public void onReadStarted() {
            }

            public void onReadStopped(Exception _) {
            }

            public void onWriteStarted() {
            }

            public void onWriteStopped(Exception _) {
            }

            //

            public void read(ByteBuffer buffer) {
                throw new UnsupportedOperationException();
            }

            public boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers) {
                throw new UnsupportedOperationException();
            }

            public void init(List<FilterFactory> factories, int index, boolean clientSide) {
                throw new UnsupportedOperationException();
            }

            public Filter getPrevious() {
                throw new UnsupportedOperationException();
            }

            public void setPrevious(Filter value) {
                throw new UnsupportedOperationException();
            }

            public Connection getConnection() {
                throw new UnsupportedOperationException();
            }

            public Filter getNext() {
                throw new UnsupportedOperationException();
            }

            public void setNext(Filter value) {
                throw new UnsupportedOperationException();
            }

            public void close() {
                throw new UnsupportedOperationException();
            }

            public void requestWrite() {
                throw new UnsupportedOperationException();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        NIOTestHTTP test = new NIOTestHTTP();
        // test.socket8();

        for (int i = 0; i < 100; i++)
            test.run(1, false, false);
    }
}
