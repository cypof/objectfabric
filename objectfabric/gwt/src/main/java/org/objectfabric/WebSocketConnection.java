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
        callback.@org.objectfabric.WebSocketConnection::onMessage(Lcom/google/gwt/core/client/JavaScriptObject;)(typed);
      }
    }

    return socket;
    }-*/;

    private void onOpen() {
        ((Remote) location()).onConnection(this);
        onStarted();
    }

    private native void send(JavaScriptObject webSocket, JavaScriptObject array, int offset, int length) /*-{
    if (!ArrayBuffer.prototype.slice) { // Apparently no slice on IE10 & Firefox?
      ArrayBuffer.prototype.slice = function(start, end) {
        var that = new Uint8Array(this);
        if (end == undefined)
          end = that.length;
        var result = new ArrayBuffer(end - start);
        var resultArray = new Uint8Array(result);
        for ( var i = 0; i < resultArray.length; i++)
          resultArray[i] = that[i + start];
        return result;
      }
    }

    var slice = array.buffer.slice(offset, offset + length);
    webSocket.send(slice);
    }-*/;

    private void onClose() {
        ((Remote) location()).onError(this, Strings.DISCONNECTED, true);
    }

    private void onError() {
        ((Remote) location()).onError(this, Strings.DISCONNECTED, true);
    }

    private void onMessage(JavaScriptObject typed) {
        if (resumeRead()) {
            int offset = 0;
            int length = length(typed);

            while (offset < length) {
                // TODO wrap instead of copy
                GWTBuff buff = (GWTBuff) Buff.getOrCreate();
                buff.position(Buff.getLargestUnsplitable());

                int copy = Math.min(length - offset, buff.remaining());
                copy(typed, offset, buff.getTyped(), buff.position(), copy);
                buff.limit(buff.position() + copy);
                offset += copy;

                read(buff);

                buff.recycle();
            }

            suspendRead();
        }
    }

    private native int length(JavaScriptObject array) /*-{
    return array.length;
    }-*/;

    private static native void copy(JavaScriptObject source, int sourceOffset, JavaScriptObject target, int targetOffset, int length) /*-{
    var sub = source.subarray(sourceOffset, sourceOffset + length);
    target.set(sub, targetOffset);
    }-*/;

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
                    send(_webSocket, buff.getTyped(), buff.position(), buff.remaining());
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
}
