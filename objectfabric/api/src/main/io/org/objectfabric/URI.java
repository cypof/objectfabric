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

import org.objectfabric.InFlight.Get;

/**
 * Internal. Don't take locks on instances of this class.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class URI { // TODO pool?

    private final Origin _origin;

    private final String _path;

    private volatile ResourceRef[] _resources;

    private volatile View[] _views;

    URI(Origin origin, String path) {
        if (origin == null || path == null)
            throw new IllegalArgumentException();

        if (Debug.ENABLED)
            Debug.assertion(!origin.isCache());

        _origin = origin;
        _path = path;
    }

    URI() {
        // Testing
        _origin = null;
        _path = null;
    }

    // For .NET
    void onReferenced(PlatformRef<URI> ref) {
    }

    public final Origin origin() {
        return _origin;
    }

    public final String path() {
        return _path;
    }

    final PlatformRef<Resource>[] resources() {
        return _resources;
    }

    /*
     * Resources.
     */

    final boolean contains(Workspace workspace) {
        ResourceRef[] refs = _resources;
        return index(refs, workspace) >= 0;
    }

    final Resource getOrCreate(Workspace workspace) {
        ResourceRef[] refs = _resources;
        int i = index(refs, workspace);
        Resource resource = i >= 0 ? refs[i].get() : null;

        if (resource == null) {
            Resource created = workspace.newResource(this);

            synchronized (this) {
                refs = _resources;
                i = index(refs, workspace);
                resource = i >= 0 ? refs[i].get() : null;

                if (resource == null) {
                    if (Debug.ENABLED)
                        Helper.instance().Resources.put(created, created);

                    resource = created;
                    ResourceRef ref = new ClosedRef(resource);

                    if (i >= 0)
                        replace(refs, i, ref, workspace);
                    else {
                        if (refs != null) {
                            ResourceRef[] temp = new ResourceRef[refs.length + 1];
                            System.arraycopy(refs, 0, temp, 0, refs.length);
                            temp[temp.length - 1] = ref;
                            refs = temp;
                        } else
                            refs = new ResourceRef[] { ref };

                        _resources = refs;
                    }
                }
            }
        }

        return resource;
    }

    private static int index(ResourceRef[] refs, Workspace workspace) {
        for (int i = 0; refs != null && i < refs.length; i++)
            if (refs[i].Workspace == workspace)
                return i;

        return -1;
    }

    final FutureWithCallbacks<Object> open(final Resource resource) {
        FutureWithCallbacks<Object> future = null;
        boolean opened = false;

        synchronized (this) {
            ResourceRef[] refs = _resources;
            int i = index(refs, resource.workspaceImpl());

            if (refs[i] instanceof LoadedRef) {
                if (Debug.ENABLED)
                    Debug.assertion(resource.getFromMemory() != null);
            } else {
                if (refs[i] instanceof LoadingRef)
                    future = ((LoadingRef) refs[i]).Future;
                else {
                    LoadingRef update = new LoadingRef(resource, //
                            new FutureWithCallbacks<Object>(FutureWithCallback.NOP_CALLBACK, null) {

                                @Override
                                public boolean cancel(boolean mayInterruptIfRunning) {
                                    URI.this.cancel(resource.workspaceImpl());
                                    return super.cancel(mayInterruptIfRunning);
                                }
                            });

                    replace(refs, i, update, resource.workspaceImpl());
                    future = update.Future;
                    opened = true;
                }
            }
        }

        if (opened) {
            Object key;

            if (Debug.THREADS)
                ThreadAssert.suspend(key = new Object());

            getKnown(resource);

            if (Debug.THREADS)
                ThreadAssert.resume(key);
        }

        return future;
    }

    final void onCancel(Resource resource, Exception exception) {
        FutureWithCallbacks<Object> future = cancel(resource.workspaceImpl());

        if (future != null)
            future.setException(exception);
    }

    private final FutureWithCallbacks<Object> cancel(Workspace workspace) {
        synchronized (this) {
            ResourceRef[] refs = _resources;
            int i = index(refs, workspace);

            if (i >= 0 && refs[i] instanceof LoadingRef) {
                LoadingRef ref = (LoadingRef) refs[i];
                replace(refs, i, new ClosedRef(ref.get()), workspace);
                return ref.Future;
            }

            return null;
        }
    }

    final FutureWithCallbacks<Object> markLoaded(Resource resource) {
        synchronized (this) {
            ResourceRef[] refs = _resources;
            int i = index(refs, resource.workspaceImpl());

            if (refs[i] instanceof LoadingRef) {
                LoadingRef ref = (LoadingRef) refs[i];
                replace(refs, i, new LoadedRef(resource), resource.workspaceImpl());
                return ref.Future;
            }

            if (refs[i] instanceof ClosedRef) {
                replace(refs, i, new LoadedRef(resource), resource.workspaceImpl());
                return new FutureWithCallbacks<Object>(null, null);
            }

            return null;
        }
    }

    final void onClose(Workspace workspace) {
        ResourceRef[] refs = _resources;

        if (index(refs, workspace) >= 0) {
            synchronized (this) {
                refs = _resources;

                for (int i = 0; refs != null && i < refs.length; i++) {
                    if (refs[i].Workspace == workspace) {
                        remove(refs, i);
                        break;
                    }
                }
            }
        }
    }

    private final void onGCed(ResourceRef ref) {
        synchronized (this) {
            ResourceRef[] refs = _resources;

            for (int i = 0; refs != null && i < refs.length; i++) {
                if (refs[i] == ref) {
                    remove(refs, i);
                    break;
                }
            }
        }
    }

    //

    private final void replace(ResourceRef[] refs, int i, ResourceRef update, Workspace workspace) {
        boolean a = refs[i] instanceof ClosedRef;
        boolean b = update instanceof ClosedRef;

        refs[i].clear();
        refs[i] = update;

        if (a && !b)
            open(refs, i, workspace);

        if (!a && b)
            close(refs);
    }

    private final void remove(ResourceRef[] refs, int i) {
        ResourceRef[] update = new ResourceRef[refs.length - 1];
        int n = 0;

        for (int t = 0; t < refs.length; t++)
            if (t != i)
                update[n++] = refs[t];

        boolean closing = !(refs[i] instanceof ClosedRef);
        refs[i].clear();
        refs = update.length != 0 ? update : null;
        _resources = refs;

        if (closing)
            close(refs);
    }

    private final void open(ResourceRef[] refs, int i, Workspace workspace) {
        boolean alreadyOpen = false;

        for (int t = 0; t < refs.length; t++) {
            if (t != i && !(refs[t] instanceof ClosedRef)) {
                alreadyOpen = true;
                break;
            }
        }

        if (!alreadyOpen) {
            getOrCreate(_origin).open(this);

            Location[] caches = workspace.caches();

            if (caches != null)
                for (int t = 0; t < caches.length; t++)
                    if (caches[t].caches(this))
                        getOrCreate(caches[t]).open(this);
        }
    }

    private final void close(ResourceRef[] refs) {
        for (int t = 0; refs != null && t < refs.length; t++)
            if (!(refs[t] instanceof ClosedRef))
                return;

        getOrCreate(_origin).close(this);
    }

    final void runIf(Runnable runnable, boolean open) {
        synchronized (this) {
            ResourceRef[] refs = _resources;
            boolean current = false;

            for (int t = 0; refs != null && t < refs.length; t++)
                if (!(refs[t] instanceof ClosedRef))
                    current = true;

            if (current == open)
                runnable.run();
        }
    }

    /*
     * Views.
     */

    final boolean contains(Location location) {
        View[] views = _views;
        return index(views, location) >= 0;
    }

    final View getOrCreate(Location location) {
        View[] views = _views;
        int i = index(views, location);
        View view = i >= 0 ? views[i] : null;

        if (view == null) {
            View update = location.newView(this);

            synchronized (this) {
                views = _views;
                i = index(views, location);
                view = i >= 0 ? views[i] : null;

                if (view == null) {
                    view = update;
                    location.onView(this, view);

                    if (views != null) {
                        View[] temp = new View[views.length + 1];
                        System.arraycopy(views, 0, temp, 0, views.length);
                        temp[temp.length - 1] = view;
                        views = temp;
                    } else
                        views = new View[] { view };

                    _views = views;
                }
            }
        }

        return view;
    }

    private static int index(View[] views, Location location) {
        for (int i = 0; views != null && i < views.length; i++)
            if (views[i].location() == location)
                return i;

        return -1;
    }

    /*
     * IO.
     */

    final void onPermission(Permission permission) {
        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();

        ResourceRef[] refs = _resources;

        for (int i = 0; refs != null && i < refs.length; i++) {
            Resource resource = refs[i].get();

            if (resource != null)
                resource.onPermission(permission);
        }

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();
    }

    //

    final void onUnresolved() {
        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();

        ResourceRef[] refs = _resources;

        for (int i = 0; refs != null && i < refs.length; i++) {
            Resource resource = refs[i].get();

            if (resource != null)
                resource.onUnresolved();
        }

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();
    }

    //

    final void getKnown(Object requester) {
        if (Debug.ENABLED)
            Debug.assertion(requester instanceof View || requester instanceof Resource);

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();

        if (Debug.TICKS_LOG)
            Log.write("getKnown(" + requester + ")");

        ResourceRef[] refs = _resources;

        for (int i = 0; refs != null && i < refs.length; i++) {
            Resource resource = refs[i].get();

            if (resource != null && resource != requester)
                resource.getKnown();
        }

        View[] views = _views;

        for (int i = 0; views != null && i < views.length; i++)
            if (views[i] != requester)
                views[i].getKnown(this);

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();
    }

    //

    final void onKnown(Object source, long[] ticks) {
        if (Debug.ENABLED) {
            Debug.assertion(source instanceof View || source instanceof Resource);
            Tick.checkSet(ticks);
        }

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();

        if (Debug.TICKS_LOG) {
            String text = "";

            for (int i = 0; i < ticks.length; i++)
                if (!Tick.isNull(ticks[i]))
                    text += ", " + Tick.toString(ticks[i]);

            Log.write("onKnown(" + source + text + ")");
        }

        ResourceRef[] refs = _resources;

        for (int i = 0; refs != null && i < refs.length; i++) {
            Resource resource = refs[i].get();

            if (resource != null && resource != source)
                resource.onKnown(ticks);
        }

        View[] views = _views;

        for (int i = 0; views != null && i < views.length; i++)
            if (views[i] != source)
                views[i].onKnown(this, ticks);

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();
    }

    //

    final void getBlock(Object requester, long tick) {
        if (Debug.ENABLED)
            Debug.assertion(requester instanceof View || requester instanceof Resource);

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();

        if (Debug.TICKS_LOG)
            Log.write("getBlock(" + requester + ", " + Tick.toString(tick) + ", " + this + ")");

        InFlight.get(this, tick, requester);

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();
    }

    final void startGetBlock(Object requester, long tick) {
        ResourceRef[] refs = _resources;

        for (int i = 0; refs != null && i < refs.length; i++) {
            Resource resource = refs[i].get();

            if (resource != null && resource != requester)
                resource.getBlock(tick);
        }

        View[] views = _views;

        /*
         * TODO spread read load between locations, maybe using some kind of round robin
         * over priority queues?
         */
        for (int i = 0; views != null && i < views.length; i++)
            if (views[i] != requester)
                views[i].getBlock(this, tick);
    }

    //

    final void cancelBlock(Object requester, long tick) {
        if (Debug.ENABLED)
            Debug.assertion(requester instanceof View || requester instanceof Resource);

        if (Debug.TICKS_LOG)
            Log.write("cancelBlock(" + requester + ", " + Tick.toString(tick) + ", " + this + ")");

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();

        InFlight.cancel(this, tick, requester);
    }

    //

    Exception onBlock(Object source, long tick, Buff[] buffs, long[] removals, boolean requested, Connection connection, boolean sendAck) {
        if (Debug.ENABLED) {
            Debug.assertion(buffs.length > 0);
            Debug.assertion(source instanceof View || source instanceof Resource);

            if (removals != null)
                Tick.checkSet(removals);
        }

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();

        if (Debug.TICKS_LOG)
            Log.write("onBlock(" + source + ", " + Tick.toString(tick) + ", " + this + ")");

        ThreadContext context = ThreadContext.get();

        if (Debug.THREADS)
            ThreadAssert.resume(context);

        List<Resource> resources = context.getReader().resources();

        if (Debug.THREADS)
            ThreadAssert.suspend(context);

        if (Debug.ENABLED)
            Debug.assertion(resources.size() == 0);

        if (requested) {
            Get get = InFlight.onBlock(this, tick, connection);

            if (get != null) {
                if (get.From instanceof Object[]) {
                    Object[] from = (Object[]) get.From;

                    for (int i = 0; i < from.length; i++)
                        onBlock(source, tick, buffs, removals, from[i], resources);
                } else
                    onBlock(source, tick, buffs, removals, get.From, resources);
            }
        } else {
            if (connection != null && sendAck)
                InFlight.needsAck(this, tick, connection);

            ResourceRef[] refs = _resources;

            for (int i = 0; refs != null && i < refs.length; i++) {
                Resource resource = refs[i].get();

                if (resource != null && resource != source)
                    resources.add(resource);
            }

            View[] views = _views;

            for (int i = 0; views != null && i < views.length; i++)
                if (views[i] != source)
                    views[i].onBlock(this, tick, buffs, removals, requested);
        }

        if (Debug.THREADS) {
            ThreadAssert.resume(context);
            ThreadAssert.exchangeTake(buffs);
        }

        if (Debug.ENABLED)
            for (int i = 0; i < buffs.length; i++)
                Debug.assertion(buffs[i].remaining() > 0);

        Exception exception = null;

        if (resources.size() > 0) {
            if (buffs.length == 0)
                exception = new Exception(Strings.INCOMPLETE_BLOCK);
            else {
                context.getReader().reset();
                context.getReader().setBuff(buffs[0]);
                context.getReader().startRead();
                int index = 0;

                for (;;) {
                    try {
                        context.getReader().read(tick);

                        if (++index == buffs.length) {
                            if (context.getReader().interrupted())
                                throw new Exception(Strings.INCOMPLETE_BLOCK);

                            break;
                        }
                    } catch (Exception e) {
                        exception = e;
                        break;
                    }

                    buffs[index].putLeftover(context.getReader().getBuff());
                    context.getReader().setBuff(buffs[index]);
                }
            }

            if (exception != null) {
                context.getReader().clean();

                for (int i = 0; i < resources.size(); i++)
                    resources.get(i).onFailed(source, tick);
            }

            if (Debug.ENABLED)
                context.getReader().assertIdle();

            resources.clear();
        }

        if (Debug.THREADS) {
            for (int i = 0; i < buffs.length; i++)
                ThreadAssert.exchangeGive(buffs, buffs[i]);

            ThreadAssert.suspend(context);
        }

        return exception;
    }

    private final void onBlock(Object source, long tick, Buff[] buffs, long[] removals, Object requester, List<Resource> resources) {
        if (requester instanceof View) {
            View view = (View) requester;
            view.onBlock(this, tick, buffs, removals, true);
            return;
        }

        if (requester instanceof Resource)
            resources.add((Resource) requester);
    }

    final void onAck(View source, long tick) {
        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();

        if (Debug.TICKS_LOG)
            Log.write("onAck(" + source + ", " + Tick.toString(tick) + ", " + this + ")");

        InFlight.onAck(this, tick);

        ResourceRef[] refs = _resources;

        for (int i = 0; refs != null && i < refs.length; i++) {
            Resource resource = refs[i].get();

            if (resource != null)
                resource.onAck(source, tick);
        }

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();
    }

    //

    @Override
    public String toString() {
        return _origin + _path;
    }

    private abstract class ResourceRef extends PlatformRef<Resource> {

        final Workspace Workspace;

        ResourceRef(Resource resource) {
            super(resource, Platform.get().getReferenceQueue());

            Workspace = resource.workspaceImpl();
            resource.onReferenced(this);
        }

        @Override
        void collected() {
            onGCed(this);
        }
    }

    private final class ClosedRef extends ResourceRef {

        ClosedRef(Resource resource) {
            super(resource);
        }
    }

    private final class LoadingRef extends ResourceRef {

        final FutureWithCallbacks<Object> Future;

        LoadingRef(Resource resource, FutureWithCallbacks<Object> future) {
            super(resource);

            Future = future;
        }
    }

    private final class LoadedRef extends ResourceRef {

        LoadedRef(Resource resource) {
            super(resource);
        }
    }
}
