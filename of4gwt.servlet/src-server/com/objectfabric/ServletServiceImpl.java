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

import java.util.Vector;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import of4gwt.ServletService;
import of4gwt.misc.Log;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.transports.polling.PollingServer;
import com.objectfabric.transports.polling.PollingServer.PollingSession;

/**
 * The server side implementation of the RPC service.
 */
public abstract class ServletServiceImpl extends RemoteServiceServlet implements ServletService {

    private static final String SESSION_ATTRIBUTE = "OF_SESSION";

    protected abstract ServletSession createSession();

    @SuppressWarnings("unchecked")
    public final byte[] connect() {
        if (Debug.ENABLED) {
            /*
             * Same as of4gwt.misc.PlatformAdapter.init(), throws if of4gwt classes are
             * loaded in a JVM. Might not work on other JVM than Sun's. This is just for
             * debugging, remove if it causes any problem.
             */
            ClassLoader loader = Thread.currentThread().getContextClassLoader();

            try {
                Vector<Class> classes = (Vector) PlatformAdapter.getPrivateField(loader, "classes", ClassLoader.class);
                boolean error = false;

                for (Class c : classes.toArray(new Class[classes.size()])) {
                    String name = c.getName();

                    if (name.startsWith("of4gwt") && !name.equals("of4gwt.PollingService")) {
                        Log.write(name);
                        error = true;
                    }
                }

                Debug.assertion(!error);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        HttpSession http = getThreadLocalRequest().getSession();
        ServletSession of = createSession();
        of.setHttpSession(http);

        if (Debug.ENABLED)
            Debug.disableEqualsOrHashCheck();

        http.setAttribute(SESSION_ATTRIBUTE, of);

        if (Debug.ENABLED)
            Debug.enableEqualsOrHashCheck();

        return PlatformAdapter.createUID(); // UID to initialize GWT generator
    }

    /**
     * TODO: use a message counter for connection losses, retries etc.
     */
    public final byte[] call(byte[] data) {
        HttpSession http = getThreadLocalRequest().getSession();
        ServletSession session = (ServletSession) http.getAttribute(SESSION_ATTRIBUTE);

        if (session == null)
            throw new RuntimeException(Strings.SESSION_EXPIRED);

        return session.call(data);
    }

    public static class ServletSession extends PollingSession implements HttpSessionBindingListener {

        private HttpSession _httpSession;

        public ServletSession(PollingServer server) {
            super(server);
        }

        public HttpSession getHttpSession() {
            return _httpSession;
        }

        protected void setHttpSession(HttpSession value) {
            _httpSession = value;
        }

        public void valueBound(HttpSessionBindingEvent e) {
        }

        public void valueUnbound(HttpSessionBindingEvent e) {
            close();
        }
    }
}
