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
@Export("server")
public class JSServer implements External {

    static final class ServerInternal extends Server implements Internal {

        JSServer _js;

        @Override
        public External external() {
            if (_js == null) {
                _js = new JSServer();
                _js._internal = this;
            }

            return _js;
        }
    }

    ServerInternal _internal;

    public JSServer() {
        _internal = new ServerInternal();
    }

    @Override
    public Internal internal() {
        return _internal;
    }

    public void addURIHandler(JSMemory value) {
        _internal.addURIHandler(value.internal());
    }

    public void addURIHandler(JSFileSystem value) {
        _internal.addURIHandler(value.internal());
    }
}
