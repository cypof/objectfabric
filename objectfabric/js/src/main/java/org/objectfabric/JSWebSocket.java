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

@SuppressWarnings("unchecked")
@Export("WebSocket")
public class JSWebSocket implements External {

    static final class WebSocketInternal extends WebSocket implements Internal {

        JSWebSocket _js;

        @Override
        public External external() {
            if (_js == null) {
                _js = new JSWebSocket();
                _js._internal = this;
            }

            return _js;
        }
    }

    private WebSocketInternal _internal;

    public JSWebSocket() {
        _internal = new WebSocketInternal();
    }

    @Override
    public WebSocketInternal internal() {
        return _internal;
    }
}
