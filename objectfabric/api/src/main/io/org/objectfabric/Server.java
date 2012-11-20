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

public abstract class Server extends Location implements URIHandlersSet {

    private final URIResolver _resolver;

    protected Server() {
        _resolver = new URIResolver();
    }

    /**
     * Can be overriden to create custom sessions.
     * 
     * @param source
     *            Transport source address, e.g. a InetSocketAddress.
     * @param channel
     *            Channel, e.g. a org.jboss.netty.channel.Channel for Netty.
     */
    protected Session onConnection(Object source, Address target, Headers headers, Object channel) {
        return null;
    }

    final URIResolver resolver() {
        return _resolver;
    }

    @Override
    View newView(URI uri) {
        return new ServerView(this);
    }

    //

    @Override
    public URIHandler[] uriHandlers() {
        return _resolver.uriHandlers();
    }

    @Override
    public void addURIHandler(URIHandler handler) {
        _resolver.addURIHandler(handler);
    }

    @Override
    public void addURIHandler(int index, URIHandler handler) {
        _resolver.addURIHandler(index, handler);
    }

    @Override
    public Location[] caches() {
        return _resolver.caches();
    }

    @Override
    public void addCache(Location location) {
        _resolver.addCache(location);
    }
}
