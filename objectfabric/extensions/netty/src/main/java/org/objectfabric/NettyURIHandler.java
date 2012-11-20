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

package org.objectfabric;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.ssl.SslHandler;

/**
 * Netty (http://netty.io) transport. Supports "tcp", "ssl", "ws" and "wss" schemes.
 */
public class NettyURIHandler extends ClientURIHandler {

    private final ChannelFactory _factory;

    private SSLContext _clientContext;

    public NettyURIHandler() {
        this(new NioClientSocketChannelFactory(ThreadPool.getInstance(), ThreadPool.getInstance()));
    }

    public NettyURIHandler(ClientSocketChannelFactory factory) {
        _factory = factory;
    }

    public ChannelFactory getChannelFactory() {
        return _factory;
    }

    @Override
    public URI handle(Address address, String path) {
        if (address.Host != null && address.Host.length() > 0) {
            String s = address.Scheme;

            if (Remote.TCP.equals(s) || Remote.SSL.equals(s) || Remote.WS.equals(s) || Remote.WSS.equals(s)) {
                Remote remote = get(address);
                return remote.getURI(path);
            }
        }

        return null;
    }

    /**
     * Can be overriden to modify default Netty pipelines. ObjectFabric's handler is added
     * as last afterward.
     */
    protected ChannelPipeline createPipeline(Address address) throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();

        if (Remote.TCP.equals(address.Scheme))
            return pipeline;

        if (Remote.SSL.equals(address.Scheme)) {
            pipeline.addLast("ssl", new SslHandler(createSSLEngine()));
            return pipeline;
        }

        if (Remote.WS.equals(address.Scheme)) {
            pipeline.addLast("decoder", new HttpResponseDecoder());
            pipeline.addLast("encoder", new HttpRequestEncoder());
            return pipeline;
        }

        if (Remote.WSS.equals(address.Scheme)) {
            pipeline.addLast("ssl", new SslHandler(createSSLEngine()));
            pipeline.addLast("decoder", new HttpResponseDecoder());
            pipeline.addLast("encoder", new HttpRequestEncoder());
            return pipeline;
        }

        throw new IllegalArgumentException("Unsupported scheme: " + address);
    }

    /**
     * Can be overriden to modify default SSLEngine, e.g. to add server key validation.
     */
    protected SSLContext createSSLContext() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        return context;
    }

    private final SSLEngine createSSLEngine() throws Exception {
        if (_clientContext == null)
            _clientContext = createSSLContext();

        SSLEngine engine = _clientContext.createSSLEngine();
        engine.setUseClientMode(true);
        return engine;
    }

    @Override
    Remote createRemote(Address address) {
        return new NettyRemote(address, this);
    }
}
