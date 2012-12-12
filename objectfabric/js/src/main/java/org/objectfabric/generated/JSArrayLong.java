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

package org.objectfabric.generated;

import org.objectfabric.IndexListener;
import org.objectfabric.JS.Closure;
import org.objectfabric.JS.External;
import org.objectfabric.JS.Internal;
import org.objectfabric.JSResource;
import org.objectfabric.Resource;
import org.objectfabric.TArrayLong;
import org.timepedia.exporter.client.Export;
import org.timepedia.exporter.client.ExportPackage;

//==============================================================================
//
//THIS FILE HAS BEEN GENERATED BY OBJECTFABRIC                                        
//
//==============================================================================

@Export("arrayLong")
@ExportPackage("org.objectfabric")
public class JSArrayLong implements External {

    // TODO back with JS typed arrays?
    public static final class ArrayInternal extends TArrayLong implements Internal {

        JSArrayLong _js;

        public ArrayInternal(Resource resource, int length) {
            super(resource, length);
        }

        @Override
        public External external() {
            if (_js == null) {
                _js = new JSArrayLong();
                _js._internal = this;
            }

            return _js;
        }
    }

    private ArrayInternal _internal;

    public JSArrayLong(JSResource resource, int length) {
        _internal = new ArrayInternal((Resource) resource.internal(), length);
    }

    private JSArrayLong() {
    }

    @Override
    public Internal internal() {
        return _internal;
    }

    public long get(int index) {
        return _internal.get(index);
    }

    public int length() {
        return _internal.length();
    }

    public void set(int index, long value) {
        _internal.set(index, value);
    }

    public void onset(final Closure closure) {
        _internal.addListener(new IndexListener() {

            @Override
            public void onSet(int index) {
                closure.runImmutable(index);
            }
        });
    }
}
