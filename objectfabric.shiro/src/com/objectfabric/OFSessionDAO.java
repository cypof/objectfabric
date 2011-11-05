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

import java.io.Serializable;
import java.util.Collection;

import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.eis.AbstractSessionDAO;

import com.objectfabric.ShiroStore;
import com.objectfabric.misc.Log;

final class OFSessionDAO extends AbstractSessionDAO {

    private final ShiroStore _store;

    public OFSessionDAO(ShiroStore store) {
        _store = store;
    }

    @Override
    protected Serializable doCreate(Session session) {
        Serializable id = generateSessionId(session);
        Log.write("create " +id.toString());
        assignSessionId(session, id);
        _store.getSessions().put((String) id, session);
        return id;
    }

    @Override
    protected Session doReadSession(Serializable id) {
        Log.write("read " + id.toString());
        Session session = (Session) _store.getSessions().get((String) id);
        Log.write("session " + session);
        return session;
    }

    public void update(Session session) {
        _store.getSessions().put((String) session.getId(), session);
    }

    public void delete(Session session) {
        Serializable id = session.getId();
Log.write("delete " +id.toString());

        if (id != null)
            _store.getSessions().remove((String) id);
    }

    public Collection<Session> getActiveSessions() {
        return null;
    }
}
