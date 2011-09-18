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

package com.objectfabric.security.shiro;

import java.io.Serializable;
import java.util.Collection;

import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.eis.AbstractSessionDAO;

final class OFSessionDAO extends AbstractSessionDAO {

    private final ShiroStore _store;

    public OFSessionDAO(ShiroStore store) {
        _store = store;
    }

    @Override
    protected Serializable doCreate(Session session) {
        Serializable id = generateSessionId(session);
        assignSessionId(session, id);
        _store.getSessions().put((String) id, session);
        return id;
    }

    @Override
    protected Session doReadSession(Serializable id) {
        return (Session) _store.getSessions().get((String) id);
    }

    public void update(Session session) {
        _store.getSessions().put((String) session.getId(), session);
    }

    public void delete(Session session) {
        Serializable id = session.getId();

        if (id != null)
            _store.getSessions().remove((String) id);
    }

    public Collection<Session> getActiveSessions() {
        return null;
    }
}
