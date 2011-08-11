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

package com.objectfabric;

import java.util.HashMap;
import java.util.HashSet;

import com.objectfabric.Helper;
import com.objectfabric.ImmutableReader;
import com.objectfabric.ImmutableWriter;
import com.objectfabric.Transaction;
import com.objectfabric.TObject.Version;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.IdentityEqualityWrapper;

final class DistributedHelper {

    private final HashMap<Transaction, HashSet<Version>> _snapshots;

    private final HashSet<IdentityEqualityWrapper> _disconnected;

    private final ImmutableReader _channelSwitchReader;

    private final ImmutableWriter _channelSwitchWriter;

    public DistributedHelper() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (Debug.COMMUNICATIONS) {
            _snapshots = new HashMap<Transaction, HashSet<Version>>();
            _channelSwitchReader = new ImmutableReader();
            _channelSwitchWriter = new ImmutableWriter();
        } else {
            _snapshots = null;
            _channelSwitchReader = null;
            _channelSwitchWriter = null;
        }

        if (Debug.DGC)
            _disconnected = new HashSet<IdentityEqualityWrapper>();
        else
            _disconnected = null;
    }

    public ImmutableReader getChannelSwitchReader() {
        return _channelSwitchReader;
    }

    public ImmutableWriter getChannelSwitchWriter() {
        return _channelSwitchWriter;
    }

    public void markAsSnapshoted(Transaction branch, Version shared) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        Debug.assertion(branch.isPublic());
        Helper.getInstance().disableEqualsOrHashCheck();
        HashSet<Version> set = _snapshots.get(branch);

        if (set == null)
            _snapshots.put(branch, set = new HashSet<Version>());

        Helper.getInstance().enableEqualsOrHashCheck();
        boolean added = set.add(shared);
        Debug.assertion(added);
    }

    public void markAsDisconnected(Version shared) {
        if (!Debug.DGC)
            throw new IllegalStateException();

        IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(shared);
        boolean added = _disconnected.add(wrapper);
        Debug.assertion(added);
    }

    public void assertNotDisconnected(Version shared) {
        if (!Debug.DGC)
            throw new IllegalStateException();

        IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(shared);
        Debug.assertion(!_disconnected.contains(wrapper));
    }
}
