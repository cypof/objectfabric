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

import org.objectfabric.JS.Closure;
import org.objectfabric.JS.External;
import org.objectfabric.JS.Internal;
import org.timepedia.exporter.client.Export;
import org.timepedia.exporter.client.ExportPackage;

@SuppressWarnings("unchecked")
@Export("set")
@ExportPackage("of")
public class JSSet implements External {

    static final class SetInternal extends TSet implements Internal {

        JSSet _js;

        SetInternal(Resource resource, TType genericParam) {
            super(resource, genericParam);
        }

        @Override
        public External external() {
            if (_js == null) {
                _js = new JSSet();
                _js._internal = this;
            }

            return _js;
        }
    }

    private SetInternal _internal;

    public JSSet(JSResource resource) {
        _internal = new SetInternal(resource._internal, null);
    }

    private JSSet() {
    }

    @Override
    public Internal internal() {
        return _internal;
    }

    public void add(Object item) {
        _internal.add(JS.in(item));
    }

    public void clear() {
        _internal.clear();
    }

    public boolean contains(Object item) {
        return _internal.contains(JS.in(item));
    }

    public void each(Closure closure) {
        for (Object item : _internal) {
            if (item instanceof Internal)
                closure.runExportable(((Internal) item).external());
            else
                closure.runImmutable(item);
        }
    }

    public void remove(Object item) {
        _internal.remove(JS.in(item));
    }

    public int size() {
        return _internal.size();
    }

    //

    public void onadd(final Closure closure) {
        _internal.addListener(new AbstractKeyListener() {

            @Override
            public void onPut(Object key) {
                if (key instanceof Internal)
                    closure.runExportable(((Internal) key).external());
                else
                    closure.runImmutable(key);
            }
        });
    }

    public void onremove(final Closure closure) {
        _internal.addListener(new AbstractKeyListener() {

            @Override
            public void onRemove(Object key) {
                if (key instanceof Internal)
                    closure.runExportable(((Internal) key).external());
                else
                    closure.runImmutable(key);
            }
        });
    }
}
