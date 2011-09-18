/**
 * Copyright (c) ObjectFabric Inc. All rights reserved.
 *
 * This file is part of ObjectFabric (objectfabric.com).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package of4gwt;

import java.util.Map.Entry;
import of4gwt.misc.Executor;
import of4gwt.misc.AtomicInteger;

import of4gwt.TObject.UserTObject.SystemClass;
import of4gwt.Transaction.ConflictDetection;
import of4gwt.Transaction.Consistency;
import of4gwt.Transaction.Granularity;
import of4gwt.misc.Debug;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.PlatformConcurrentMap;
import of4gwt.misc.TransparentExecutor;

/**
 * A site is a location which contains one replica of a distributed object. It usually
 * represents a process, an AppDomain in .NET, or an isolated ClassLoader in Java.
 * Site.getLocal() identifies the current site.
 */
public final class Site extends SiteBase implements SystemClass {

    private static final Site _local;

    private Executor _methodExecutor = TransparentExecutor.getInstance();

    static {
        _local = (Site) DefaultObjectModelBase.getInstance().createInstance(Transaction.getLocalTrunk(), DefaultObjectModelBase.COM_OBJECTFABRIC_SITE_CLASS_ID, null);

        {
            Version shared = (Version) _local.getSharedVersion_objectfabric();

            if (Debug.ENABLED)
                Debug.assertion(shared._distance == 0); // Local

            // Make sure distance bit set to force distance update on remote sites
            shared.setBit(SiteBase.DISTANCE_INDEX);
        }

        registerDefaultObjectModel();

        _local.setTrunk(Transaction.getLocalTrunk());
    }

    protected static final void registerDefaultObjectModel() {
        ObjectModel.register(DefaultObjectModelBase.getInstance());
    }

    protected Site(Transaction trunk) {
        super(new Version(null), trunk);
    }

    public static final Site getLocal() {
        return _local;
    }

    //

    public final Transaction createTrunk() {
        return createTrunk(Transaction.DEFAULT_CONFLICT_DETECTION, Transaction.DEFAULT_CONSISTENCY, Transaction.DEFAULT_GRANULARITY, null);
    }

    public final Transaction createTrunk(ConflictDetection conflictDetection) {
        return createTrunk(conflictDetection, Transaction.DEFAULT_CONSISTENCY, Transaction.DEFAULT_GRANULARITY, null);
    }

    public final Transaction createTrunk(Consistency consistency) {
        return createTrunk(Transaction.DEFAULT_CONFLICT_DETECTION, consistency, Transaction.DEFAULT_GRANULARITY, null);
    }

    public final Transaction createTrunk(Granularity granularity) {
        return createTrunk(Transaction.DEFAULT_CONFLICT_DETECTION, Transaction.DEFAULT_CONSISTENCY, granularity, null);
    }

    public final Transaction createTrunk(Store store) {
        return createTrunk(Transaction.DEFAULT_CONFLICT_DETECTION, Transaction.DEFAULT_CONSISTENCY, Transaction.DEFAULT_GRANULARITY, store);
    }

    /**
     * Transactions are children of a trunk, in the same model as some source-control
     * solutions. Each Site creates a default trunk. For scalability, or to specify e.g. a
     * different consistency, this method creates new trunks. If you specify a store,
     * objects belonging to this trunk can be stored if they get referenced by the root of
     * the store.
     * <nl>
     * You can only create trunks on the local site.
     */
    public final Transaction createTrunk(ConflictDetection conflictDetection, Consistency consistency, Granularity granularity, Store store) {
        if (this != getLocal())
            throw new RuntimeException(Strings.ONLY_ON_LOCAL);

        return Transaction.createTrunk(conflictDetection, consistency, granularity, store);
    }

    //

    /**
     * Methods are by default executed on the current thread for transactional objects
     * created locally (See TransparentExecutor). For transactional objects that have been
     * created on another site, methods are run remotely on a thread pool.
     */
    public final Executor getMethodExecutor() {
        return _methodExecutor;
    }

    // TODO: improve method routing, for now latest connection
    final void setMethodExecutor(Executor value) {
        if (Debug.ENABLED)
            Debug.assertion(this != Site.getLocal());

        _methodExecutor = value;
    }

    /**
     * The 'distance' field is special, it keeps track of the distance to the site through
     * each connection. It is computed by adding one the value read from each connection.
     */
    protected static class Version extends SiteBase.Version {

        // TODO static for now, handle updates etc.
        private final PlatformConcurrentMap<Connection, Distance> _distances;

        public Version(SiteBase.Version shared) {
            super(shared, FIELD_COUNT);

            if (shared == null)
                _distances = new PlatformConcurrentMap<Connection, Distance>();
            else
                _distances = null;
        }

        public final boolean isShortestDistance(Connection connection) {
            for (;;) {
                if (Debug.ENABLED)
                    Helper.getInstance().disableEqualsOrHashCheck();

                Distance distance = _distances.get(connection);

                if (Debug.ENABLED)
                    Helper.getInstance().enableEqualsOrHashCheck();

                if (distance == null) {
                    /*
                     * Site has not been received from another site, it is either the
                     * local site, or has been loaded from a store. Connections must not
                     * intercept commits.
                     */
                    return false;
                }

                int shortest = distance.Shortest.get();

                if (shortest >= 0) {
                    // First check previous to make sure always return same
                    return shortest > 0;
                }

                int min = Integer.MAX_VALUE;
                int connectionDistance = Integer.MAX_VALUE;

                for (Entry<Connection, Distance> entry : _distances.entrySet()) {
                    if (Debug.ENABLED) // getValue can do get(Object) on Android
                        Helper.getInstance().disableEqualsOrHashCheck();

                    Distance value = entry.getValue();

                    if (Debug.ENABLED)
                        Helper.getInstance().enableEqualsOrHashCheck();

                    if (min > value.Value)
                        min = value.Value;

                    if (entry.getKey() == connection)
                        connectionDistance = value.Value;
                }

                if (Debug.ENABLED)
                    Debug.assertion(min != Integer.MAX_VALUE);

                boolean value = min == connectionDistance;

                if (distance.Shortest.compareAndSet(-1, value ? 1 : 0))
                    return value;
            }
        }

        @Override
        public void readWrite(Reader reader, int index) {
            super.readWrite(reader, index);

            if (index == DISTANCE_INDEX && !reader.interrupted()) {
                Site.Version shared = (Site.Version) getUnion();

                if (shared._distance == 0 && shared.getBit(SiteBase.DISTANCE_INDEX)) {
                    // Local or old local loaded from store

                    if (Debug.ENABLED)
                        Debug.assertion(shared._distances.size() == 0);
                } else {
                    if (reader instanceof DistributedReader) {
                        Connection connection = ((DistributedReader) reader).getEndpoint().getConnection();
                        Distance distancePlusConnection = new Distance(_distance + 1);

                        if (Debug.ENABLED) {
                            Helper.getInstance().disableEqualsOrHashCheck();
                            PlatformAdapter.assertEqualsAndHashCodeAreDefault(connection);
                        }

                        shared._distances.put(connection, distancePlusConnection);

                        if (Debug.ENABLED)
                            Helper.getInstance().enableEqualsOrHashCheck();

                        shared.updateDistance();
                    } else {
                        // Reading from store

                        if (_distance == 0)
                            shared._distance = 0; // Was local when persisted
                        else
                            shared._distance = Integer.MAX_VALUE; // Not connected

                        // Make sure distance bit set (C.f. static {})
                        shared.setBit(SiteBase.DISTANCE_INDEX);
                    }
                }

                // Prevent merge of the field, shared value is already updated
                unsetBit(DISTANCE_INDEX);
            }
        }

        public final void onDisconnection(Connection connection) {
            if (Debug.ENABLED)
                Helper.getInstance().disableEqualsOrHashCheck();

            _distances.remove(connection);

            if (Debug.ENABLED)
                Helper.getInstance().enableEqualsOrHashCheck();

            updateDistance();
        }

        private final void updateDistance() {
            int min = Integer.MAX_VALUE;

            for (Entry<Connection, Distance> entry : _distances.entrySet()) {
                if (Debug.ENABLED) // getValue can do get(Object) on Android
                    Helper.getInstance().disableEqualsOrHashCheck();

                Distance value = entry.getValue();

                if (Debug.ENABLED)
                    Helper.getInstance().enableEqualsOrHashCheck();

                if (min > value.Value)
                    min = value.Value;
            }

            _distance = min;
            setBit(SiteBase.DISTANCE_INDEX);
        }

        @Override
        public TObject.Version createVersion() {
            return new Site.Version(this);
        }

        @Override
        public boolean isImmutable() {
            return true;
        }

        @Override
        public String toString() {
            return super.toString() + (getShared() == Site.getLocal().getSharedVersion_objectfabric() ? " (Local)" : "");
        }
    }

    /**
     * Avoids using Integer for the .NET version.
     */
    private static final class Distance {

        public final int Value;

        public final AtomicInteger Shortest = new AtomicInteger(-1);

        public Distance(int value) {
            Value = value;
        }
    }

    // Debug

    final void assertIdle() {
        Debug.assertion(((Version) getSharedVersion_objectfabric())._distance == 0);
        Debug.assertion(((Version) getSharedVersion_objectfabric())._distances.size() == 0);
    }
}
