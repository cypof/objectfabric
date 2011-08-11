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

import of4gwt.misc.Log;
import of4gwt.misc.PlatformAdapter;

/**
 * This class can be overriden by the user to react to overload situations. Overload of a
 * branch should never occur using default settings.
 * <nl>
 * Overload when sending objects might happen if your application sends objects faster
 * than can be serialized over the corresponding connection. Sending large amounts of data
 * using ObjectFabric should be done instead by sending an object once, and updating its
 * fields. Performance will be higher, and ObjectFabric will automatically adapt
 * serialization rate by coalescing updates. Check Transaction.Granularity.COALESCE.
 * <nl>
 * Overload when invoking remote methods can happen if your application invokes methods
 * faster than the remote machine can execute them. One way to throttle calls is to
 * provide your own OverloadHandler (override OverloadHandler and set an instance using
 * setInstance). onPendingCallsThresholdReached is invoke every time too many methods are
 * pending.
 */
public class OverloadHandler {

    public static final int MAP_QUEUE_SIZE_THRESHOLD = 40;

    public static final int MAP_QUEUE_SIZE_MAXIMUM = 100;

    public static final int DEFAULT_PENDING_SENDS_THRESHOLD = 1000;

    public static final int DEFAULT_PENDING_CALLS_THRESHOLD = 50;

    private static OverloadHandler _instance = PlatformAdapter.createOverloadHandler();

    private final int _pendingSendsThreshold, _pendingCallsThreshold;

    public OverloadHandler() {
        this(DEFAULT_PENDING_SENDS_THRESHOLD, DEFAULT_PENDING_CALLS_THRESHOLD);
    }

    public OverloadHandler(int pendingSendsThreshold, int pendingCallsThreshold) {
        _pendingSendsThreshold = pendingSendsThreshold;
        _pendingCallsThreshold = pendingCallsThreshold;
    }

    public static OverloadHandler getInstance() {
        return _instance;
    }

    public static void setInstance(OverloadHandler value) {
        _instance = value;
    }

    /*
     * Branches.
     */

    public static final int getMapQueueSize(Transaction branch) {
        return branch.getSharedSnapshot().getVersionMaps().length;
    }

    public static final boolean isOverloaded(Transaction branch) {
        return getMapQueueSize(branch) > MAP_QUEUE_SIZE_THRESHOLD;
    }

    /**
     * Called when the size of the queue of maps reaches MAP_QUEUE_SIZE_THRESHOLD. This
     * method is called from the thread that is trying to commit a new transaction on this
     * branch.
     * <nl>
     * The queue of maps contains commits that still need to be processed by extensions.
     * It can grow when e.g. for a branch with Granularity == ALL when an extension does
     * not process changes fast enough. It can happen also when a site receives
     * distributed transactions from multiple sites, as maps from different sites cannot
     * be merged together before distribution extensions have processed them. Those
     * callbacks can be used to slow down data production, and eventually block a writing
     * thread if the overload continues.
     */
    protected void onMapQueueSizeThresholdReached(Transaction branch) {
    }

    /**
     * Called when the size of the queue of maps reaches MAP_QUEUE_SIZE_MAXIMUM. This
     * method is called from the thread that is trying to commit a new transaction on this
     * branch.
     * <nl>
     * Default behavior is to block the current thread for a small amount of time. This
     * reduces the rate of writing threads, and allows the overload to resolve itself. In
     * some cases it can be useful to override this method to implement another form of
     * thread blocking, e.g. pumping an event loop.
     */
    protected void onMapQueueSizeMaximumReached(Transaction branch, boolean firstNotification) {
        if (CompileTimeSettings.DISALLOW_THREAD_BLOCKING)
            throw new RuntimeException(Strings.THREAD_BLOCKING_DISALLOWED);

        if (firstNotification)
            Log.write("Warning (OverloadHandler): trunk " + branch + " has too many memory snapshots.");
    }

    /*
     * Object sends.
     */

    /**
     * Number of objects scheduled to be sent on this connection. Objects are buffered
     * until received by the remote site. If the send rate is too high, the buffer can
     * grow excessively.
     */
    public static final int getPendingSendCount(Connection connection) {
        if (connection.getEndpoint() == null)
            throw new RuntimeException(Strings.ONLY_ON_ORIGIN_OR_TARGET);

        return connection.getEndpoint().getPendingSendCount();
    }

    public final int getPendingSendsThreshold() {
        return _pendingSendsThreshold;
    }

    /**
     * Called when the number of objects to be sent reaches the threshold. This method is
     * called from the user thread that is trying to add a new object to send, which can
     * be blocked as a throttling solution, e.g. Thread.sleep(1), or trigger an
     * application-specific mechanism.
     */
    protected void onPendingSendsThresholdReached(Connection connection) {
    }

    /*
     * Method calls.
     */

    /**
     * Method calls are buffered when their execution is remote. If an application invokes
     * methods faster than the remote machine can execute them, the buffer can grow
     * excessively.
     */
    public static final int getPendingCallCount(Connection connection) {
        if (connection.getEndpoint() == null)
            throw new RuntimeException(Strings.ONLY_ON_ORIGIN_OR_TARGET);

        return connection.getEndpoint().getPendingMethodCallCount();
    }

    public final int getPendingCallsThreshold() {
        return _pendingCallsThreshold;
    }

    /**
     * Called when the number of pending calls reaches PENDING_CALLS_THRESHOLD. This
     * method is called from the user thread that is trying to add a new method call,
     * which can be blocked as a throttling solution, e.g. Thread.sleep(1), or trigger an
     * application-specific mechanism.
     */
    protected void onPendingCallsThresholdReached(Connection connection) {
    }
}
