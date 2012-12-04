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

import java.util.concurrent.atomic.AtomicReference;

import org.objectfabric.URI.ResourceRef;

/**
 * Lets an application specify its own resource storage and data format.
 */
public abstract class CustomLocation extends Origin {

    private final Origin _innerLocation;

    private final AtomicReference<Workspace> _workspace = new AtomicReference<Workspace>();

    /**
     * This class must be backed by a regular OF location to keep track of versions
     * ordering. This information is not strictly required, but loosing it might lead to
     * ordering errors during synchronization. E.g. an old version stored in a client
     * cache might become the new latest version instead of being discarded during
     * synchronization. When conflicts will be exposed by OF's API, this will turn into
     * false positives.
     * 
     * @param backingLocation
     *            Backing OF location. Must not be registered in any Workspace or Server.
     */
    public CustomLocation(Origin backingLocation) {
        super(false);

        _innerLocation = backingLocation;
    }

    /**
     * A resource has been requested, set its value using data from the custom location.
     * This method should not block as it might be called from an internal or IO thread.
     * The resource value can instead be set asynchronously when data is ready.
     */
    protected abstract void onGet(Resource resource);

    /**
     * A resource has changed, update the custom location using its new value, and call
     * the acknowledgment runnable when done. Any change triggers this method, even if the
     * resource is a graph or collection and only a subset changed.<br>
     * <br>
     * <b>Warning:</b> If value contains TObjects, its content must not be accessed once
     * method has returned, or it might be invalid. All serialization must occur during
     * method execution, even if acknowledgment is invoked later on. <br>
     * <br>
     * This method should not block as it might be called from an internal or IO thread.
     * Invoking the runnable notifies OF data is safely stored. It lets workspace flushes
     * complete, if any are pending.
     */
    protected abstract void onChange(Resource resource, Runnable ack);

    @Override
    View newView(URI uri) {
        if (uri.origin() == this)
            return new CustomView(this);

        if (Debug.ENABLED)
            Debug.assertion(uri.origin() == _innerLocation);

        return new Bridge(this);
    }

    @Override
    void onView(URI uri, View view) {
        if (uri.origin() == this) {
            CustomView outerView = (CustomView) view;
            outerView._innerURI = _innerLocation.getURI(uri.path());
            outerView._innerURI.getOrCreate(_innerLocation);
            outerView._bridge = (Bridge) outerView._innerURI.getOrCreate(this);
            outerView._bridge._outerURI = uri;
        }
    }

    static final class CustomView extends View {

        URI _innerURI;

        Bridge _bridge;

        CustomResource _resource;

        CustomView(CustomLocation outerLocation) {
            super(outerLocation);
        }

        @Override
        void getKnown(URI outerUri) {
            _innerURI.getKnown(_bridge);
        }

        @Override
        void onKnown(URI outerUri, long[] ticks) {
        }

        @Override
        void getBlock(URI outerUri, long tick) {
        }

        @Override
        void onBlock(URI outerUri, long tick, Buff[] buffs, long[] removals, boolean requested) {
            transferBlock(_innerURI, _bridge, tick, buffs, removals, requested);
        }
    }

    private static void transferBlock(URI uri, View view, long tick, Buff[] buffs, long[] removals, boolean requested) {
        if (Debug.THREADS)
            ThreadAssert.exchangeTake(buffs);

        final Buff[] duplicates = new Buff[buffs.length];

        for (int i = 0; i < buffs.length; i++) {
            duplicates[i] = buffs[i].duplicate();

            if (Debug.THREADS) {
                ThreadAssert.exchangeGive(duplicates, duplicates[i]);
                ThreadAssert.exchangeGive(buffs, buffs[i]);
            }
        }

        uri.onBlock(view, tick, duplicates, removals, requested, null, false, null);

        if (Debug.THREADS)
            ThreadAssert.exchangeTake(duplicates);

        for (int i = 0; i < duplicates.length; i++)
            duplicates[i].recycle();
    }

    static final class Bridge extends View {

        URI _outerURI;

        CustomResource _innerResource;

        Bridge(CustomLocation outerLocation) {
            super(outerLocation);
        }

        @Override
        void getKnown(URI innerURI) {
            try {
                CustomLocation location = (CustomLocation) location();
                location.onGet(_innerResource);
            } catch (Exception ex) {
                Log.write(ex);
            }
        }

        @Override
        void onKnown(URI innerUri, long[] ticks) {
            if (ticks.length != 0) {
                View view = _outerURI.getOrCreate(_innerResource._outerLocation);
                _outerURI.onKnown(view, ticks);
            } else {
                Workspace workspace, created = null;
                CustomLocation location = (CustomLocation) location();

                // Delays creation of workspace so that Platform is set.
                // TODO remove or simplify
                for (;;) {
                    workspace = location._workspace.get();

                    if (workspace != null)
                        break;

                    if (created == null)
                        created = Platform.get().newCustomWorkspace(location);

                    if (location._workspace.compareAndSet(null, workspace = created)) {
                        workspace.startWatcher();
                        break;
                    }
                }

                Resource resource;

                for (;;) {
                    innerUri.open(workspace);
                    ResourceRef ref = innerUri.getRef(workspace);
                    resource = ref.get();

                    if (resource != null)
                        break;
                }

                _innerResource = (CustomResource) resource;
                _innerResource._outerURI = _outerURI;
            }
        }

        @Override
        void getBlock(URI innerUri, long tick) {
        }

        @Override
        void onBlock(URI innerUri, long tick, Buff[] buffs, long[] removals, boolean requested) {
            View view = _outerURI.getOrCreate((CustomLocation) location());
            transferBlock(_outerURI, view, tick, buffs, removals, requested);
        }
    }

    static final class CustomResource extends Resource {

        final CustomLocation _outerLocation;

        URI _outerURI;

        CustomResource(Workspace workspace, URI innerUri, CustomLocation outerLocation) {
            super(workspace, innerUri);

            _outerLocation = outerLocation;
        }

        @Override
        void onNewBlock() {
            tellKnown();
            super.onNewBlock();
        }

        // @Override
        // boolean cancelAck(Object source) {
        // return source == _view;
        // }

        @Override
        void onLoad(Snapshot snapshot, final List<Block> acks) {
            if (Debug.ENABLED)
                Debug.assertion(workspace().transaction() == null);

            Transaction transaction = workspace().getOrCreateTransaction();
            int flags = TransactionBase.FLAG_IGNORE_READS | TransactionBase.FLAG_NO_WRITES | TransactionBase.FLAG_COMMITTED;
            workspace().startImpl(transaction, flags, snapshot);

            if (Debug.ENABLED)
                transaction.checkInvariants();

            try {
                _outerLocation.onChange(this, new Runnable() {

                    @Override
                    public void run() {
                        Object key;

                        if (Debug.THREADS)
                            ThreadAssert.suspend(key = new Object());

                        View view = _outerURI.getOrCreate(_outerLocation);

                        for (int i = 0; i < acks.size(); i++)
                            _outerURI.onAck(view, acks.get(i).Tick);

                        if (Debug.THREADS)
                            ThreadAssert.resume(key);
                    }
                });
            } catch (Exception ex) {
                Log.write(ex);
            }

            transaction.reset();

            if (Debug.THREADS)
                ThreadAssert.removePrivate(transaction);

            workspace().recycle(transaction);
        }
    }

    @Override
    public String toString() {
        return "custom://";
    }
}