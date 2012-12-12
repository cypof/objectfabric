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

import org.objectfabric.JS.External;
import org.objectfabric.JS.Internal;
import org.timepedia.exporter.client.Export;

import com.google.gwt.core.client.JavaScriptObject;

@Export("connection")
public class JSConnection implements External {

    static final class ConnectionInternal extends WebSocketConnection implements Internal {

        JSConnection _js;

        ConnectionInternal(Server server, JavaScriptObject ws) {
            super(server, ws);

            init(ws);
            onStarted();
        }

        private native void init(Object ws) /*-{
      var this_ = this;
      ws.on('message', function(data, flags) {
        if (data instanceof Buffer) {
          var typed = new Uint8Array(data);
          this_.@org.objectfabric.WebSocketConnection::onMessage(Lorg/objectfabric/Uint8Array;)(typed);
        }
      });
        }-*/;

        @Override
        boolean firefox() {
            return false;
        }

        @Override
        native void send(JavaScriptObject webSocket, Uint8Array buffer, boolean firefox) /*-{
      webSocket.send(buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.length), {
        binary : true
      });
        }-*/;

        @Override
        public External external() {
            if (_js == null) {
                _js = new JSConnection();
                _js._internal = this;
            }

            return _js;
        }
    }

    private ConnectionInternal _internal;

    public JSConnection(JSServer server, JavaScriptObject ws) {
        _internal = new ConnectionInternal(server._internal, ws);
    }

    @Override
    public Internal internal() {
        return _internal;
    }

    private JSConnection() {
    }
}
