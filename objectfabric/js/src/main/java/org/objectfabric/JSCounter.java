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
@Export
public class JSCounter implements External  {

    static final class CounterInternal extends Counter implements Internal {

        JSCounter _js;

        CounterInternal(Resource resource) {
            super(resource);
        }

        @Override
        public External external() {
            if (_js == null) {
                _js = new JSCounter();
                _js._internal = this;
            }

            return _js;
        }
    }

    private CounterInternal _internal;

    public JSCounter(JSResource resource) {
        _internal = new CounterInternal(resource._internal);
    }

    @Override
    public Internal internal() {
        return _internal;
    }

    private JSCounter() {
    }

    public void add(long delta) {
        _internal.add(delta);
    }

    public long get() {
        return _internal.get();
    }

    public void reset() {
        _internal.reset();
    }
}
