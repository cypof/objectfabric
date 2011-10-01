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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map.Entry;

import sun.net.www.http.HttpClient;

import com.objectfabric.Reader;
import com.objectfabric.Validator;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformThreadPool;
import com.objectfabric.transports.Client;
import com.objectfabric.transports.http.CometTransport.HTTPRequestBase;
import com.objectfabric.transports.http.CometTransport.HTTPRequestCallback;

/**
 * Bidirectional communication with server over HTTP (Comet). This transport is designed
 * to connect to an ObjectFabric SocketServer with an HTTP filter.
 */
public final class HTTPClient extends HTTPConnection implements Client {

    // TODO: send heartbeat

    public HTTPClient(URL url) {
        this(url, null);
    }

    public HTTPClient(URL url, Validator validator) {
        super(url, validator, false);
    }

    @Override
    protected HTTPRequestBase createRequest(Object url, boolean serverToClient) {
        return new HTTPRequest((URL) url, serverToClient);
    }

    public static final class HTTPRequest implements HTTPRequestBase {

        private final URL _url;

        private final boolean _serverToClient;

        private final byte[] _buffer = new byte[8192];

        private HTTPRequestCallback _callback;

        private volatile boolean _running = true;

        private volatile URLConnection _request;

        public HTTPRequest(URL url, boolean serverToClient) {
            _url = url;
            _serverToClient = serverToClient;
        }

        public byte[] getBuffer() {
            return _buffer;
        }

        public void setCallback(HTTPRequestCallback value) {
            _callback = value;
        }

        public void close() {
            _running = false;

            for (;;) {
                URLConnection request = _request;

                if (request == null)
                    break;

                try {
                    /*
                     * Can't reliably abort request, so hack...
                     */
                    Class c = Class.forName("sun.net.www.protocol.http.HttpURLConnection");
                    HttpClient http = (HttpClient) PlatformAdapter.getPrivateField(request, "http", c);

                    if (http != null) {
                        c = Class.forName("sun.net.NetworkClient");
                        Socket socket = (Socket) PlatformAdapter.getPrivateField(http, "serverSocket", c);
                        socket.close();
                    }
                } catch (Exception e) {
                    break; // Ignore
                }
            }
        }

        public void connect() {
            PlatformThreadPool.getInstance().execute(new Runnable() {

                public void run() {
                    try {
                        while (_running) {
                            int length = _callback.onWrite();

                            if (length == 0) {
                                if (Debug.ENABLED)
                                    Debug.assertion(!_serverToClient);
                            } else
                                call(length);

                            if (_serverToClient) {
                                // Comet connection never exits
                            } else {
                                _callback.onDone();
                                break;
                            }
                        }
                    } catch (Throwable t) {
                        onThrowable(t);
                    }
                }

                private final void call(int length) {
                    OutputStream output = null;
                    InputStream input = null;

                    try {
                        _request = _url.openConnection();
                        _request.setDoInput(true);
                        _request.setDoOutput(true);
                        _request.setRequestProperty("Content-Type", "text/plain");
                        _request.setRequestProperty("Accept", "text/plain");

                        output = _request.getOutputStream();
                        output.write(_buffer, 0, length);

                        input = _request.getInputStream();

                        if (Debug.COMMUNICATIONS_LOG_HTTP)
                            for (Entry<String, List<String>> headers : _request.getHeaderFields().entrySet())
                                Log.write(headers.toString());

                        while (_running) {
                            int read = input.read(_buffer, Reader.LARGEST_UNSPLITABLE, _buffer.length - Reader.LARGEST_UNSPLITABLE);

                            if (_serverToClient) {
                                if (Debug.ENABLED) {
                                    // Comet chunks have no content length
                                    Debug.assertion(_request.getContentLength() == -1);
                                }

                                /*
                                 * End of request, will need to reconnect.
                                 */
                                if (read < 0)
                                    break;
                            } else {
                                if (Debug.ENABLED) {
                                    Debug.assertion(_request.getContentLength() == 0);
                                    Debug.assertion(read == -1);
                                }

                                // Write side ACK, nothing to do
                                break;
                            }

                            _callback.onRead(_buffer, Reader.LARGEST_UNSPLITABLE, Reader.LARGEST_UNSPLITABLE + read);
                        }
                    } catch (IOException ex) {
                        if (_running) {
                            _running = false;
                            _callback.onError(ex);
                        }

                        return;
                    } finally {
                        if (output != null) {
                            try {
                                output.close();
                            } catch (IOException _) {
                                // Ignore
                            }
                        }

                        if (input != null) {
                            try {
                                input.close();
                            } catch (IOException _) {
                                // Ignore
                            }
                        }

                        _request = null;
                    }
                }
            });
        }
    }
}
