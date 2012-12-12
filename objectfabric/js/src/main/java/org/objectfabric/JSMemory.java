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

@Export("memory")
public class JSMemory implements External {

    static final class MemoryInternal extends Memory implements Internal {

        JSMemory _js;

        MemoryInternal(boolean cache) {
            super(cache);
        }

        @Override
        public External external() {
            if (_js == null) {
                _js = new JSMemory();
                _js._internal = this;
            }

            return _js;
        }
    }

    private MemoryInternal _internal;

    public JSMemory() {
        this(false);
    }

    public JSMemory(boolean cache) {
        _internal = new MemoryInternal(cache);
    }

    @Override
    public MemoryInternal internal() {
        return _internal;
    }
}
