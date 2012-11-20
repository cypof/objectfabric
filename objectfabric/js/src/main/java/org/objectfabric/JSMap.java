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
@Export("map")
@ExportPackage("of")
public class JSMap implements Exportable {

    static final class MapInternal extends TMap implements Internal {

        JSMap _js;

        MapInternal(Resource resource, TType genericParamKey, TType genericParamValue) {
            super(resource, genericParamKey, genericParamValue);
        }

        @Override
        public Exportable getOrCreateJS() {
            if (_js == null)
                _js = new JSMap(this);

            return _js;
        }
    }

    private final MapInternal _internal;

    public JSMap(JSResource resource) {
        this(new MapInternal(resource._internal, null, null));
    }

    JSMap(MapInternal internal) {
        _internal = internal;
    }

    public Object get(Object key) {
        return _internal.get(key);
    }

    public void clear() {
        _internal.clear();
    }

    public void put(Object key, Object value) {
        _internal.put(key, value);
    }

    public void each(Closure closure) {
        for (Object key : _internal.keySet()) {
            Object js = Main.map(key);

            if (js instanceof Exportable)
                closure.runExportable((Exportable) js);
            else
                closure.runPrimitive(js);
        }
    }

    public void remove(Object key) {
        _internal.remove(key);
    }

    public int size() {
        return _internal.size();
    }

    //

    public void onput(final Closure closure) {
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
