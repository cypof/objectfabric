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

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.crypto.hash.Sha512Hash;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.session.mgt.DefaultSessionManager;

import com.objectfabric.misc.JavaSerializer;
import com.objectfabric.misc.PlatformAdapter;

/**
 * Apache Shiro is a powerful and easy-to-use Java security framework that performs
 * authentication, authorization, cryptography, and session management. This project
 * implements basic integration with ObjectFabric for a login and password based web
 * site's accounts management with roles.
 * <nl>
 * OF wraps Shiro to expose only username and password authentication for now using
 * methods that can be invoked from other OF processes.
 * <nl>
 * This extension requires the Java serializer to serialize long running session.
 * {@code OF.setCustomSerializer(new JavaSerializer())}.
 * <nl>
 * http://shiro.apache.org/index.html
 */
public class UsernamePasswordUserManager extends UsernamePasswordUserManagerBase {

    private static final int DEFAULT_HASH_ITERATIONS = 733;

    /**
     * Sessions last one month by default.
     */
    private static final int DEFAULT_SESSION_TIMEOUT = 30 * 24 * 3600 * 1000;

    private final ShiroStore _store;

    private final OFRealm _realm;

    private final int _hashIterations;

    public UsernamePasswordUserManager(ShiroStore store) {
        this(store, DEFAULT_HASH_ITERATIONS);
    }

    public UsernamePasswordUserManager(ShiroStore store, int hashIterations) {
        this(store, hashIterations, DEFAULT_SESSION_TIMEOUT);
    }

    /**
     * The number of iterations the password should be hashed. Check
     * http://www.katasoft.com/blog/2011/04/04/strong-password-hashing-apache-shiro.
     */
    public UsernamePasswordUserManager(ShiroStore store, int hashIterations, int sessionTimeoutMillis) {
        super(Transaction.getDefaultTrunk());

        _store = store;
        _realm = new OFRealm(store);
        _hashIterations = hashIterations;

        HashedCredentialsMatcher matcher = new HashedCredentialsMatcher(Sha512Hash.ALGORITHM_NAME);
        matcher.setHashIterations(hashIterations);
        _realm.setCredentialsMatcher(matcher);

        DefaultSecurityManager securityManager = new DefaultSecurityManager(_realm);
        DefaultSessionManager sessionManager = (DefaultSessionManager) securityManager.getSessionManager();
        sessionManager.setSessionDAO(new OFSessionDAO(store));
        sessionManager.setGlobalSessionTimeout(sessionTimeoutMillis);
        SecurityUtils.setSecurityManager(securityManager);

        if (!(OF.getCustomSerializer() instanceof JavaSerializer))
            throw new RuntimeException(Strings.MISSING_CUSTOM_SERIALIZER);
    }

    UsernamePasswordUserManager(Transaction trunk) {
        super(trunk);

        _store = null;
        _realm = null;
        _hashIterations = 0;
    }

    // TODO remove userData in next two methods

    @Override
    protected void createAccountImplementation(String username, String password, Object userData) {
        if (_store.getAccounts().get(username) != null)
            throw new RuntimeException("This username already exists.");

        byte[] salt = PlatformAdapter.createUID();
        Sha512Hash hash = new Sha512Hash(password, salt, _hashIterations);
        Account account = new Account(username);
        account.setSalt(salt);
        account.setPasswordHash(hash.getBytes());
        _store.getAccounts().put(username, account);
    }

    @Override
    protected void updateAccountImplementation(String username, String oldPassword, String newPassword, Object userData) {
        Account account = _store.getAccounts().get(username);

        if (account != null) {
            byte[] salt = PlatformAdapter.createUID();
            Sha512Hash hash = new Sha512Hash(newPassword, salt, _hashIterations);
            account.setSalt(salt);
            account.setPasswordHash(hash.getBytes());
        }
    }

    @Override
    protected void deleteAccountImplementation(String username) {
        _store.getAccounts().remove(username);
    }
}
