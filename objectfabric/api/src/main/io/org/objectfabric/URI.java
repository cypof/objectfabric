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

    final FutureWithCallbacks<Resource> open(final Workspace workspace) {
        ResourceRef[] refs = _resources;
        int i = index(refs, workspace);
        Resource resource = i >= 0 ? refs[i].get() : null;
        FutureWithCallbacks<Resource> future;

        if (resource != null) {
            if (refs[i] instanceof LoadedRef) {
                future = new FutureWithCallbacks<Resource>(FutureWithCallback.NOP_CALLBACK, null);
                future.set(resource);
            } else
                future = ((LoadingRef) refs[i]).Future;
        } else {
            Resource created = workspace.newResource(this);
            boolean load = false;

            synchronized (this) {
                refs = _resources;
                i = index(refs, workspace);
                resource = i >= 0 ? refs[i].get() : null;

                if (resource != null) {
                    if (refs[i] instanceof LoadedRef) {
                        future = new FutureWithCallbacks<Resource>(FutureWithCallback.NOP_CALLBACK, null);
                        future.set(resource);
                    } else
                        future = ((LoadingRef) refs[i]).Future;
                } else {
                    if (Debug.ENABLED)
                        Helper.instance().Resources.put(created, created);

                    resource = created;

                    LoadingRef ref = new LoadingRef(resource, //
                            new FutureWithCallbacks<Resource>(FutureWithCallback.NOP_CALLBACK, null) {

                                @Override
                                public boolean cancel(boolean mayInterruptIfRunning) {
                                    URI.this.cancel(workspace);
                                    return super.cancel(mayInterruptIfRunning);
                                }
                            });

                    if (i >= 0) {
                        refs[i].clear();
                        refs[i] = ref;
                    } else {
                        if (refs != null) {
                            ResourceRef[] temp = new ResourceRef[refs.length + 1];
                            System.arraycopy(refs, 0, temp, 0, refs.length);
                            temp[temp.length - 1] = ref;
                            refs = temp;
                        } else {
                            refs = new ResourceRef[] { ref };

                            getOrCreate(_origin).open(this);
                            Location[] caches = workspace.caches();

                            if (caches != null)
                                for (int t = 0; t < caches.length; t++)
                                    getOrCreate(caches[t]).open(this);
                        }

                        _resources = refs;
                    }

                    future = ref.Future;
                    load = true;
                }
            }

            if (load) {
                Object key;

                if (Debug.THREADS)
                    ThreadAssert.suspend(key = new Object());

                getKnown(resource);

                if (Debug.THREADS)
                    ThreadAssert.resume(key);
            }
        }

        return future;
    }

    final void onCancel(Resource resource, Exception exception) {
        FutureWithCallbacks<Resource> future = cancel(resource.workspaceImpl());

        if (future != null)
            future.setException(exception);
    }

    final void markLoaded(Resource resource) {
        synchronized (this) {
            ResourceRef[] refs = _resources;
            int i = index(refs, resource.workspaceImpl());

            if (i >= 0 && refs[i] instanceof LoadingRef) {
                LoadingRef ref = (LoadingRef) refs[i];

                if (Debug.ENABLED)
                    Debug.assertion(ref.Resource == resource);

                refs[i].clear();
                refs[i] = new LoadedRef(resource);
                ref.Future.set(resource);
            }
        }
    }

    final ResourceRef getRef(Workspace workspace) {
        ResourceRef[] refs = _resources;
        int i = index(refs, workspace);
        return i >= 0 ? refs[i] : null;
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

    final void runIf(Runnable runnable, boolean open) {
        synchronized (this) {
            ResourceRef[] refs = _resources;
            boolean current = refs != null;

            if (current == open)
                runnable.run();
        }
    }

    //

    private static int index(ResourceRef[] refs, Workspace workspace) {
        for (int i = 0; refs != null && i < refs.length; i++)
            if (refs[i].Workspace == workspace)
                return i;

        return -1;
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

    private final FutureWithCallbacks<Resource> cancel(Workspace workspace) {
        synchronized (this) {
            ResourceRef[] refs = _resources;
            int i = index(refs, workspace);

            if (i >= 0 && refs[i] instanceof LoadingRef) {
                LoadingRef ref = (LoadingRef) refs[i];
                remove(refs, i);
                return ref.Future;
            }

            return null;
        }
    }

    private final void remove(ResourceRef[] refs, int i) {
        ResourceRef[] update = new ResourceRef[refs.length - 1];
        int n = 0;

        for (int t = 0; t < refs.length; t++)
            if (t != i)
                update[n++] = refs[t];

        refs[i].clear();
        refs = update.length != 0 ? update : null;
        _resources = refs;

        if (refs == null)
            getOrCreate(_origin).close(this);
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

    Exception onBlock(Object source, long tick, Buff[] buffs, long[] removals, boolean requested, Connection connection, boolean sendAck, Location cache) {
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
                if (get.Requesters instanceof Object[]) {
                    Object[] requesters = (Object[]) get.Requesters;

                    for (int i = 0; i < requesters.length; i++)
                        onBlock(tick, buffs, removals, requesters[i], resources);
                } else
                    onBlock(tick, buffs, removals, get.Requesters, resources);
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
                if (views[i] != source && views[i].location() != cache)
                    views[i].onBlock(this, tick, buffs, removals, requested);
        }

        if (Debug.THREADS) {
            ThreadAssert.resume(context);
            ThreadAssert.exchangeTake(buffs);
        }

        if (Debug.ENABLED)
            for (int i = 0; i < buffs.length; i++)
                Debug.assertion(buffs[i].remaining() > 0);

        if (Stats.ENABLED) {
            int bytes = 0;

            for (int i = 0; i < buffs.length; i++)
                bytes += buffs[i].remaining();

            Stats.max(Stats.Instance.BlockMaxBytes, bytes);
        }

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
                    resources.get(i).onFailed(tick);
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

    private final void onBlock(long tick, Buff[] buffs, long[] removals, Object requester, List<Resource> resources) {
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

    abstract class ResourceRef extends PlatformRef<Resource> {

        final Resource Resource; // For GC

        final Workspace Workspace;

        ResourceRef(Resource resource) {
            super(resource, Platform.get().getReferenceQueue());

            Resource = resource;
            Workspace = resource.workspaceImpl();
            resource.onReferenced(this);
        }

        @Override
        void collected() {
            onGCed(this);
        }
    }

    private final class LoadingRef extends ResourceRef {

        final FutureWithCallbacks<Resource> Future;

        LoadingRef(Resource resource, FutureWithCallbacks<Resource> future) {
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
