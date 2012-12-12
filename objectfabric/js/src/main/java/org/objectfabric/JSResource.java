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
import org.timepedia.exporter.client.NoExport;

@Export("resource")
public class JSResource implements External {

    static final class ResourceInternal extends Resource implements Internal {

        JSResource _js;

        ResourceInternal(Workspace workspace, URI uri) {
            super(workspace, uri);
        }

        @Override
        public JSResource external() {
            if (_js == null) {
                _js = new JSResource();
                _js._internal = this;
            }

            return _js;
        }
    }

    ResourceInternal _internal;

    @NoExport
    public JSResource() {
    }

    @Override
    public Internal internal() {
        return _internal;
    }

    public String permission() {
        switch (_internal.permission()) {
            case NONE:
                return "none";
            case READ:
                return "read";
            case WRITE:
                return "write";
            default:
                throw new IllegalStateException();
        }
    }

    public Object get() {
        return JS.out(_internal.get());
    }

    public void set(Object value) {
        _internal.set(JS.in(value));
    }
}
