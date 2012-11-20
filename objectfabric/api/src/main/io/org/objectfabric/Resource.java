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

import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A resource can be anything from a text string to a complex object graph. Objects from a
 * resource can only reference objects belonging to the same resource.
 */
public class Resource extends TObject {

    public static final TType TYPE;

    static {
        TYPE = Platform.newTType(Platform.get().defaultObjectModel(), BuiltInClass.RESOURCE_CLASS_ID);
    }

    static final Object NULL = new Object() {

        @Override
        public String toString() {
            return "Resource.NULL";
        }
    };

    private final Workspace _workspace;

    private final URI _uri;

    private final AtomicReference<Block> _loadQueue = new AtomicReference<Block>();

    //

    private volatile Permission _permission;

    // TODO use long[] + block array?
    private PlatformMap<Long, Block> _pending;

    // TODO bench merging this and _pending in concurrent map & load on IO threads
    private long[] _loaded = new long[OpenMap.CAPACITY];

    //

    private final List<Block> _ordered = new List<Block>();

    private volatile List<long[]> _goals = new List<long[]>();

    //

    private final PlatformMap<Long, NewBlock> _pendingAcks = new PlatformMap<Long, NewBlock>();

    Resource(Workspace workspace, URI uri) {
        super(null, new ResourceVersion());

        if (Debug.ENABLED)
            if (uri == null)
                Debug.assertion(workspace.emptyResource() == null);

        _workspace = workspace;
        _uri = uri;

        if (uri != null)
            setReferencedByURI();
    }

    // For .NET
    void onReferenced(PlatformRef<Resource> ref) {
    }

    public final URI uri() {
        return _uri;
    }

    /**
     * Location this resource was loaded from.
     */
    public final Origin origin() {
        return _uri.origin();
    }

    public final Permission permission() {
        return _permission;
    }

    //

    final Workspace workspaceImpl() {
        return _workspace;
    }

    final Watcher watcher() {
        return _workspace.watcher();
    }

    final Transaction transaction() {
        return _workspace.transactionThreadLocal().get();
    }

    final PlatformMap<Long, NewBlock> pendingAcks() {
        return _pendingAcks;
    }

    final PlatformMap<Long, Block> pending() {
        return _pending;
    }

    final List<Block> ordered() {
        return _ordered;
    }

    final long[] loaded() {
        return _loaded;
    }

    //

    public final boolean isLoaded() {
        if (_uri == null)
            wrongResource_();

        return _goals == null;
    }

    /**
     * Gets resource synchronously. The calling thread blocks until the first location
     * responds (origin or cache). Returned value might not be the most up to date
     * version, and change when following responses are received from other locations.
     */
    public Object get() {
        if (_uri == null)
            wrongResource_();

        for (;;) {
            Object value = getFromMemory();

            if (value != null)
                return value != NULL ? value : null;

            FutureWithCallbacks<Object> future = _uri.open(this);

            if (future != null) {
                try {
                    return future.get();
                } catch (Exception ex) {
                    ExpectedExceptionThrower.throwRuntimeException(ex);
                }
            }
        }
    }

    public Future<Object> getAsync(AsyncCallback<Object> callback) {
        if (_uri == null)
            wrongResource_();

        Executor executor = workspaceImpl().callbackExecutor();

        for (;;) {
            Object value = getFromMemory();

            if (value != null) {
                value = value != NULL ? value : null;

                if (callback == null)
                    return new CompletedFuture<Object>(value);

                FutureWithCallback<Object> future = new FutureWithCallback<Object>(callback, executor);
                future.set(value);
                return future;
            }

            FutureWithCallbacks<Object> future = _uri.open(this);

            if (future != null) {
                if (callback != null)
                    future.addCallback(callback, executor);

                return future;
            }
        }
    }

    final Object getFromMemory() {
        Transaction outer = current_();
        Transaction inner = startRead_(outer);
        Object value = getFromMemory(inner, (ResourceVersion) inner.getVersion(this), true);
        endRead_(outer, inner);
        return value;
    }

    /**
     * Updates resource lazily. Calling thread does not block.
     */
    public void set(Object value) {
        if (_uri == null)
            wrongResource_();

        if (value == null)
            value = NULL;

        Transaction outer = current_();
        Transaction inner = startWrite_(outer);
        ResourceVersion version = (ResourceVersion) getOrCreateVersion_(inner);
        boolean open = getFromMemory(inner, version, false) == null;
        version._value = value;
        endWrite_(outer, inner);

        if (open)
            _uri.open(this);
    }

    /**
     * Deletes resource lazily. Calling thread does not block.
     */
    public void delete() {
        set(NULL);
    }

    private final Object getFromMemory(Transaction transaction, ResourceVersion version, boolean markRead) {
        if (version != null && version._value != null)
            return version._value;

        Version[][] versions = transaction.getPrivateSnapshotVersions();

        if (versions != null) {
            for (int i = versions.length - 1; i >= 0; i--) {
                ResourceVersion v = (ResourceVersion) TransactionBase.getVersion(versions[i], this);

                if (v != null && v._value != null)
                    return v._value;
            }
        }

        if (markRead && !transaction.ignoreReads()) {
            Version read = transaction.getRead(this);

            if (read == null) {
                read = createRead();
                transaction.putRead(read);
            }

            if (Debug.ENABLED)
                Debug.assertion(read instanceof ResourceRead);
        }

        versions = transaction.getPublicSnapshotVersions();
        return getFromPublicVersions(versions);
    }

    private final Object getFromPublicVersions(Version[][] versions) {
        for (int i = versions.length - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
            ResourceVersion version = (ResourceVersion) TransactionBase.getVersion(versions[i], this);

            if (version != null && version._value != null)
                return version._value;
        }

        ResourceVersion shared = (ResourceVersion) shared_();
        return shared._value;
    }

    //

    /**
     * Blocks until resource has been synchronized from workspace to given location.
     */
    void push(Location location) {
        @SuppressWarnings("unchecked")
        Future<Void> future = pushAsync(location, FutureWithCallback.NOP_CALLBACK);

        try {
            future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // TODO
    Future<Void> pushAsync(Location location, AsyncCallback<Void> callback) {
        if (workspaceImpl().resolver().isClosing())
            throw new ClosedException();

        return null;
    }

    /**
     * Blocks until resource has been synchronized from given location to workspace.
     */
    void pull(Location location) {
        @SuppressWarnings("unchecked")
        Future<Void> future = pullAsync(location, FutureWithCallback.NOP_CALLBACK);

        try {
            future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // TODO
    Future<Void> pullAsync(Location location, AsyncCallback<Void> callback) {
        if (workspaceImpl().resolver().isClosing())
            throw new ClosedException();

        return null;
    }

    /*
     * IO threads.
     */

    final void onPermission(Permission permission) {
        if (Debug.ENABLED)
            Debug.assertion(permission != null);

        _permission = permission;

        if (permission == Permission.REJECT)
            _uri.onCancel(this, new RuntimeException(Strings.PERMISSION_REJECT));

        // Forget acks if permission is not WRITE
        if (permission != Permission.WRITE) {
            watcher().actor().addAndRun(new Actor.Message() {

                @Override
                void run(Actor actor) {
                    List<Long> toRemove = null;

                    for (Entry<Long, NewBlock> entry : _pendingAcks.entrySet()) {
                        if (entry.getValue().PendingAcksBitSet == URIResolver.ORIGIN_BIT) {
                            if (toRemove == null)
                                toRemove = new List<Long>();

                            toRemove.add(entry.getKey());
                        }
                    }

                    if (toRemove != null)
                        for (int i = 0; i < toRemove.size(); i++)
                            removePendingAck(toRemove.get(i));
                }
            });
        }
    }

    final void onUnresolved() {
        _uri.onCancel(this, new RemoteException(Strings.URI_UNRESOLVED));
    }

    final void getKnown() {
        watcher().actor().addAndRun(new Actor.Message() {

            @Override
            void run(Actor actor) {
                if (_pendingAcks.size() > 0)
                    tellKnown();
            }
        });
    }

    final void tellKnown() {
        long[] ticks = null;

        for (int i = 0; i < _ordered.size(); i++)
            ticks = Tick.add(ticks, _ordered.get(i).Tick);

        Object key;

        if (Debug.THREADS)
            ThreadAssert.suspend(key = new Object());

        _uri.onKnown(Resource.this, ticks);

        if (Debug.THREADS)
            ThreadAssert.resume(key);
    }

    final void onKnown(final long[] ticks) {
        watcher().actor().addAndRun(new Actor.Message() {

            @Override
            void run(Actor actor) {
                if (ticks.length == 0)
                    onUpToDate();
                else {
                    boolean pending = false;

                    for (int i = 0; i < ticks.length; i++) {
                        if (!Tick.isNull(ticks[i])) {
                            boolean skip = Tick.happenedBefore(ticks[i], _loaded);

                            if (!skip)
                                if (_pending != null && _pending.containsKey(ticks[i]))
                                    skip = true;

                            if (!skip) {
                                pending = true;
                                Object key;

                                if (Debug.THREADS)
                                    ThreadAssert.suspend(key = new Object());

                                _uri.getBlock(Resource.this, ticks[i]);

                                if (Debug.THREADS)
                                    ThreadAssert.resume(key);
                            }
                        }
                    }

                    if (pending) {
                        List<long[]> goals = _goals;

                        if (goals != null)
                            goals.add(ticks);
                    }
                }
            }
        });
    }

    final void getBlock(final long tick) {
        if (Debug.ENABLED)
            Debug.assertion(!Tick.isNull(tick));

        watcher().actor().addAndRun(new Actor.Message() {

            @Override
            void run(Actor actor) {
                NewBlock block = _pendingAcks.get(tick);

                if (block != null) {
                    Buff[] duplicates = new Buff[block.Buffs.length];

                    for (int i = 0; i < duplicates.length; i++) {
                        duplicates[i] = block.Buffs[i].duplicate();

                        if (Debug.THREADS)
                            ThreadAssert.exchangeGive(duplicates, duplicates[i]);
                    }

                    Object key;

                    if (Debug.THREADS)
                        ThreadAssert.suspend(key = new Object());

                    _uri.onBlock(Resource.this, tick, duplicates, null, true, null, true);

                    if (Debug.THREADS) {
                        ThreadAssert.resume(key);
                        ThreadAssert.exchangeTake(duplicates);
                    }

                    for (int i = 0; i < duplicates.length; i++)
                        duplicates[i].recycle();
                }
            }
        });
    }

    final void onBlock(final Block block) {
        if (!watcher().actor().isClosed()) {
            for (;;) {
                Block head = _loadQueue.get();
                block.Next = head;

                if (_loadQueue.compareAndSet(head, block)) {
                    if (head == null) {
                        watcher().actor().addAndRun(new Actor.Message() {

                            @Override
                            void run(Actor actor) {
                                onEnqueued();
                            }
                        });
                    }

                    break;
                }
            }
        }
    }

    final void onAck(final View view, final long tick) {
        if (Debug.ENABLED)
            Debug.assertion(!Tick.isNull(tick));

        watcher().actor().addAndRun(new Actor.Message() {

            @Override
            void run(Actor actor) {
                NewBlock block = _pendingAcks.get(tick);

                if (block != null) {
                    Location[] caches = _workspace.caches();

                    // Wait for ack of caches if present
                    if (caches != null) {
                        for (int i = 0; i < caches.length; i++) {
                            if (view.location() == caches[i]) {
                                block.PendingAcksBitSet = Bits.unset(block.PendingAcksBitSet, i);
                                break;
                            }
                        }

                        int mask = -1 >>> (32 - caches.length);

                        if ((block.PendingAcksBitSet & mask) == 0)
                            removePendingAck(tick);
                    } else {
                        // Otherwise wait for origin ack
                        if (view.location() == uri().origin()) {
                            block.PendingAcksBitSet &= ~URIResolver.ORIGIN_BIT;

                            if (block.PendingAcksBitSet == 0)
                                removePendingAck(tick);
                        }
                    }
                }
            }
        });
    }

    final void onFailed(final Object source, long tick) {
        watcher().actor().addAndRun(new Actor.Message() {

            @Override
            void run(Actor actor) {
                // If data loss, show currently loaded instead of just blocking
                onUpToDate();
            }
        });
    }

    /*
     * Loading.
     */

    final void onEnqueued() {
        Block queue;

        for (;;) {
            queue = _loadQueue.get();

            if (_loadQueue.compareAndSet(queue, null))
                break;
        }

        if (Debug.ENABLED)
            Debug.assertion(queue != null);

        while (queue != null) {
            if (!Tick.happenedBefore(queue.Tick, _loaded)) {
                if (_pending == null)
                    _pending = new PlatformMap<Long, Block>();

                _pending.put(queue.Tick, queue);
            }

            queue = queue.Next;
        }

        if (_pending != null) {
            long[] missing = null;
            int delta = 0;

            for (Block block : _pending.values()) {
                if (block.Dependencies != null) {
                    for (int i = 0; i < block.Dependencies.length; i++) {
                        if (!Tick.isNull(block.Dependencies[i])) {
                            if (!Tick.happenedBefore(block.Dependencies[i], _loaded)) {
                                if (!_pending.containsKey(block.Dependencies[i])) {
                                    if (!Tick.contains(missing, block.Dependencies[i])) {
                                        missing = Tick.add(missing, block.Dependencies[i]);
                                        delta++;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            for (int i = 0; missing != null && i < missing.length; i++) {
                if (!Tick.isNull(missing[i])) {
                    for (Block block : _pending.values()) {
                        if (replaces(block, missing[i])) {
                            missing[i] = Tick.REMOVED;
                            delta--;
                            break;
                        }
                    }
                }
            }

            List<Block> list = new List<Block>();

            if (delta == 0) {
                for (Block block : _pending.values())
                    list.add(block);

                _pending = null;
            } else {
                for (Block block : _pending.values())
                    if (!hasMissingDependencies(block, missing))
                        list.add(block);

                for (int i = 0; i < list.size(); i++)
                    _pending.remove(list.get(i).Tick);

                if (_pending.size() == 0)
                    _pending = null;
            }

            if (list.size() > 0) {
                for (int i = 0; i < list.size(); i++)
                    load(list.get(i));

                order(list);
                List<long[]> goals = _goals;

                if (goals != null) {
                    for (int i = 0; i < goals.size(); i++) {
                        if (loadedOrPending(goals.get(i))) {
                            onUpToDate();
                            break;
                        }
                    }
                }
            }
        }
    }

    private static boolean replaces(Block block, long dependency) {
        if (!Tick.contains(block.Dependencies, dependency)) {
            if (Tick.peer(block.Tick) == Tick.peer(dependency))
                if (Tick.time(block.Tick) >= Tick.time(dependency))
                    return true;

            if (block.HappenedBefore != null)
                if (Tick.happenedBefore(dependency, block.HappenedBefore))
                    return true;
        }

        return false;
    }

    private static boolean hasMissingDependencies(Block block, long[] missing) {
        if (block.Dependencies != null)
            for (int i = 0; i < block.Dependencies.length; i++)
                if (!Tick.isNull(block.Dependencies[i]))
                    if (Tick.contains(missing, block.Dependencies[i]))
                        return true;

        return false;
    }

    /*
     * Don't block on goal with pending loads, cache might have received partial data that
     * will be ignored for now but be in good state anyway.
     */
    private final boolean loadedOrPending(long[] ticks) {
        if (ticks != null)
            for (int i = 0; i < ticks.length; i++)
                if (!Tick.isNull(ticks[i]))
                    if (!Tick.happenedBefore(ticks[i], _loaded))
                        if (_pending == null || !_pending.containsKey(ticks[i]))
                            return false;

        return true;
    }

    private final void load(Block block) {
        _loaded = Tick.putMax(_loaded, block.Tick, true);

        if (block.HappenedBefore != null)
            for (int i = 0; i < block.HappenedBefore.length; i++)
                if (!Tick.isNull(block.HappenedBefore[i]))
                    _loaded = Tick.putMax(_loaded, block.HappenedBefore[i], true);

        block.Dependencies = null;
        block.Next = null;
    }

    /*
     * Ordering.
     */

    private final void order(List<Block> list) {
        Version[] versions = null;

        for (int i = 0; i < list.size(); i++)
            versions = order(list.get(i), versions);

        if (versions != null) {
            if (isLoaded())
                commit(versions, list);
            else {
                for (int i = versions.length - 1; i >= 0; i--) {
                    if (versions[i] != null) {
                        if (versions[i].object() != this || !watcher().hasPendingAcks(this)) {
                            Version shared = versions[i].object().shared_();
                            shared.merge(shared, versions[i], true);
                        } else {
                            int todo;
                            System.out.println("skipping root");
                        }
                    }
                }
            }
        }
    }

    private final Version[] order(Block block, Version[] versions) {
        int index = binarySearch(_ordered, block.Tick, block.HappenedBefore);
        boolean empty = false;

        if (index < _ordered.size()) {
            empty = true;

            for (int i = index; i < _ordered.size(); i++)
                empty &= mask(_ordered.get(i).Versions, block.Versions);
        }

        if (!empty) {
            if (Debug.ENABLED)
                Debug.assertion(block.Dependencies == null && block.Next == null);

            _ordered.add(index, block);
            maskPrevious(block.Versions, index);

            if (versions == null)
                versions = new Version[OpenMap.CAPACITY];

            for (int i = block.Versions.length - 1; i >= 0; i--) {
                if (block.Versions[i] != null) {
                    Version version = TransactionBase.getVersion(versions, block.Versions[i].object());

                    if (version == null) {
                        version = block.Versions[i].object().createVersion_();
                        versions = TransactionBase.putVersion(versions, version);
                    }

                    // Makes sure no races between masks & merges
                    version.deepCopy(block.Versions[i]);
                }
            }
        }

        return versions;
    }

    static int binarySearch(List<Block> list, long tick, long[] happenedBefores) {
        int low = 0;
        int high = list.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            Block midVal = list.get(mid);
            int comp = compare(midVal.Tick, midVal.HappenedBefore, tick, happenedBefores);

            if (comp < 0)
                low = mid + 1;
            else
                high = mid - 1;
        }

        return low;
    }

    private static int compare(long a, long[] aHB, long b, long[] bHB) {
        // TODO pre-compute in Peer somehow?
        byte[] uidA = Peer.get(Tick.peer(a)).uid();
        byte[] uidB = Peer.get(Tick.peer(b)).uid();
        int compare = UID.compare(uidA, uidB);

        if (compare == 0) {
            if (Debug.ENABLED) {
                Debug.assertion(Tick.peer(a) == Tick.peer(b));
                Debug.assertion(Tick.time(a) != Tick.time(b));
            }

            return Tick.time(a) < Tick.time(b) ? -1 : 1;
        }

        if (compare > 0) {
            if (bHB != null && Tick.happenedBefore(a, bHB))
                return -compare;

            return compare;
        } else {
            if (aHB != null && Tick.happenedBefore(b, aHB))
                return -compare;

            return compare;
        }
    }

    static final class Block {

        final long Tick;

        final Version[] Versions;

        final long[] HappenedBefore;

        long[] Dependencies;

        Block Next;

        Block(long tick, Version[] versions, long[] happenedBefore, long[] dependencies) {
            Tick = tick;
            Versions = versions;
            HappenedBefore = happenedBefore;
            Dependencies = dependencies;
        }
    }

    private final void commit(Version[] versions, List<Block> list) {
        Snapshot previous = watcher().snapshot();
        VersionMap watched = null;

        for (;;) {
            Snapshot snapshot = workspaceImpl().snapshot();

            if (snapshot.last() == VersionMap.CLOSING)
                break;

            if (snapshot.last() != previous.last()) {
                boolean empty = true;
                VersionMap map;

                for (;;) {
                    map = snapshot.last();

                    if (map.tryToAddWatchers(1)) {
                        if (Debug.ENABLED)
                            Helper.instance().addWatcher(map, this, snapshot, "mask");

                        break;
                    }
                }

                int index = Helper.getIndex(snapshot, previous.last());

                for (int i = index + 1; i < snapshot.writes().length; i++)
                    empty &= mask(snapshot.writes()[i], versions);

                if (watched != null) {
                    if (Debug.ENABLED)
                        Helper.instance().removeWatcher(watched, this, snapshot, "mask retry");

                    watched.removeWatchers(workspaceImpl(), 1, false, snapshot);
                }

                watched = map;
                previous = snapshot;

                if (empty)
                    break;
            }

            if (TransactionManager.load(workspaceImpl(), versions, snapshot.last(), this, list))
                break;
        }

        if (watched != null) {
            if (Debug.ENABLED)
                Helper.instance().removeWatcher(watched, this, previous, "mask done");

            watched.removeWatchers(workspaceImpl(), 1, false, previous);
        }
    }

    void onLoad(Snapshot snapshot, List<Block> acks) {
    }

    private static boolean mask(Version[] masks, Version[] versions) {
        boolean empty = true;

        for (int i = versions.length - 1; i >= 0; i--) {
            if (versions[i] != null) {
                Version mask = TransactionBase.getVersion(masks, versions[i].object());

                if (mask != null)
                    empty &= mask.mask(versions[i]);
                else
                    empty = false;
            }
        }

        return empty;
    }

    private final long[] maskPrevious(Version[] masks, int index) {
        long[] removals = null;

        for (int i = index - 1; i >= 0; i--) {
            boolean empty = mask(masks, _ordered.get(i).Versions);

            if (empty)
                removals = Tick.add(removals, _ordered.remove(i).Tick);
        }

        return removals;
    }

    private final void onUpToDate() {
        FutureWithCallbacks<Object> future = _uri.markLoaded(this);

        if (future != null) {
            _goals = null;

            if (Debug.ENABLED)
                Debug.assertion(current_() == null);

            Snapshot snapshot;

            for (;;) {
                snapshot = workspaceImpl().snapshotWithoutClosing();

                if (snapshot.last().tryToAddWatchers(1))
                    break;
            }

            if (Debug.ENABLED)
                Helper.instance().addWatcher(snapshot.last(), this, snapshot, "Resource::onUpToDate()");

            Object value = getFromPublicVersions(snapshot.writes());
            value = value != NULL ? value : null;

            if (Debug.ENABLED)
                Helper.instance().removeWatcher(snapshot.last(), this, snapshot, "Resource::onUpToDate()");

            snapshot.last().removeWatchers(workspaceImpl(), 1, false, null);
            future.set(value);

            if (watcher().removeHasPendingAcks(this, false)) {
                if (Debug.ENABLED)
                    Debug.assertion(_pendingAcks.size() == 0);

                watcher().writeChangesUntilUpToDate(this, value);
            }
        }
    }

    //

    final void writeNewBlock(long tick, Version[] versions) {
        if (Debug.ENABLED) {
            Debug.assertion(isLoaded());
            boolean ok = false;

            for (int i = 0; i < versions.length; i++)
                if (versions[i] != null)
                    ok = true;

            Debug.assertion(ok);
        }

        long[] removals = maskPrevious(versions, _ordered.size());

        for (int i = 0; i < _ordered.size(); i++)
            watcher().writeDependency(_ordered.get(i).Tick);

        long[] happenedBefore = new long[_loaded.length];
        Platform.arraycopy(_loaded, 0, happenedBefore, 0, _loaded.length);
        watcher().writeHappenedBefore(happenedBefore);

        _ordered.add(new Block(tick, versions, happenedBefore, null));

        _loaded = Tick.putMax(_loaded, tick, true);
        Buff[] buffs = watcher().finishTick();
        NewBlock block = new NewBlock();
        Location[] caches = watcher().workspace().caches();

        // Wait for ack of caches if any
        if (caches != null)
            block.PendingAcksBitSet = -1 >>> (32 - caches.length);

        // And of origin if allowed to write
        if (_permission == null || _permission == Permission.WRITE)
            block.PendingAcksBitSet |= URIResolver.ORIGIN_BIT;

        if (block.PendingAcksBitSet != 0)
            addPendingAck(tick, block, buffs);

        if (removals != null)
            for (int i = 0; i < removals.length; i++)
                if (!Tick.isNull(removals[i]))
                    removePendingAck(removals[i]);

        Object key;

        if (Debug.THREADS) {
            for (int i = 0; i < buffs.length; i++)
                ThreadAssert.exchangeGive(buffs, buffs[i]);

            ThreadAssert.suspend(key = new Object());
        }

        Exception exception = onBlock(tick, buffs, removals);

        if (Debug.THREADS) {
            ThreadAssert.resume(key);
            ThreadAssert.exchangeTake(buffs);
        }

        if (Debug.ENABLED)
            Debug.assertion(exception == null);

        for (int i = 0; i < buffs.length; i++)
            buffs[i].recycle();

        if (Stats.ENABLED)
            Stats.Instance.BlockCreated.incrementAndGet();
    }

    Exception onBlock(long tick, Buff[] buffs, long[] removals) {
        return _uri.onBlock(this, tick, buffs, removals, false, null, true);
    }

    private final void addPendingAck(long tick, NewBlock block, Buff[] buffs) {
        Buff[] duplicates = new Buff[buffs.length];

        for (int i = 0; i < duplicates.length; i++)
            duplicates[i] = buffs[i].duplicate();

        block.Buffs = duplicates;

        //

        if (_pendingAcks.size() == 0)
            watcher().addHasPendingAcks(this);

        _pendingAcks.put(tick, block);
    }

    private final void removePendingAck(long tick) {
        NewBlock block = _pendingAcks.remove(tick);

        if (block != null) {
            for (int i = 0; i < block.Buffs.length; i++)
                block.Buffs[i].recycle();

            watcher().onBlockAck(Tick.time(tick));

            if (_pendingAcks.size() == 0)
                watcher().removeHasPendingAcks(this, true);
        }
    }

    private static final class NewBlock {

        Buff[] Buffs;

        int PendingAcksBitSet;
    }

    /*
     * Listeners.
     */

    public final void addListener(ResourceListener listener) {
        addListener(listener, workspaceImpl().callbackExecutor());
    }

    public final void addListener(ResourceListener listener, Executor executor) {
        workspaceImpl().addListener(this, listener, executor);
    }

    public final void removeListener(ResourceListener listener) {
        removeListener(listener, workspaceImpl().callbackExecutor());
    }

    public final void removeListener(ResourceListener listener, Executor executor) {
        workspaceImpl().removeListener(this, listener, executor);
    }

    //

    /**
     * Trims ' ' and '/' at beginning and end.
     */
    static String trim(String uri) {
        while (uri.length() > 0) {
            if (uri.charAt(0) == ' ')
                uri = uri.substring(1);
            else if (uri.charAt(0) == '/')
                uri = uri.substring(1);
            else if (uri.charAt(uri.length() - 1) == ' ')
                uri = uri.substring(0, uri.length() - 1);
            else if (uri.charAt(uri.length() - 1) == '/')
                uri = uri.substring(0, uri.length() - 1);
            else
                break;
        }

        return uri;
    }

    @Override
    public final int hashCode() {
        // Final as used as key (Range, URI.getBlock)

        if (Debug.ENABLED)
            Helper.instance().disableEqualsOrHashCheck();

        int value = super.hashCode();

        if (Debug.ENABLED)
            Helper.instance().enableEqualsOrHashCheck();

        return value;
    }

    @Override
    public final boolean equals(Object obj) {
        if (Debug.ENABLED)
            Helper.instance().disableEqualsOrHashCheck();

        boolean value = super.equals(obj);

        if (Debug.ENABLED)
            Helper.instance().enableEqualsOrHashCheck();

        return value;
    }

    @Override
    public String toString() {
        if (this == workspaceImpl().emptyResource())
            return "Empty URI";

        return _uri.toString();
    }

    //

    @Override
    final ResourceRead createRead() {
        ResourceRead version = new ResourceRead();
        version.setObject(this);
        return version;
    }

    @Override
    protected final ResourceVersion createVersion_() {
        ResourceVersion version = new ResourceVersion();
        version.setObject(this);
        return version;
    }

    @Override
    protected final int classId_() {
        return BuiltInClass.RESOURCE_CLASS_ID;
    }

    //

    static final class ResourceRead extends TObject.Version {

        @Override
        public boolean validAgainst(VersionMap map, Snapshot snapshot, int start, int stop) {
            for (int i = start; i < stop; i++) {
                TObject.Version write = TransactionBase.getVersion(snapshot.writes()[i], object());

                if (write != null)
                    return false;
            }

            return true;
        }

        @Override
        public void visit(org.objectfabric.Visitor visitor) {
            visitor.visit(this);
        }
    }

    static final class ResourceVersion extends TObject.Version {

        private Object _value;

        final Object getValue() {
            return _value;
        }

        final void setValue(Object value) {
            _value = value;
        }

        @Override
        public TObject.Version merge(TObject.Version target, TObject.Version next, boolean threadPrivate) {
            ResourceVersion source = (ResourceVersion) next;
            ResourceVersion merged = this;

            if (source._value != null)
                merged._value = source._value;

            return merged;
        }

        @Override
        void deepCopy(Version source_) {
            ResourceVersion source = (ResourceVersion) source_;

            if (source._value != null)
                _value = source._value;
        }

        @Override
        public void visit(org.objectfabric.Visitor visitor) {
            visitor.visit(this);
        }

        @Override
        boolean mask(Version version) {
            ResourceVersion uri = (ResourceVersion) version;
            uri._value = null;
            return true;
        }

        // Debug

        @Override
        void getContentForDebug(List<Object> list) {
            if (!Debug.ENABLED)
                throw new IllegalStateException();

            list.add(_value);
        }

        @Override
        boolean hasWritesForDebug() {
            if (!Debug.ENABLED)
                throw new IllegalStateException();

            return true;
        }
    }

    // Debug

    final void assertIdle() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Debug.assertion(range() == null);
        Debug.assertion(id() == 0);
        Debug.assertion(_pendingAcks.size() == 0);
    }
}
