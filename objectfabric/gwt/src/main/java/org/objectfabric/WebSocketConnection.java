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
import com.google.gwt.user.client.Window.Navigator;

class WebSocketConnection extends Connection {

    private JavaScriptObject _webSocket;

    private final boolean _firefox = firefox();

    WebSocketConnection(Remote remote) {
        this(remote, null);
    }

    WebSocketConnection(Location location, JavaScriptObject webSocket) {
        super(location, null);

        _webSocket = webSocket;
    }

    final void connect() {
        Address address = ((Remote) location()).address();
        _webSocket = createWebSocket(address.toString() + Remote.WS_PATH, this);
    }

    private native JavaScriptObject createWebSocket(String url, WebSocketConnection callback) /*-{
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
      if (event.data instanceof ArrayBuffer || event.data instanceof Buffer) {
        var array = new Uint8Array(event.data);
        callback.@org.objectfabric.WebSocketConnection::onMessage(Lorg/objectfabric/Uint8Array;)(array);
      }
    }

    return socket;
    }-*/;

    final void onOpen() {
        if (location() instanceof Remote)
            ((Remote) location()).onConnection(this);

        onStarted();
    }

    private void onClose() {
        ((Remote) location()).onError(this, Strings.DISCONNECTED, true);
    }

    private void onError() {
        ((Remote) location()).onError(this, Strings.DISCONNECTED, true);
    }

    final void onMessage(Uint8Array buffer) {
        if (resumeRead()) {
            int offset = 0;
            int length = buffer.length();

            while (offset < length) {
                // Copy for getLargestUnsplitable() offset
                GWTBuff buff = (GWTBuff) Buff.getOrCreate();
                buff.position(Buff.getLargestUnsplitable());
                int copy = Math.min(length - offset, buff.remaining());
                Uint8Array sub = buffer.subarray(offset, offset + copy);
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
                    send(_webSocket, buff.subarray(), _firefox);
                }
            } catch (Exception e) {
                ex = e;
            }

            while (buffs.size() > 0)
                buffs.poll().recycle();

            writeComplete();

            if (ex != null && location() instanceof Remote)
                ((Remote) location()).onError(this, ex.toString(), true);
        }
    }

    boolean firefox() {
        return Navigator.getUserAgent().toLowerCase().indexOf("firefox") >= 0;
    }

    native void send(JavaScriptObject webSocket, Uint8Array array, boolean firefox) /*-{
    if (firefox) {
      // Firefox doesn't like sending Uint8Array directly, or the slice method
      if (!ArrayBuffer.prototype.slice) {
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

      array = array.buffer.slice(buffer.byteOffset, buffer.length);
    }

    webSocket.send(array);
    }-*/;
}
