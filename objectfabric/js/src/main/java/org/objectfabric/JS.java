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
import org.timepedia.exporter.client.ExportClosure;
import org.timepedia.exporter.client.Exportable;
import org.timepedia.exporter.client.NoExport;

@Export
public final class JS implements Exportable {

    @Export
    @ExportClosure
    public interface Closure extends Exportable {

        void runExportable(Exportable value);

        void runImmutable(Object value);
    }

    public interface External extends Exportable {

        Internal internal();
    }

    public interface Internal {

        External external();
    }

    @NoExport
    @SuppressWarnings("unchecked")
    public static <T> T out(T value) {
        return (T) wrap(value instanceof Internal, value);
    }

    static native Object wrap(boolean wrap, Object value) /*-{
    if (wrap)
      return $wnd.org.objectfabric.JS.wrap(value);

    return value;
    }-*/;

    public static Exportable wrap(Internal value) {
        return value.external();
    }

    @NoExport
    @SuppressWarnings("unchecked")
    public static <T> T in(T value) {
        if (value instanceof Exportable)
            return (T) ((Internal) value).external();

        return value;
    }
}
