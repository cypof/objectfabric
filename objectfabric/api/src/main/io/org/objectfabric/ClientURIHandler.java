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

import java.lang.ref.WeakReference;

@SuppressWarnings("rawtypes")
public abstract class ClientURIHandler implements URIHandler {

    private static final PlatformConcurrentMap<String, WeakReference<Remote>> _remotes;

    static {
        _remotes = new PlatformConcurrentMap<String, WeakReference<Remote>>();
    }

    private static volatile boolean _enabled = true;

    protected ClientURIHandler() {
    }

    /**
     * Called on every connection and automatic reconnection to pass optional headers.
     */
    protected Headers getHeaders(Address address) {
        return null;
    }

    /**
     * Called when received response headers from server.
     */
    protected void onResponseHeaders(Headers headers) {
    }

    static PlatformConcurrentMap<String, WeakReference<Remote>> remotes() {
        return _remotes;
    }

    abstract Remote createRemote(Address address);

    protected final Remote get(Address address) {
        String key = address.toString();
        WeakReference<Remote> expect = _remotes.get(key);

        if (expect != null) {
            Remote remote = expect.get();

            if (remote != null)
                return remote;
        }

        Remote remote = createRemote(address);
        WeakReference<Remote> update = new WeakReference<Remote>(remote);

        for (;;) {
            if (expect != null) {
                if (_remotes.replace(key, expect, update))
                    return remote;
            } else {
                expect = _remotes.putIfAbsent(key, update);

                if (expect == null)
                    return remote;

                Remote previous = expect.get();

                if (previous != null)
                    return previous;
            }
        }
    }

    //

    static boolean isEnabled() {
        return _enabled;
    }

    /**
     * Unsupported, testing purposes only.
     */
    public static void enableNetwork() {
        _enabled = true;

        for (WeakReference<Remote> ref : _remotes.values()) {
            Remote remote = ref.get();

            if (remote != null)
                remote.retry();
        }
    }

    /**
     * Unsupported, testing purposes only.
     */
    public static void disableNetwork() {
        _enabled = false;

        for (WeakReference<Remote> ref : _remotes.values()) {
            Remote remote = ref.get();

            if (remote != null) {
                Connection connection = remote.connection();

                if (connection != null) {
                    remote.onError(connection, "Forced disconnection", true);

                    while (!connection.isClosed())
                        Platform.get().sleep(1);
                }
            }
        }
    }
}
