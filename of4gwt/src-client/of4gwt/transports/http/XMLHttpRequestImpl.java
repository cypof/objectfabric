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

import of4gwt.Reader;
import of4gwt.misc.Bits;
import of4gwt.misc.Debug;
import of4gwt.misc.RuntimeIOException;
import of4gwt.transports.http.HTTPClient.HTTPRequest;

import com.google.gwt.http.client.Response;
import com.google.gwt.xhr.client.ReadyStateChangeHandler;
import com.google.gwt.xhr.client.XMLHttpRequest;

class XMLHttpRequestImpl extends HTTPRequest implements ReadyStateChangeHandler {

    private XMLHttpRequest _request;

    private int _offset, _padLength, _padOffset;

    @Override
    public void close() {
        if (_request != null) {
            _request.abort();

            /*
             * This can be delayed by a timer, so no way to make sure is done before
             * abort. Use test _request == request in callback instead.
             */
            _request.clearOnReadyStateChange();
            _request = null;
        }
    }

    @Override
    public void send(byte[] data, int length) {
        _request = XMLHttpRequest.create();
        _request.open("POST", getURL());

        /*
         * 'text/plain' type prevents browsers from sending a OPTIONS request first for
         * cross-site calls. Cannot prevent encoding, apparently UTF8.
         */
        _request.setRequestHeader("Content-type", "text/plain");
        _request.setOnReadyStateChange(this);

        _request.send(encode(data, length));
        _offset = 0;
    }

    String encode(byte[] data, int length) {
        String text = "" + (char) data[CometTransport.FIELD_TYPE];
        text += (char) CometTransport.ENCODING_0_127; // CometTransport.FIELD_REQUEST_ENCODING
        text += (char) CometTransport.ENCODING_PADDING; // CometTransport.FIELD_RESPONSE_ENCODING
        int encoderLZ = 0, encoderIndex = 0;

        for (int i = text.length(); i < length; i++) {
            if (encoderIndex-- == 0) {
                for (int t = 6; t >= 0; t--)
                    encoderLZ = Bits.set(encoderLZ, 6 - t, i + t < data.length && data[i + t] < 0);

                text += (char) encoderLZ;
                encoderIndex = 6;
            }

            byte b = data[i];
            text += (char) (b >= 0 ? b : -b - 1);
        }

        return text;
    }

    public final void onReadyStateChange(XMLHttpRequest request) {
        if (request == _request) {
            switch (request.getReadyState()) {
                case XMLHttpRequest.LOADING: {
                    if (request.getStatus() == Response.SC_OK) {
                        String data = request.getResponseText();
                        byte[] buffer = getBuffer();
                        int position = Reader.LARGEST_UNSPLITABLE;

                        for (; _offset < data.length(); _offset++)
                            buffer[position++] = (byte) data.charAt(_offset);

                        unpad(buffer, Reader.LARGEST_UNSPLITABLE, position);
                    } else {
                        String error = request.getStatusText();
                        getCallback().onError(new RuntimeIOException(error));
                    }

                    break;
                }
                case XMLHttpRequest.DONE: {
                    int status = request.getStatus();

                    if (status == Response.SC_OK) {
                        if (getServerToClient())
                            connect();
                        else
                            getCallback().onDone();
                    } else if (status == 0) {
                        String s = "Status 0. Might be cross domain issue.";
                        getCallback().onError(new RuntimeIOException(s));
                    } else {
                        String error = request.getStatusText();
                        getCallback().onError(new RuntimeIOException("" + status + " " + error));
                    }

                    break;
                }
            }
        }
    }

    private final void unpad(byte[] buffer, int offset, int limit) {
        for (;;) {
            if (_padOffset == Math.max(_padLength, CometTransport.MIN_CHUNK_SIZE)) {
                _padOffset = 0;
                _padLength = 0;
            }

            if (offset == limit)
                break;

            if (_padOffset == 0) {
                _padLength |= (buffer[offset++] & 0xff) << 8;
                _padOffset++;
            }

            if (offset == limit)
                break;

            if (_padOffset == 1) {
                _padLength |= (buffer[offset++] & 0xff);
                _padOffset++;
            }

            if (_padOffset < _padLength) {
                int needed = _padLength - _padOffset;
                int available = limit - offset;

                if (needed < available) {
                    getCallback().onRead(buffer, offset, offset + needed);
                    offset += needed;
                    _padOffset += needed;
                } else {
                    getCallback().onRead(buffer, offset, limit);
                    _padOffset += available;
                    break;
                }
            }

            if (_padOffset < CometTransport.MIN_CHUNK_SIZE) {
                if (Debug.ENABLED)
                    Debug.assertion(_padLength < CometTransport.MIN_CHUNK_SIZE);

                int needed = CometTransport.MIN_CHUNK_SIZE - _padOffset;
                int available = limit - offset;

                if (needed < available) {
                    offset += needed;
                    _padOffset += needed;
                } else {
                    _padOffset += available;
                    break;
                }
            }
        }
    }
}
