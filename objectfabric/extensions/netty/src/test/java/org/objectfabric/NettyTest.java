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
//import java.io.FileInputStream;
//import java.net.InetSocketAddress;
//import java.security.KeyStore;
//import java.util.ArrayList;
//import java.util.concurrent.ConcurrentLinkedQueue;
//import java.util.concurrent.Executors;
//
//import javax.net.ssl.KeyManagerFactory;
//import javax.net.ssl.SSLContext;
//import javax.net.ssl.SSLEngine;
//import javax.net.ssl.TrustManagerFactory;
//
//import org.jboss.netty.bootstrap.ServerBootstrap;
//import org.jboss.netty.channel.Channel;
//import org.jboss.netty.channel.ChannelPipeline;
//import org.jboss.netty.channel.ChannelPipelineFactory;
//import org.jboss.netty.channel.Channels;
//import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
//import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
//import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
//import org.jboss.netty.handler.ssl.SslHandler;
//import org.junit.Assert;
//import org.junit.Test;
//
//public class NettyTest {
//
//    private JVMServer _server = new JVMServer();
//
//    private ConcurrentLinkedQueue<TestSession> _sessions = new ConcurrentLinkedQueue<TestSession>();
//
//    private TestLocation _location;
//
//    private URI _uri;
//
//    public NettyTest() {
//    }
//
//    @Test
//    public void tcp() throws Exception {
//        run(4, "tcp");
//    }
//
//    @Test
//    public void ssl() throws Exception {
//        run(4, "ssl");
//    }
//
//    @Test
//    public void ws() throws Exception {
//        run(4, "ws");
//    }
//
//    @Test
//    public void wss() throws Exception {
//        run(4, "wss");
//    }
//
//    // TODO test IPv6
//
//    private void run(int clientCount, final String scheme) throws Exception {
//        JVMPlatform.loadClass();
//        Helper.instance().ProcessName = "Server";
//
//        _location = new TestLocation();
//        _server.addURIHandler(_location);
//        _uri = _location.getURI("/_");
//        _location.setURI(_uri);
//
//        final SSLContext sslContext = scheme.equals("ssl") || scheme.equals("wss") ? createSSLContext() : null;
//
//        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory( //
//                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
//
//        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
//
//            @Override
//            public ChannelPipeline getPipeline() throws Exception {
//                ChannelPipeline pipeline = Channels.pipeline();
//
//                if (sslContext != null) {
//                    SSLEngine engine = sslContext.createSSLEngine();
//                    engine.setUseClientMode(false);
//                    pipeline.addLast("ssl", new SslHandler(engine));
//                }
//
//                if (scheme.equals("ws") || scheme.equals("wss")) {
//                    pipeline.addLast("decoder", new HttpRequestDecoder());
//                    pipeline.addLast("encoder", new HttpResponseEncoder());
//                }
//
//                pipeline.addLast("handler", new TestSession());
//                return pipeline;
//            }
//        });
//
//        Channel server = bootstrap.bind(new InetSocketAddress(8080));
//
//        //
//
//        ArrayList<SeparateCL> clients = new ArrayList<SeparateCL>();
//
//        for (int i = 0; i < clientCount; i++) {
//            SeparateCL client = new SeparateCL("Client Thread " + (i + 1), NettyTestClient.class.getName(), true);
//            client.setArgTypes(int.class, String.class);
//            client.setArgs(i + 1, scheme);
//            clients.add(client);
//            client.start();
//        }
//
//        while (_sessions.size() < clients.size())
//            Thread.sleep(1);
//
//        for (;;) {
//            boolean ready = true;
//
//            for (TestSession session : _sessions) {
//                if (session.getConnection() == null)
//                    ready = false;
//                else if (!_uri.contains(_location))
//                    ready = false;
//            }
//
//            if (ready)
//                break;
//
//            Thread.sleep(1);
//        }
//
//        TestsHelper.assertMemory("All connected");
//
//        for (SeparateCL client : clients)
//            client.setProgress(NettyTestClient.GO);
//
//        for (;;) {
//            boolean done = true, doneWriting = true;
//
//            for (NettySession session : _sessions) {
//                TestConnection test = (TestConnection) session.getConnection();
//
//                if (Platform.get().randomBoolean())
//                    test.writeOnKnown(_uri);
//                else
//                    test.writeBlock(_uri);
//
//                done &= test.isDone();
//                doneWriting &= test.isDoneWriting();
//            }
//
//            if (done)
//                break;
//
//            if (doneWriting)
//                Thread.sleep(1);
//        }
//
//        for (SeparateCL client : clients)
//            client.waitForProgress(NettyTestClient.DONE);
//
//        TestsHelper.assertMemory("All done");
//        server.close();
//        Helper.instance().ProcessName = "";
//
//        for (;;) {
//            boolean done = true;
//
//            for (NettySession session : _sessions) {
//                TestConnection test = (TestConnection) session.getConnection();
//                done &= test.isClosed();
//            }
//
//            if (done)
//                break;
//
//            Thread.sleep(1);
//        }
//
//        _sessions.clear();
//
//        Thread.sleep(100);
//
//        if (Debug.ENABLED)
//            Helper.instance().assertClassLoaderIdle();
//    }
//
//    private class TestSession extends NettySession {
//
//        TestSession() {
//            super(_server);
//
//            _sessions.add(this);
//        }
//
//        @Override
//        protected NettyConnection createConnection(org.jboss.netty.channel.Channel channel, boolean webSocket, Headers headers) {
//            return new TestConnection(_server, channel, webSocket);
//        }
//    }
//
//    static final class TestConnection extends NettyConnection {
//
//        private static final int KNOWN = 12;
//
//        private final DataGen _gen = new DataGen(100, 100);
//
//        private int _sent, _received;
//
//        TestConnection(JVMServer server, org.jboss.netty.channel.Channel channel, boolean webSocket) {
//            super(server, channel, webSocket, null);
//        }
//
//        boolean isDone() {
//            return _received == KNOWN && _gen.isDone();
//        }
//
//        boolean isDoneWriting() {
//            return _gen.isDoneWriting();
//        }
//
//        void writeOnKnown(URI uri) {
//            if (_sent < KNOWN) {
//                long[] ticks = new long[OpenMap.CAPACITY];
//
//                for (int i = 0; i < 4; i++) {
//                    long tick = Tick.get(Peer.get(new UID(Platform.get().newUID())).index(), 5);
//                    ticks = Tick.add(ticks, tick);
//                }
//
//                onReadKnown(uri, uri.getOrCreate(location()), ticks);
//                _sent += 4;
//            }
//        }
//
//        @Override
//        void onKnown(URI uri, long[] ticks) {
//            Tick.checkSet(ticks);
//            int count = 0;
//
//            for (int i = 0; i < ticks.length; i++) {
//                if (!Tick.isNull(ticks[i])) {
//                    Assert.assertEquals(5, Tick.time(ticks[i]));
//                    count++;
//                }
//            }
//
//            _received += count;
//            Assert.assertEquals(4, count);
//        }
//
//        void writeBlock(URI uri) {
//            long block = Tick.get(Peer.get(new UID(Platform.get().newUID())).index(), 42);
//            JVMBuff[] buffs = _gen.write();
//
//            if (buffs != null) {
//                boolean requested = Platform.get().randomBoolean();
//                long[] removals = null;
//
//                for (int i = 0; i < (requested ? 5 : 20); i++) {
//                    int peer = Peer.get(new UID(Platform.get().newUID())).index();
//                    removals = Tick.add(removals, Tick.get(peer, i));
//                }
//
//                onBlock(uri, uri.getOrCreate(this), block, buffs, removals, requested);
//
//                if (Debug.THREADS)
//                    ThreadAssert.exchangeTake(buffs);
//
//                for (int i = 0; i < buffs.length; i++)
//                    buffs[i].recycle();
//            }
//        }
//
//        @Override
//        void onReadBlock(URI uri, long tick, JVMBuff[] buffs, long[] removals, boolean requested) {
//            _gen.read(buffs);
//
//            Assert.assertEquals(42, Tick.time(tick));
//            int max = requested ? 5 : 20;
//            int count = 0;
//
//            for (int i = 0; i < removals.length; i++) {
//                if (!Tick.isNull(removals[i])) {
//                    count++;
//                    Assert.assertTrue(Tick.time(removals[i]) < max);
//                }
//            }
//
//            Assert.assertEquals(max, count);
//        }
//    }
//
//    /**
//     * Initiates a SSL context from self-signed certificate.
//     */
//    private static SSLContext createSSLContext() throws Exception {
//        KeyStore ks = KeyStore.getInstance("JKS");
//        KeyStore ts = KeyStore.getInstance("JKS");
//
//        char[] passphrase = "passphrase".toCharArray();
//        ks.load(new FileInputStream("src/test/java/org/objectfabric/keystore.jks"), passphrase);
//        ts.load(new FileInputStream("src/test/java/org/objectfabric/keystore.jks"), passphrase);
//
//        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
//        kmf.init(ks, passphrase);
//
//        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
//        tmf.init(ts);
//
//        SSLContext ssl = SSLContext.getInstance("TLS");
//        ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
//        return ssl;
//    }
//}
