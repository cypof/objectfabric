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

import org.timepedia.exporter.client.Export;
import org.timepedia.exporter.client.ExportPackage;
import org.timepedia.exporter.client.Exportable;

@SuppressWarnings("unchecked")
@Export("set")
@ExportPackage("of")
public class JSSet implements Exportable {

    static final class SetInternal extends TSet implements Internal {

        JSSet _js;

        SetInternal(Resource resource, TType genericParam) {
            super(resource, genericParam);
        }

        @Override
        public Exportable getOrCreateJS() {
            if (_js == null)
                _js = new JSSet(this);

            return _js;
        }
    }

    private final SetInternal _internal;

    public JSSet(JSResource resource) {
        this(new SetInternal(resource._internal, null));
    }

    JSSet(SetInternal internal) {
        _internal = internal;
    }

    public void add(Object item) {
        _internal.add(item);
    }

    public void clear() {
        _internal.clear();
    }

    public boolean contains(Object item) {
        return _internal.contains(item);
    }

    public void each(Closure closure) {
        for (Object item : _internal) {
            Object js = Main.map(item);

            if (js instanceof Exportable)
                closure.runExportable((Exportable) js);
            else
                closure.runPrimitive(js);
        }
    }

    public void remove(Object item) {
        _internal.remove(item);
    }

    public int size() {
        return _internal.size();
    }

    //

    public void onadd(final Closure closure) {
        _internal.addListener(new AbstractKeyListener() {

            @Override
            public void onPut(Object key) {
                Object js = Main.map(key);

                if (js instanceof Exportable)
                    closure.runExportable((Exportable) js);
                else
                    closure.runPrimitive(js);
            }
        });
    }

    public void onremove(final Closure closure) {
        _internal.addListener(new AbstractKeyListener() {

            @Override
            public void onRemove(Object key) {
                Object js = Main.map(key);

                if (js instanceof Exportable)
                    closure.runExportable((Exportable) js);
                else
                    closure.runPrimitive(js);
            }
        });
    }
}
