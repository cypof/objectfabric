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

import java.util.HashSet;

import org.apache.shiro.authc.AccountException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;
import org.apache.shiro.util.SimpleByteSource;

import com.objectfabric.TSet;
import com.objectfabric.Transaction;

/**
 * This realm reads data from an AccountService. This class is adapted from
 * org.apache.shiro.realm.jdbc.JdbcRealm included with the Shiro 1.1 release.
 */
final class OFRealm extends AuthorizingRealm {

    private final ShiroStore _store;

    public OFRealm(ShiroStore store) {
        _store = store;
    }

    public ShiroStore getStore() {
        return _store;
    }
    
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        UsernamePasswordToken upToken = (UsernamePasswordToken) token;
        String username = upToken.getUsername();

        if (username == null)
            throw new AccountException("Null usernames are not allowed by this realm.");

        Account account = _store.getAccounts().get(username);

        if (account == null)
            throw new UnknownAccountException("No account found for user [" + username + "]");

        byte[] hash = account.getPasswordHash();
        ByteSource salt = new SimpleByteSource(account.getSalt());
        return new SimpleAuthenticationInfo(username, hash, salt, getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        if (principals == null)
            throw new AuthorizationException("PrincipalCollection method argument cannot be null.");

        String username = (String) getAvailablePrincipal(principals);
        Account account = _store.getAccounts().get(username);
        TSet<Role> roles = account.getRoles();
        HashSet<String> permissions = new HashSet<String>();
        Transaction iteratorSnapshot = Transaction.start();

        try {
            for (Role role : roles)
                permissions.addAll(role.getPermissions());
        } finally {
            iteratorSnapshot.abort();
        }

        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        info.setStringPermissions(permissions);
        return info;
    }
}
