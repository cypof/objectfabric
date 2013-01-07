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

package part04;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.Assert;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.Netty;
import org.objectfabric.Workspace;

/**
 * URI schemes supported by {@link Netty}, the default transport on the JVM.
 */
public class SchemesClient {

    public static void main(String[] args) throws Exception {
        Workspace workspace = new JVMWorkspace();
        workspace.addURIHandler(new TestURIHandler());
        Object value;

        /*
         * Get resource over socket.
         */
        value = workspace.open("tcp://localhost:1850/test").get();
        Assert.assertEquals("data", value);

        /*
         * Get resource over secure socket.
         */
        value = workspace.open("ssl://localhost:1853/test").get();
        Assert.assertEquals("data", value);

        /*
         * Get resource over WebSocket.
         */
        value = workspace.open("ws://localhost:8888/test").get();
        Assert.assertEquals("data", value);

        /*
         * Get resource over secure WebSocket.
         */
        value = workspace.open("wss://localhost:8883/test").get();
        Assert.assertEquals("data", value);

        System.out.println("Done!");
        workspace.close();
    }

    /**
     * Self-signed certificate used by this test for SSL connections would not be accepted
     * by Java's default trusted CA, so use a SSLContext that accepts any certificate.
     */
    public static class TestURIHandler extends Netty {

        @Override
        protected SSLContext createSSLContext() throws Exception {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] { ACCEPT_ALL }, null);
            return context;
        }

        private static final TrustManager ACCEPT_ALL = new X509TrustManager() {

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }
        };
    }
}
