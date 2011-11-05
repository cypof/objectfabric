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

import com.google.gwt.core.client.JavaScriptObject;

/**
 * XDomainRequest is IE equivalent of XMLHttpRequest for cross domain requests.
 */
final class XDomainRequestImpl extends HTTPRequest {

    private XDomainRequest _request;

    private int _offset, _decoderIndex, _decoderLZ, _decoderGZ;

    @Override
    public void close() {
        if (_request != null) {
            _request.abort();
            _request = null;
        }
    }

    @Override
    public void send(byte[] data, int length) {
        close();

        _request = XDomainRequest.create();
        _request.open(getURL());
        _request.setHandler(this);
        _request.send(encode(data, length, CometTransport.ENCODING_1_127));
        _offset = _decoderIndex = 0;
    }

    /**
     * Same encoding as XMLHttpRequest, but also removes zeros. @see
     * of4gwt.transports.http.CometTransport.ENCODING_IE.
     */
    public static String encode(byte[] data, int length, byte responseEncoding) {
        String text = "" + (char) data[CometTransport.FIELD_TYPE];
        text += (char) CometTransport.ENCODING_1_127; // CometTransport.FIELD_REQUEST_ENCODING
        text += (char) responseEncoding; // CometTransport.FIELD_RESPONSE_ENCODING
        int encoderIndex = 0;

        for (int i = text.length(); i < length; i++) {
            if (encoderIndex-- == 0) {
                int lz = Bits.set(0, 6), gz = Bits.set(0, 6);

                for (int t = 5; t >= 0; t--) {
                    if (i + t < data.length) {
                        if (data[i + t] < 0) {
                            lz = Bits.set(lz, 5 - t);
                            data[i + t] = (byte) (-data[i + t] - 1);
                        }

                        if (data[i + t] > 0)
                            gz = Bits.set(gz, 5 - t);
                        else
                            data[i + t] = CometTransport.ENCODING_BYTE_ZERO;
                    }
                }

                text += (char) lz;
                text += (char) gz;
                encoderIndex = 5;
            }

            text += (char) data[i];
        }

        if (Debug.ENABLED)
            for (int i = 0; i < text.length(); i++)
                Debug.assertion(text.charAt(i) > 0 && text.charAt(i) <= 127);

        return text;
    }

    int _counter;

    public void onLoading() {
        if (getServerToClient())
            read();
    }

    public void onDone() {
        if (getServerToClient()) {
            read();
            connect();
        } else
            getCallback().onDone();
    }

    private void read() {
        String data = _request.getResponseText();
        byte[] buffer = getBuffer();
        int position = Reader.LARGEST_UNSPLITABLE;

        for (; _offset < data.length(); _offset++) {
            if (_decoderIndex == 0) {
                _decoderLZ = data.charAt(_offset);
                _decoderIndex = -1;
            } else if (_decoderIndex < 0) {
                _decoderGZ = data.charAt(_offset);
                _decoderIndex = 6;
            } else {
                byte b = (byte) data.charAt(_offset);
                boolean pad = false;

                if (!Bits.get(_decoderGZ, --_decoderIndex)) {
                    pad = b == CometTransport.ENCODING_BYTE_PADDING;
                    b = 0;
                }

                if (Bits.get(_decoderLZ, _decoderIndex))
                    b = (byte) (-b - 1);

                if (!pad)
                    buffer[position++] = b;
            }
        }

        getCallback().onRead(buffer, Reader.LARGEST_UNSPLITABLE, position);
    }

    public void onError() {
        String error = _request.getStatus();
        getCallback().onError(new RuntimeIOException(error));
    }

    private static final class XDomainRequest extends JavaScriptObject {

        public static native XDomainRequest create() /*-{
			var me = new Object();
			me.request = new XDomainRequest();
			return me;
        }-*/;

        protected XDomainRequest() {
        }

        public native void open(String url) /*-{
			this.request.open("POST", url);
        }-*/;

        public native void send(String requestData) /*-{
			this.request.send(requestData);
        }-*/;

        public native void abort() /*-{
			this.request.abort();
        }-*/;

        public native String getResponseText() /*-{
			return this.request.responseText;
        }-*/;

        public native String getStatus() /*-{
			return this.request.contentType;
        }-*/;

        public static void onLoading(XDomainRequestImpl handler) {
            handler.onLoading();
        }

        public static void onDone(XDomainRequestImpl handler) {
            handler.onDone();
        }

        public static void onError(XDomainRequestImpl handler) {
            handler.onError();
        }

        public native void setHandler(XDomainRequestImpl handler) /*-{
			this.request.onprogress = function() {
				$entry(@of4gwt.transports.http.XDomainRequestImpl.XDomainRequest::onLoading(Lof4gwt/transports/http/XDomainRequestImpl;)(handler));
			};

			this.request.onload = function() {
				$entry(@of4gwt.transports.http.XDomainRequestImpl.XDomainRequest::onDone(Lof4gwt/transports/http/XDomainRequestImpl;)(handler));
			};

			this.request.onerror = function() {
				$entry(@of4gwt.transports.http.XDomainRequestImpl.XDomainRequest::onError(Lof4gwt/transports/http/XDomainRequestImpl;)(handler));
			};

			this.request.ontimeout = function() {
				$entry(@of4gwt.transports.http.XDomainRequestImpl.XDomainRequest::onError(Lof4gwt/transports/http/XDomainRequestImpl;)(handler));
			};
        }-*/;
    }
}
