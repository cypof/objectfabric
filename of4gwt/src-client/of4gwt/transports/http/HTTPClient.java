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

import of4gwt.Privileged;
import of4gwt.Reader;
import of4gwt.Validator;
import of4gwt.misc.Bits;
import of4gwt.misc.Debug;
import of4gwt.misc.Log;
import of4gwt.misc.RuntimeIOException;
import of4gwt.transports.http.CometTransport.HTTPRequestBase;
import of4gwt.transports.http.CometTransport.HTTPRequestCallback;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.Timer;
import com.google.gwt.xhr.client.ReadyStateChangeHandler;
import com.google.gwt.xhr.client.XMLHttpRequest;

/**
 * Bidirectional communication with server using XMLHttpRequest (Comet). This transport is
 * designed to connect to an ObjectFabric socket server with an HTTP filter. This
 * transport can connect to a different server from the one serving the static files. This
 * allows Web applications where static files are served by e.g. Amazon S3 and dynamic
 * content is served by ObjectFabric. If cross domain calls are impossible, e.g. due to
 * https domain validation, you can either configure the static content web server as a
 * reverse proxy, or fall back to polling (Check RPCClient).
 */
public final class HTTPClient extends HTTPConnection {

    private static final String DEFAULT_PATH = "objectfabric";

    private static final int BUFFER_SIZE = Reader.LARGEST_UNSPLITABLE + CometTransport.MAX_CHUNK_SIZE;

    private static final int HEART_BEAT = 55000;

    private final Heartbeat _heartBeat = new Heartbeat();

    public HTTPClient() {
        this(GWT.getHostPageBaseURL() + DEFAULT_PATH);
    }

    public HTTPClient(String url) {
        this(url, null);
    }

    public HTTPClient(String url, Validator validator) {
        super(url, validator, true);
    }

    @Override
    protected HTTPRequestBase createRequest(Object url, boolean serverToClient) {
        return new HTTPRequest((String) url, serverToClient, _heartBeat);
    }

    public static final class HTTPRequest extends Privileged implements HTTPRequestBase, ReadyStateChangeHandler {

        private final String _url;

        private final boolean _serverToClient;

        private final Heartbeat _heartbeat;

        private XMLHttpRequest _request;

        private HTTPRequestCallback _callback;

        private int _offset;

        public HTTPRequest(String url, boolean serverToClient, Heartbeat heartbeat) {
            _url = url;
            _serverToClient = serverToClient;
            _heartbeat = heartbeat;

            ensureThreadContextBufferLength(BUFFER_SIZE);
        }

        public void close() {
            if (_request != null) {
                _request.clearOnReadyStateChange();
                _request.abort();
                _request = null;
            }
        }

        public byte[] getBuffer() {
            return getThreadContextBuffer();
        }

        public void setCallback(HTTPRequestCallback value) {
            _callback = value;
        }

        public void connect() {
            if (_heartbeat != null)
                _heartbeat.reset();

            close();

            int length = _callback.onWrite();

            if (length == 0) {
                if (Debug.ENABLED)
                    Debug.assertion(!_serverToClient);

                _callback.onDone();
            } else {
                try {
                    _request = XMLHttpRequest.create();
                    _request.open("POST", _url);

                    /*
                     * 'text/plain' type prevents browsers from sending a OPTIONS request
                     * first for cross-site calls.
                     */
                    _request.setRequestHeader("Content-type", "text/plain");
                    _request.setOnReadyStateChange(this);

                    /*
                     * Prevents browser from encoding data. This only applies to incoming
                     * data. POST data is still encoded, so use a bitset trick to have
                     * only positive bytes (regular ASCII).
                     */
                    overrideMimeType(_request, "text/plain; charset=x-user-defined");

                    byte[] buffer = getBuffer();
                    String data = "" + (char) buffer[CometTransport.FIELD_TYPE];
                    data += (char) buffer[CometTransport.FIELD_IS_ENCODED];
                    int encoderBits = 0, encoderIndex = 0;

                    for (int i = data.length(); i < length; i++) {
                        if (encoderIndex-- == 0) {
                            for (int t = 6; t >= 0; t--)
                                encoderBits = Bits.set(encoderBits, 6 - t, i + t < buffer.length && buffer[i + t] < 0);

                            data += (char) encoderBits;
                            encoderIndex = 6;
                        }

                        byte b = buffer[i];
                        data += (char) (b >= 0 ? b : -b - 1);
                    }

                    _request.send(data);
                    _offset = 0;
                } catch (Throwable t) {
                    Log.write(t);
                    _callback.onError(t);
                }
            }
        }

        private native void overrideMimeType(XMLHttpRequest request, String mimeType) /*-{
			request.overrideMimeType(mimeType);
        }-*/;

        public final void onReadyStateChange(XMLHttpRequest request) {
            if (Debug.ENABLED)
                Debug.assertion(request == _request);

            switch (request.getReadyState()) {
                case XMLHttpRequest.LOADING: {
                    if (request.getStatus() == Response.SC_OK) {
                        String data = request.getResponseText();
                        byte[] buffer = getBuffer();
                        int position = Reader.LARGEST_UNSPLITABLE;

                        for (; _offset < data.length(); _offset++)
                            buffer[position++] = (byte) data.charAt(_offset);

                        _callback.onRead(buffer, Reader.LARGEST_UNSPLITABLE, position);
                    } else {
                        String error = request.getStatusText();
                        _callback.onError(new RuntimeIOException(error));
                    }

                    break;
                }
                case XMLHttpRequest.DONE: {
                    int status = request.getStatus();

                    if (status == Response.SC_OK) {
                        if (_serverToClient)
                            connect();
                        else
                            _callback.onDone();
                    } else if (status == 0) {
                        String s = "Status 0. Might be cross domain issue.";
                        _callback.onError(new RuntimeIOException(s));
                    } else {
                        String error = request.getStatusText();
                        _callback.onError(new RuntimeIOException("" + status + " " + error));
                    }

                    break;
                }
            }
        }
    }

    private final class Heartbeat extends Timer {

        public void reset() {
            cancel();
            schedule(HEART_BEAT);
        }

        @Override
        public void run() {
            sendHeartbeat();
        }
    }
}
