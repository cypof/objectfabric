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

package examples.client;

import java.util.ArrayList;

import org.objectfabric.Log;

public final class Terminal extends Log {

    public interface Listener {

        void onLine(String line);
    }

    private static final ArrayList<Listener> _listeners = new ArrayList<Listener>();

    private static final Terminal _instance = new Terminal();

    static {
        init();
    }

    private Terminal() {
    }

    public static Terminal getInstance() {
        return _instance;
    }

    //

    public static void add(Listener listener) {
        _listeners.add(listener);
    }

    private static native void init() /*-{
        $wnd.gwtTerminal = $entry(@examples.client.Terminal::onLine(Ljava/lang/String;));
    }-*/;

    private static void onLine(String line) {
        for (Listener listener : _listeners)
            listener.onLine(line);
    }

    //

    public static native void write(String line) /*-{
        $wnd.$('body').terminal().echo(line);
    }-*/;

    public static void write(Object object) {
        write(String.valueOf(object));
    }

    @Override
    protected void log(String message) {
        write(message);
    }
}
