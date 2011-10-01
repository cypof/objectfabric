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

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.Subject.Builder;

import com.objectfabric.Transaction;

public class UsernamePasswordSession extends UsernamePasswordSessionBase {

    private volatile Subject _subject;

    public UsernamePasswordSession(UsernamePasswordUserManager manager) {
        this(Transaction.getDefaultTrunk(), manager);
    }

    public UsernamePasswordSession(Transaction trunk, UsernamePasswordUserManager manager) {
        super(trunk, manager);
    }

    public boolean isLoggedIn() {
        return _subject != null;
    }

    public String getUsername() {
        Subject subject = _subject;

        if (subject == null)
            throw new SecurityException("Not logged in");

        return (String) subject.getPrincipal();
    }

    @Override
    protected String loginImplementation(String username, String password) {
        UsernamePasswordToken token = new UsernamePasswordToken(username, password);
        Subject subject = SecurityUtils.getSecurityManager().login(null, token);
        String sessionId = (String) subject.getSession().getId();
        _subject = subject;
        return sessionId;
    }

    @Override
    protected boolean bindImplementation(String sessionId) {
        Builder builder = new Builder();
        builder.sessionId(sessionId);
        Subject subject = builder.buildSubject();

        if (subject.getSession(false) != null) {
            _subject = subject;
            return true;
        }

        return false;
    }

    @Override
    protected void logoutImplementation() {
        Subject subject = _subject;

        if (subject == null)
            throw new RuntimeException("Not logged in");

        subject.logout();
        _subject = null;
    }
}
