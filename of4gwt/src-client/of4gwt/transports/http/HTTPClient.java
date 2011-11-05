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
import of4gwt.misc.Debug;
import of4gwt.misc.Log;
import of4gwt.transports.http.CometTransport.HTTPRequestBase;
import of4gwt.transports.http.CometTransport.HTTPRequestCallback;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;

/**
 * Bidirectional communication with server using XMLHttpRequest (Comet). This transport is
 * designed to connect to an ObjectFabric socket server with an HTTP filter. This
 * transport can connect to a different server from the one serving the static files. This
 * allows Web applications where static files are served by e.g. Amazon S3 and dynamic
 * content is served by ObjectFabric. If cross domain calls are impossible, e.g. due to
 * https domain validation, you can either configure the static content web server as a
 * reverse proxy, or fall back to polling (Check RPCClient).
 */
public final class HTTPClient extends HTTPClientBase {

    private static final String DEFAULT_PATH = "objectfabric";

    public HTTPClient() {
        this(GWT.getHostPageBaseURL() + DEFAULT_PATH);
    }

    public HTTPClient(String url) {
        this(url, null);
    }

    public HTTPClient(String url, Validator validator) {
        super(url, validator);
    }

    static {
        /*
         * Prevents canceling requests when pressing ESC. (Seems only Firefox & Safari)
         */
        Event.addNativePreviewHandler(new NativePreviewHandler() {

            @Override
            public void onPreviewNativeEvent(NativePreviewEvent e) {
                if (e.getTypeInt() == Event.getTypeInt(KeyDownEvent.getType().getName())) {
                    NativeEvent nativeEvent = e.getNativeEvent();

                    if (nativeEvent.getKeyCode() == KeyCodes.KEY_ESCAPE)
                        nativeEvent.preventDefault();
                }
            }
        });
    }

    @Override
    protected HTTPRequestBase createRequest(Object url, boolean serverToClient) {
        return createRequestStatic(url, serverToClient);
    }

    public static HTTPRequestBase createRequestStatic(Object url, boolean serverToClient) {
        HTTPRequest request = GWT.create(XMLHttpRequestImpl.class);
        request.init((String) url, serverToClient);
        return request;
    }

    abstract static class HTTPRequest extends Privileged implements HTTPRequestBase {

        /**
         * One buffer per side as write can be triggered any time during read.
         */
        private final byte[] _buffer = new byte[Reader.LARGEST_UNSPLITABLE + CometTransport.MAX_CHUNK_SIZE];

        private String _url;

        private boolean _serverToClient;

        private HTTPRequestCallback _callback;

        public final void init(String url, boolean serverToClient) {
            _url = url;
            _serverToClient = serverToClient;
        }

        public final byte[] getBuffer() {
            return _buffer;
        }

        public final String getURL() {
            return _url;
        }

        public final boolean getServerToClient() {
            return _serverToClient;
        }

        public final HTTPRequestCallback getCallback() {
            return _callback;
        }

        public final void setCallback(HTTPRequestCallback value) {
            _callback = value;
        }

        public abstract void close();

        public abstract void send(byte[] data, int length);

        public final void connect() {
            close();

            int length = _callback.onWrite();

            if (length == 0) {
                if (Debug.ENABLED)
                    Debug.assertion(!_serverToClient);

                _callback.onDone();
            } else {
                try {
                    send(getBuffer(), length);
                } catch (Exception e) {
                    Log.write(e);
                    _callback.onError(e);
                }
            }
        }
    }
}
