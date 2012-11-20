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

/**
 * Stores resources in memory. Data is stored in part as direct ByteBuffers. If
 * application exhibits unexplained GCs it might be running out of direct memory. This can
 * be increased using the JVM switch, e.g.
 * <nl>
 * -XX:MaxDirectMemorySize=4G
 */
public class Memory extends Origin implements URIHandler {

    protected interface Backend {

        Object get(String key);

        Object putIfAbsent(String key, Object value);
    }

    private final Backend _backend;

    /**
     * @param cache
     *            Must be true if this location is to be used as a cache (
     *            {@link URIResolver#addCache(Location)}) or as a resource origin (
     *            {@link URIResolver#addURIHandler(URIHandler)}).
     */
    public Memory(boolean cache) {
        this(cache, new DefaultBackend());
    }

    protected Memory(boolean cache, Backend backend) {
        super(cache);

        _backend = backend;

        if (Debug.ENABLED)
            if (backend instanceof DefaultBackend)
                Helper.instance().Memories.put(this, (DefaultBackend) backend);
    }

    final Backend backend() {
        return _backend;
    }

    final void onEviction(Object value) {
        MemoryView view = (MemoryView) value;
        view.dispose();
    }

    @Override
    public URI handle(Address address, String path) {
        return getURI(path);
    }

    @Override
    View newView(URI uri) {
        MemoryView view = (MemoryView) _backend.get(uri.path());

        if (view == null) {
            view = new MemoryView(this);
            MemoryView previous = (MemoryView) _backend.putIfAbsent(uri.path(), view);

            if (previous != null)
                view = previous;
        }

        return view;
    }

    @Override
    public String toString() {
        return "memory://";
    }

    static final class DefaultBackend implements Backend {

        final PlatformConcurrentMap<String, Object> Map = new PlatformConcurrentMap<String, Object>();

        @Override
        public Object get(String key) {
            return Map.get(key);
        }

        @Override
        public Object putIfAbsent(String key, Object value) {
            return Map.putIfAbsent(key, value);
        }
    }
}