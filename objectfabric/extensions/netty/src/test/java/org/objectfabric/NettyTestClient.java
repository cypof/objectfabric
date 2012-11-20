///**
// * This file is part of ObjectFabric (http://objectfabric.org).
// *
// * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
// * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
// * 
// * Copyright ObjectFabric Inc.
// * 
// * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
// * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
// */
//
//package org.objectfabric;
//
//import java.security.cert.CertificateException;
//import java.security.cert.X509Certificate;
//import java.util.concurrent.Executors;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import javax.net.ssl.SSLContext;
//import javax.net.ssl.TrustManager;
//import javax.net.ssl.X509TrustManager;
//
//import org.jboss.netty.channel.Channel;
//import org.jboss.netty.channel.ChannelPipeline;
//import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
//import org.objectfabric.NettyTest.TestConnection;
//
//public class NettyTestClient {
//
//    static final int START = 0;
//
//    static final int GO = 1;
//
//    static final int DONE = 2;
//
//    public static void main(final AtomicInteger progress, int number, String scheme) throws Exception {
//        JVMPlatform.loadClass();
//        Helper.instance().ProcessName = "Client " + number;
//
//        TestURIHandler handler = new TestURIHandler() {
//
//            @Override
//            protected ChannelPipeline createPipeline(Address address) throws Exception {
//                // TODO Auto-generated method stub
//                return super.createPipeline(address);
//            }
//
//            @Override
//            Remote createRemote(Address address) {
//                NettyRemote remote = new NettyRemote(address, this) {
//
//                    @Override
//                    protected NettyConnection createConnection(Channel channel, boolean webSocket) {
//                        return new TestConnection(null, this, channel, webSocket);
//                    }
//                };
//
//                return remote;
//            }
//        };
//
//        Workspace workspace = new JVMWorkspace();
//        workspace.addURIHandler(handler);
//        Resource resource = workspace.resolve(scheme + "://localhost:8080/_");
//        Remote origin = (Remote) resource.origin();
//
//        while (origin.connection() == null)
//            Thread.sleep(1);
//
//        TestConnection connection = (TestConnection) origin.connection();
//        resource.uri().getOrCreate(origin);
//        SeparateCL.waitForProgress(progress, GO);
//
//        while (!connection.isDone()) {
//            if (Platform.get().randomBoolean())
//                connection.writeOnKnown(resource.uri());
//            else
//                connection.writeBlock(resource.uri());
//
//            if (connection.isDoneWriting())
//                Thread.sleep(1);
//        }
//
//        workspace.close();
//        progress.set(DONE);
//        ClientURIHandler.disableNetwork();
//    }
//
//    private static class TestURIHandler extends NettyURIHandler {
//
//        TestURIHandler() {
//            super(new NioClientSocketChannelFactory( //
//                    Executors.newFixedThreadPool(2), Executors.newFixedThreadPool(2)));
//        }
//
//        @Override
//        protected SSLContext createSSLContext() throws Exception {
//            SSLContext context = SSLContext.getInstance("TLS");
//            context.init(null, new TrustManager[] { ACCEPT_ALL }, null);
//            return context;
//        }
//
//        private static final TrustManager ACCEPT_ALL = new X509TrustManager() {
//
//            public X509Certificate[] getAcceptedIssuers() {
//                return new X509Certificate[0];
//            }
//
//            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
//            }
//
//            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
//            }
//        };
//    }
//}
