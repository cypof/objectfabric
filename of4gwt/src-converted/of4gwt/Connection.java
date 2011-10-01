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

import of4gwt.misc.Executor;
import of4gwt.misc.Future;
import of4gwt.misc.AtomicReference;

import of4gwt.Extension.TObjectMapEntry;
import of4gwt.Transaction.ConflictDetection;
import com.google.gwt.user.client.rpc.AsyncCallback;
import of4gwt.misc.ClosedConnectionException;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.Log;
import of4gwt.misc.OverrideAssert;
import of4gwt.misc.PlatformThreadLocal;
import of4gwt.misc.Queue;
import of4gwt.misc.SparseArrayHelper;

/**
 * Allows sites to communicate with each other.
 */
public class Connection extends ConnectionBase {

    private final Endpoint _endpoint;

    private static final PlatformThreadLocal<Connection> _current = new PlatformThreadLocal<Connection>();

    // Constructor for object model
    Connection(Transaction trunk, Site target) {
        this(trunk);
    }

    Connection(Transaction trunk) {
        super(new ConnectionBase.Version(null, FIELD_COUNT), trunk, null);

        _endpoint = null;
    }

    protected Connection(Transaction trunk, Site target, Validator validator) {
        super(new ConnectionBase.Version(null, FIELD_COUNT), trunk, target);

        _endpoint = new Endpoint(target == null, validator);
        _endpoint.setConnection(this);
    }

    protected final Endpoint getEndpoint() {
        return _endpoint;
    }

    public void close() {
        close(new ClosedConnectionException());
    }

    public void close(Exception e) {
        if (_endpoint == null)
            throw new RuntimeException(Strings.ONLY_ON_ORIGIN_OR_TARGET);

        OverrideAssert.add(this);
        close_(e);
        OverrideAssert.end(this);
    }

    protected void close_(Exception e) {
        OverrideAssert.set(this);
    }

    //

    /**
     * Send this object to the remote site. Objects can have one of the types listed on
     * ImmutableClass (E.g. String, int, byte[] etc.), or derive from TObject. Events
     * occurring on TObjects will be replicated between both sites until it is
     * garbage-collected on one of them.
     * <nl>
     * Check of4gwt.OverloadHandler if you need to throttle the speed at switch
     * your application sends object.
     */
    public void send(Object object) {
        if (_endpoint == null)
            throw new RuntimeException(Strings.ONLY_ON_ORIGIN_OR_TARGET);

        _endpoint.send(object);
    }

    /**
     * Send an empty message to the remote site. Useful if connection can expire.
     */
    public void sendHeartbeat() {
        _endpoint.sendHeartbeat();
    }

    //

    /**
     * When called in a method implementation, returns the connection which invoked the
     * method, or null if the call is local.
     */
    public static final Connection getCurrent() {
        return _current.get();
    }

    static final void setCurrent(Connection value) {
        _current.set(value);
    }

    //

    protected void onDialogEstablished() {
    }

    protected void onObject(Object object) {
    }

    //

    protected final void startRead() {
        if (Debug.ENABLED)
            Debug.assertion(Helper.getInstance().getNoTransaction());

        OverrideAssert.add(this);
        onReadStarted();
        OverrideAssert.end(this);
    }

    protected void onReadStarted() {
        OverrideAssert.set(this);

        _endpoint.startRead();
    }

    protected final void stopRead(Exception e) {
        if (Debug.ENABLED)
            Debug.assertion(Helper.getInstance().getNoTransaction());

        OverrideAssert.add(this);
        onReadStopped(e);
        OverrideAssert.end(this);
    }

    protected void onReadStopped(Exception e) {
        OverrideAssert.set(this);

        _endpoint.stopRead(e);
    }

    //

    protected final void startWrite() {
        if (Debug.ENABLED)
            Debug.assertion(Helper.getInstance().getNoTransaction());

        OverrideAssert.add(this);
        onWriteStarted();
        OverrideAssert.end(this);
    }

    protected void onWriteStarted() {
        OverrideAssert.set(this);

        _endpoint.startWrite();
    }

    protected final void stopWrite(Exception e) {
        if (Debug.ENABLED)
            Debug.assertion(Helper.getInstance().getNoTransaction());

        OverrideAssert.add(this);
        onWriteStopped(e);
        OverrideAssert.end(this);
    }

    protected void onWriteStopped(Exception e) {
        OverrideAssert.set(this);

        _endpoint.stopWrite(e);

        if (Debug.ENABLED) {
            _endpoint._interceptor.getAcknowledger().assertIdle();
            _endpoint._propagator.getWalker().assertIdle();
        }
    }

    //

    protected void requestWrite() {
    }

    protected static final AsyncOptions getDefaultAsyncOptions() {
        return OF.getDefaultAsyncOptions();
    }

    protected static void setNoTransaction(boolean value) {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        Helper.getInstance().setNoTransaction(value);
    }

    protected static boolean assertCurrentTransactionNull() {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        return Transaction.currentNull();
    }

    protected static final void onThrowable(Throwable t) {
        OF.getConfig().onThrowable(t);
    }

    //

    protected final void read(byte[] buffer, int offset, int limit) {
        if (Debug.ENABLED)
            Debug.assertion(Helper.getInstance().getNoTransaction());

        getEndpoint().read(buffer, offset, limit);
    }

    protected final int write(byte[] buffer, int offset, int limit) {
        if (Debug.ENABLED)
            Debug.assertion(Helper.getInstance().getNoTransaction());

        return getEndpoint().write(buffer, offset, limit);
    }

    static final class Endpoint extends Multiplexer {

        enum Status {
            UNKNOWN, CREATED, SNAPSHOTTED, GCED
        }

        private final Status[] STATUS_VALUES = Status.values();

        private final boolean _clientSide;

        private final Validator _validator;

        private final ControllerOutWriter _controllerOutWriter;

        private final ControllerInWriter _controllerInWriter;

        private final CallOutWriter _callOutWriter;

        private final CallInWriter _callInWriter;

        private final InterceptorWriter _interceptor;

        private final PropagatorWriter _propagator;

        private final DGCOutWriter _dgcOutWriter;

        private final DGCInWriter _dgcInWriter;

        private final List<UserTObject> _newTObjects = new List<TObject.UserTObject>();

        // TODO: replace by map of session to bit set
        private TObject.Version[] _remote = new TObject.Version[SparseArrayHelper.DEFAULT_CAPACITY];

        // TODO: use _remote + temp sets for CREATED & GCED
        private byte[] _status = new byte[SparseArrayHelper.DEFAULT_CAPACITY];

        @SuppressWarnings("unchecked")
        private TObjectMapEntry<Queue<TObject.Version>>[] _pendingSnapshots = new TObjectMapEntry[SparseArrayHelper.DEFAULT_CAPACITY];

        @SuppressWarnings("unchecked")
        private TObjectMapEntry<Queue<TObject.Version>>[] _pendingDisconnections = new TObjectMapEntry[SparseArrayHelper.DEFAULT_CAPACITY];

        // Avoid object to be GCed if propagating, GC must happen on client first
        // TODO shared collection or counter + reference in the shared version or Session
        // atomic array?
        private UserTObject[] _gcPreventers = new UserTObject[SparseArrayHelper.DEFAULT_CAPACITY];

        Endpoint(boolean clientSide, Validator validator) {
            super(8, 8, !clientSide, clientSide);

            _clientSide = clientSide;
            _validator = validator;

            //

            _controllerOutWriter = new ControllerOutWriter(this);
            _controllerInWriter = new ControllerInWriter(this);
            setWriter(0, _controllerOutWriter);
            setWriter(1, _controllerInWriter);

            ControllerInReader controllerInReader = new ControllerInReader(this, _controllerInWriter);
            ControllerOutReader controllerOutReader = new ControllerOutReader(this);
            setReader(0, controllerInReader);
            setReader(1, controllerOutReader);

            //

            _callOutWriter = new CallOutWriter(this);
            _callInWriter = new CallInWriter(this);
            setWriter(2, _callOutWriter);
            setWriter(3, _callInWriter);

            CallInReader callInReader = new CallInReader(this, _callInWriter);
            CallOutReader callOutReader = new CallOutReader(this, _callOutWriter);
            setReader(2, callInReader);
            setReader(3, callOutReader);

            //

            InterceptorWriter interceptorWriter = new InterceptorWriter(this);
            PropagatorWriter propagatorWriter = new PropagatorWriter(this);
            setWriter(4, interceptorWriter);
            setWriter(5, propagatorWriter);

            _interceptor = interceptorWriter;
            _propagator = propagatorWriter;

            PropagatorReader propagationReader = new PropagatorReader(this, propagatorWriter);
            InterceptorReader interceptionReader = new InterceptorReader(this, interceptorWriter);
            setReader(4, propagationReader);
            setReader(5, interceptionReader);

            propagatorWriter.setReader(propagationReader);

            //

            _dgcOutWriter = new DGCOutWriter(this);
            _dgcInWriter = new DGCInWriter(this);
            setWriter(6, _dgcOutWriter);
            setWriter(7, _dgcInWriter);

            DGCInReader dgcInReader = new DGCInReader(this, _dgcInWriter);
            DGCOutReader dgcOutReader = new DGCOutReader(this, _dgcOutWriter);
            setReader(6, dgcInReader);
            setReader(7, dgcOutReader);

            if (Debug.THREADS)
                threadExchange();
        }

        final InterceptorWriter getInterceptor() {
            return _interceptor;
        }

        final PropagatorWriter getPropagator() {
            return _propagator;
        }

        final int getPendingSendCount() {
            return _controllerOutWriter.getPendingSendCount();
        }

        final void send(Object object) {
            _controllerOutWriter.addObjectToSend(object);
        }

        final void sendHeartbeat() {
            _controllerOutWriter.sendHeartbeat();
        }

        //

        final int getPendingMethodCallCount() {
            return _callOutWriter.getPendingCalls().size();
        }

        final Executor getMethodExecutor() {
            return _callOutWriter;
        }

        //

        final List<UserTObject> getNewTObjects() {
            return _newTObjects;
        }

        //

        final boolean clientSide() {
            return _clientSide;
        }

        final Validator getValidator() {
            return _validator;
        }

        //

        final Status getStatus(TObject.Version shared) {
            if (Debug.THREADS)
                assertWriteThread();

            int index = VersionSet.indexOf(_remote, shared);

            if (index >= 0)
                return STATUS_VALUES[_status[index]];

            return Status.UNKNOWN;
        }

        final void setStatus(TObject.Version shared, Status status) {
            int index = getOrCreateStatusIndex(shared);
            _status[index] = (byte) status.ordinal();
        }

        final int getOrCreateStatusIndex(TObject.Version shared) {
            if (Debug.THREADS)
                assertWriteThread();

            int index = VersionSet.indexOf(_remote, shared);

            if (index < 0) {
                while ((index = VersionSet.tryToAdd(_remote, shared)) == SparseArrayHelper.REHASH) {
                    TObject.Version[] previous = _remote;
                    byte[] previousStatus = _status;

                    for (;;) {
                        _remote = new TObject.Version[_remote.length << SparseArrayHelper.TIMES_TWO_SHIFT];
                        _status = new byte[_remote.length];

                        if (rehash(previous, previousStatus))
                            break;
                    }
                }
            }

            return index;
        }

        private boolean rehash(TObject.Version[] previous, byte[] previousStatus) {
            for (int i = previous.length - 1; i >= 0; i--) {
                if (previous[i] != null && previous[i] != VersionSet.REMOVED) {
                    if (Debug.ENABLED)
                        Debug.assertion(!VersionSet.contains(_remote, previous[i]));

                    int index = VersionSet.tryToAdd(_remote, previous[i]);

                    if (index < 0) {
                        if (Debug.ENABLED)
                            Debug.assertion(index == SparseArrayHelper.REHASH);

                        return false;
                    }

                    _status[index] = previousStatus[i];
                }
            }

            if (Debug.ENABLED)
                VersionSet.checkInvariants(_remote, false);

            return true;
        }

        final void disconnect(TObject.Version shared) {
            if (Debug.THREADS)
                assertWriteThread();

            int index = VersionSet.remove(_remote, shared);
            _status[index] = 0;
        }

        //

        final void addPendingSnapshot(TObject.Version shared) {
            if (Debug.THREADS)
                assertWriteThread();

            if (Debug.ENABLED)
                Debug.assertion(getStatus(shared) == Status.CREATED);

            Transaction trunk = shared.getTrunk();
            Queue<TObject.Version> queue = TObjectMapEntry.get(_pendingSnapshots, trunk);

            if (queue == null) {
                queue = new Queue<TObject.Version>();
                TObjectMapEntry<Queue<TObject.Version>> entry = new TObjectMapEntry<Queue<TObject.Version>>(trunk, queue);
                TObjectMapEntry.put(_pendingSnapshots, entry);
            }

            if (queue.size() == 0) {
                Queue<Transaction> branches;

                if (!intercepts(trunk))
                    branches = _propagator.getNewPendingSnapshotsBranches();
                else
                    branches = _interceptor.getNewPendingSnapshotsBranches();

                if (Debug.ENABLED)
                    for (int i = 0; i < branches.size(); i++)
                        Debug.assertion(branches.get(i) != trunk);

                branches.add(trunk);
            }

            if (Debug.ENABLED)
                for (int i = 0; i < queue.size(); i++)
                    Debug.assertion(queue.get(i) != shared);

            queue.add(shared);
        }

        final Queue<TObject.Version> getPendingSnapshots(Transaction branch) {
            if (Debug.THREADS)
                assertWriteThread();

            return TObjectMapEntry.get(_pendingSnapshots, branch);
        }

        final boolean intercepts(Transaction branch) {
            Site.Version origin = (Site.Version) branch.getOrigin().getSharedVersion_objectfabric();
            return origin.isShortestDistance(getConnection());
        }

        //

        final void onBranchIntercepted(Transaction branch) {
            _controllerOutWriter.onBranchIntercepted();
            _controllerInWriter.onBranchIntercepted();
            _callOutWriter.onBranchIntercepted();
            _callInWriter.onBranchIntercepted();
            _interceptor.onBranchIntercepted();
            _propagator.onBranchIntercepted();
        }

        final void onBranchPropagated(Transaction branch) {
            _controllerOutWriter.onBranchPropagated();
            _controllerInWriter.onBranchPropagated();
            _callOutWriter.onBranchPropagated();
            _callInWriter.onBranchPropagated();
            _interceptor.onBranchPropagated();
            _propagator.onBranchPropagated();
        }

        final boolean hasPendingSnapshots() {
            return _interceptor.getNewPendingSnapshotsBranches().size() > 0 || _propagator.getNewPendingSnapshotsBranches().size() > 0;
        }

        final void onBranchUpToDate(Transaction branch) {
            _controllerOutWriter.onBranchUpToDate(branch);
            _controllerInWriter.onBranchUpToDate(branch);
            _callOutWriter.onBranchUpToDate(branch);
            _callInWriter.onBranchUpToDate(branch);
            _interceptor.onBranchUpToDate(branch);
            _propagator.onBranchUpToDate(branch);
        }

        // Received objects

        protected final void registerNewObjects() {
            for (int i = 0; i < getNewTObjects().size(); i++) {
                final UserTObject object = getNewTObjects().get(i);

                if (object instanceof Site) {
                    Site site = (Site) object;

                    if (Debug.COMMUNICATIONS_LOG)
                        Log.write("Set executor: " + site);

                    site.setMethodExecutor(getMethodExecutor());
                }

                final TObject.Version shared = object.getSharedVersion_objectfabric();
                final Transaction trunk = object.getTrunk();
                final boolean intercepting = intercepts(trunk);

                if (intercepting)
                    Interceptor.intercept(trunk);
                else {
                    addGCPreventer(object);

                    if (trunk.getConflictDetection() != ConflictDetection.LAST_WRITE_WINS)
                        if (!shared.isImmutable() && !(object instanceof Method))
                            _propagator.getReader().ensureSourcesSplitted(trunk);
                }

                enqueueOnWriterThread(new Runnable() {

                    public void run() {
                        /*
                         * Optim: try to prevent snapshots as read objects are already
                         * known remotely. Except for sites, so that their _distance field
                         * is calculated remotely.
                         */
                        if (!(shared instanceof Site.Version))
                            if (getStatus(shared) == Status.UNKNOWN)
                                setStatus(shared, Status.SNAPSHOTTED);

                        if (intercepting)
                            _interceptor.ensureIntercepted(trunk);
                        else if (!shared.isImmutable() && !(object instanceof Method))
                            _propagator.ensurePropagated(trunk);
                    }
                });
            }

            getNewTObjects().clear();
        }

        // DGC

        final void onGarbageCollected(TObject.Version shared) {
            _dgcOutWriter.onGarbageCollected(shared);
        }

        //

        final boolean hasPendingDisconnectionsForBranch(Transaction branch) {
            if (Debug.THREADS)
                assertWriteThread();

            return TObjectMapEntry.contains(_pendingDisconnections, branch);
        }

        final void addPendingDisconnection(TObject.Version shared) {
            if (Debug.THREADS) {
                assertWriteThread();
                Debug.assertion(getStatus(shared) == Status.CREATED);
            }

            Transaction trunk = shared.getTrunk();
            Queue<TObject.Version> queue = TObjectMapEntry.get(_pendingDisconnections, trunk);

            if (queue == null) {
                queue = new Queue<TObject.Version>();
                TObjectMapEntry<Queue<TObject.Version>> entry = new TObjectMapEntry<Queue<TObject.Version>>(trunk, queue);
                TObjectMapEntry.put(_pendingDisconnections, entry);
            }

            if (Debug.ENABLED)
                for (int i = 0; i < queue.size(); i++)
                    Debug.assertion(queue.get(i) != shared);

            queue.add(shared);
        }

        final Queue<TObject.Version> getPendingDisconnections(Transaction branch) {
            if (Debug.THREADS)
                assertWriteThread();

            return TObjectMapEntry.get(_pendingDisconnections, branch);
        }

        //

        final void addGCPreventer(UserTObject object) {
            while (UserTObjectSet.tryToAdd(_gcPreventers, object) == SparseArrayHelper.REHASH) {
                UserTObject[] previous = _gcPreventers;

                for (;;) {
                    _gcPreventers = new UserTObject[_gcPreventers.length << SparseArrayHelper.TIMES_TWO_SHIFT];

                    if (UserTObjectSet.rehash(previous, _gcPreventers))
                        break;
                }
            }
        }

        final void removeGCPreventer(UserTObject object) {
            if (Debug.THREADS)
                assertWriteThread();

            UserTObjectSet.remove(_gcPreventers, object);
        }

        // Debug

        final void assertNoPendingSnapshots() {
            for (TObjectMapEntry<Queue<TObject.Version>> entry : _pendingSnapshots)
                if (entry != null)
                    Debug.assertion(entry.getValue().size() == 0);
        }
    }

    protected class ClientState extends AtomicReference<ClientFuture> {

        public ClientState() {
        }

        public void throwIfAlreadyStarted() {
            if (get() != null)
                throw new RuntimeException(Strings.ALREADY_STARTED);
        }

        public ClientFuture startConnection(AsyncCallback<Void> callback, AsyncOptions options) {
            ClientFuture future = new ClientFuture(callback, options);

            if (!compareAndSet(null, future))
                throw new RuntimeException(Strings.ALREADY_STARTED);

            return future;
        }

        public void onDialogEstablished() {
            get().set(null);
        }
    }

    protected static final class ClientFuture extends FutureWithCallback<Void> {

        private volatile Future<Void> _transportFuture;

        public ClientFuture(AsyncCallback<Void> callback, AsyncOptions options) {
            super(callback, options);
        }

        public Future<Void> getTransportFuture() {
            return _transportFuture;
        }

        public void setTransportFuture(Future<Void> value) {
            _transportFuture = value;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            Future<Void> transport = _transportFuture;

            if (transport != null)
                transport.cancel(mayInterruptIfRunning);

            return super.cancel(mayInterruptIfRunning);
        }
    }
}
