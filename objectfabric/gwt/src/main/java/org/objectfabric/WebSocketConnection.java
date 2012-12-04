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

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.client.Uint8ArrayNative;
import com.google.gwt.typedarrays.shared.ArrayBuffer;

class WebSocketConnection extends Connection {

    private JavaScriptObject _webSocket;

    WebSocketConnection(Remote client) {
        super(client, null);
    }

    final void start() {
        Address address = ((Remote) location()).address();
        _webSocket = createWebSocket(address.toString() + Remote.WS_PATH, this);
    }

    private native JavaScriptObject createWebSocket(final String url, final WebSocketConnection callback) /*-{
    var socket = new WebSocket(url);
    socket.binaryType = "arraybuffer";

    socket.onopen = function() {
      callback.@org.objectfabric.WebSocketConnection::onOpen()();
    }

    socket.onclose = function() {
      callback.@org.objectfabric.WebSocketConnection::onClose()();
    }

    socket.onerror = function() {
      callback.@org.objectfabric.WebSocketConnection::onError()();
    }

    socket.onmessage = function(event) {
      if (event.data instanceof ArrayBuffer) {
        var typed = new Uint8Array(event.data);
        callback.@org.objectfabric.WebSocketConnection::onMessage(Lcom/google/gwt/typedarrays/client/Uint8ArrayNative;)(typed);
      }
    }

    return socket;
    }-*/;

    private void onOpen() {
        ((Remote) location()).onConnection(this);
        onStarted();
    }

    private void onClose() {
        ((Remote) location()).onError(this, Strings.DISCONNECTED, true);
    }

    private void onError() {
        ((Remote) location()).onError(this, Strings.DISCONNECTED, true);
    }

    private void onMessage(Uint8ArrayNative typed) {
        if (resumeRead()) {
            int offset = 0;
            int length = typed.length();

            while (offset < length) {
                // TODO wrap instead of copy
                GWTBuff buff = (GWTBuff) Buff.getOrCreate();
                buff.position(Buff.getLargestUnsplitable());

                int copy = Math.min(length - offset, buff.remaining());
                Uint8ArrayNative sub = typed.subarray(offset, offset + copy);
                buff.typed().set(sub, buff.position());
                buff.limit(buff.position() + copy);
                offset += copy;

                read(buff);

                buff.recycle();
            }

            suspendRead();
        }
    }

    @Override
    void onClose(Callback callback) {
        super.onClose(callback);

        try {
            close(_webSocket);
        } catch (Exception _) {
            // Ignore
        }
    }

    private native void close(JavaScriptObject webSocket) /*-{
    webSocket.close();
    }-*/;

    @Override
    protected void write() {
        Queue<Buff> buffs = fill(0xffff);

        if (buffs != null) {
            Exception ex = null;

            try {
                for (int i = 0; i < buffs.size(); i++) {
                    GWTBuff buff = (GWTBuff) buffs.get(i);
                    send(_webSocket, buff.slice());
                }
            } catch (Exception e) {
                ex = e;
            }

            while (buffs.size() > 0)
                buffs.poll().recycle();

            writeComplete();

            if (ex != null)
                ((Remote) location()).onError(this, ex.toString(), true);
        }
    }

    private native void send(JavaScriptObject webSocket, ArrayBuffer buffer) /*-{
    webSocket.send(buffer);
    }-*/;
}
