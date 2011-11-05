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

import of4gwt.Interception.SingleMapInterception;
import of4gwt.Snapshot.SlowChanging;
import of4gwt.Transaction.CommitStatus;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.OverrideAssert;
import of4gwt.misc.ThreadAssert.SingleThreaded;

/**
 * When a public transaction has an interception, commits cannot succeed until they are
 * acknowledged. This class iterates over maps like a walker, but uses the interception
 * mechanism instead of a walker's two transactions.
 */
@SingleThreaded
public abstract class Acknowledger extends Extension<Acknowledger.LastAcknowledged> {

    protected static final class LastAcknowledged {

        // TODO, set + list for fast contains + clean?
        public final List<Interception> Processed = new List<Interception>();

        // TODO: keep only last map?
        public Snapshot Snapshot;
    }

    private Interception _interception;

    protected Acknowledger() {
    }

    public final Interception getInterception() {
        return _interception;
    }

    final Snapshot getSnapshot(Transaction branch) {
        LastAcknowledged last = get(branch);
        return last != null ? last.Snapshot : null;
    }

    //

    @Override
    boolean casSnapshotWithThis(Transaction branch, Snapshot snapshot, Snapshot newSnapshot) {
        if (super.casSnapshotWithThis(branch, snapshot, newSnapshot)) {
            LastAcknowledged last = new LastAcknowledged();
            last.Snapshot = new Snapshot();
            snapshot.trimWithoutReads(last.Snapshot, snapshot.getInterception(), snapshot.getSlowChanging(), snapshot.getAcknowledgedIndex() + 1);
            put(branch, last);
            return true;
        }

        return false;
    }

    @Override
    boolean casSnapshotWithoutThis(Transaction branch, Snapshot snapshot, Snapshot newSnapshot, Exception exception) {
        boolean blocked = false;

        if (newSnapshot.getSlowChanging() == null) {
            newSnapshot.setSlowChanging(new SlowChanging(branch, (Extension[]) null, SlowChanging.BLOCKED_AS_BRANCH_DISCONNECTED));
            blocked = true;
        } else if (newSnapshot.getSlowChanging().getAcknowledgers() == null) {
            if (Debug.ENABLED)
                Debug.assertion(newSnapshot.getInterception() != null);

            newSnapshot.setSlowChanging(new SlowChanging(newSnapshot.getSlowChanging(), SlowChanging.BLOCKED_AS_BRANCH_DISCONNECTED));
            blocked = true;
        }

        if (super.casSnapshotWithoutThis(branch, snapshot, newSnapshot, exception)) {
            if (blocked)
                Interceptor.nack(branch, CommitStatus.ABORT, exception);

            return true;
        }

        return false;
    }

    //

    protected final void run(Visitor visitor) {
        Transaction branch = null;
        int index = 0;

        if (visitor.interrupted()) {
            branch = (Transaction) visitor.resume();
            index = visitor.resumeInt();
        }

        for (; index < getBranchCount(); index++) {
            if (branch == null) {
                branch = getNextBranch();

                if (branch == null)
                    return;
            }

            LastAcknowledged last = get(branch);
            run(branch, last, visitor);

            if (visitor.interrupted()) {
                visitor.interruptInt(index);
                visitor.interrupt(branch);
                return;
            }

            branch = null;
        }
    }

    @Override
    protected boolean isUpToDate(Transaction branch, LastAcknowledged value) {
        Snapshot snapshot = branch.getSharedSnapshot();
        return value.Snapshot == snapshot;
    }

    private final void run(Transaction branch, LastAcknowledged last, Visitor visitor) {
        if (!visitor.interrupted()) {
            for (;;) {
                Snapshot snapshot = branch.getSharedSnapshot();

                if (Debug.ENABLED)
                    Debug.assertion(snapshot != visitor.getSnapshot());

                /*
                 * Find the first new pending interception if any, or exit.
                 */
                int index = snapshot.getAcknowledgedIndex() + 1;

                // Remove irrelevant processed interruptions

                for (int i = last.Processed.size() - 1; i >= 0; i--) {
                    Interception processed = last.Processed.get(i);

                    for (int j = index; j <= snapshot.getAcknowledgedIndex(); j++) {
                        if (snapshot.getVersionMaps()[j].getInterception() == processed) {
                            last.Processed.remove(i);
                            break;
                        }
                    }
                }

                for (;;) {
                    if (index >= snapshot.getVersionMaps().length) {
                        last.Snapshot = snapshot;

                        OverrideAssert.add(this);
                        onUpToDate(branch);
                        OverrideAssert.end(this);

                        return;
                    }

                    VersionMap map = snapshot.getVersionMaps()[index];
                    _interception = map.getInterception();

                    if (Debug.ENABLED)
                        Debug.assertion(_interception != null);

                    boolean processed = false;

                    for (int i = 0; i < last.Processed.size(); i++)
                        if (last.Processed.get(i) == _interception)
                            processed = true;

                    if (!processed)
                        break;

                    index++;
                }

                visitor.setBranch(branch);
                visitor.setMapIndex1(index);
                visitor.setMapIndex2(snapshot.getWrites().length);

                if (_interception instanceof SingleMapInterception) {
                    visitor.setSnapshot(snapshot);
                    break;
                }

                /*
                 * If interception is not the last of the snapshot, we are done,
                 * otherwise, switch to another one to protect the current maps from
                 * future commits.
                 */
                if (snapshot.getInterception() != _interception) {
                    visitor.setSnapshot(snapshot);
                    break;
                }

                if (Interceptor.tryToAddMultiMapInterception(branch, snapshot, snapshot.getSlowChanging(), visitor))
                    break;
            }
        }

        visitor.visitBranch();

        if (visitor.interrupted())
            return;

        if (Debug.ENABLED)
            for (int i = 0; i < last.Processed.size(); i++)
                Debug.assertion(last.Processed.get(i) != _interception);

        last.Snapshot = visitor.getSnapshot();
        last.Processed.add(_interception);

        OverrideAssert.add(this);
        onUpToDate(branch);
        OverrideAssert.end(this);

        if (Debug.ENABLED)
            Debug.assertion(!visitor.interrupted());
    }

    @Override
    protected Action onVisitingMap(Visitor visitor, int mapIndex) {
        VersionMap map = visitor.getSnapshot().getVersionMaps()[mapIndex];
        Action action = super.onVisitingMap(visitor, mapIndex);

        if (map.getInterception() != _interception) {
            visitor.flush();
            return Action.TERMINATE;
        }

        return action;
    }

    @Override
    protected void onVisitedBranch(Visitor visitor) {
        visitor.flush();

        super.onVisitedBranch(visitor);
    }
}
