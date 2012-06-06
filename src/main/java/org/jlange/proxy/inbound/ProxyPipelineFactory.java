/*
 * Copyright (C) 2012 Julian Knocke
 * 
 * This file is part of jLange.
 * 
 * jLange is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * jLange is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with jLange. If not, see <http://www.gnu.org/licenses/>.
 */
package org.jlange.proxy.inbound;

import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.npn.NextProtoNego;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.spdy.SpdyFrameDecoder;
import org.jboss.netty.handler.codec.spdy.SpdyFrameEncoder;
import org.jboss.netty.handler.codec.spdy.SpdySessionHandler;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jlange.proxy.inbound.ssl.KeyStoreManager;
import org.jlange.proxy.inbound.ssl.SelfSignedKeyStoreManager;
import org.jlange.proxy.inbound.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyPipelineFactory implements ChannelPipelineFactory {

    private final Logger    log = LoggerFactory.getLogger(getClass());
    private final SSLEngine engine;

    public ProxyPipelineFactory() {
        KeyStoreManager ksm = new SelfSignedKeyStoreManager(false);
        SslContextFactory scf = new SslContextFactory(ksm);
        this.engine = scf.getServerContext().createSSLEngine();
        this.engine.setUseClientMode(false);

        NextProtoNego.put(this.engine, new SimpleServerProvider());
        NextProtoNego.debug = true;
    }

    public ChannelPipeline getPipeline() {
        final ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("decoder", new HttpOrSpdyDecoder());
        pipeline.addLast("spdy_encoder", new SpdyFrameEncoder());
        pipeline.addLast("http_encoder", new HttpResponseEncoder());
        pipeline.addLast("deflater", new HttpContentCompressor(9));
        pipeline.addLast("spdy_session_handler", new SpdySessionHandler(true));
        pipeline.addLast("idle", new IdleShutdownHandler(60, 0));
        pipeline.addLast("proxy_protocol", new ProxyProtocolHandler(engine));
        pipeline.addLast("proxy", new ProxyHandler());

        return pipeline;
    }

    private class HttpOrSpdyDecoder extends SimpleChannelUpstreamHandler {
        private final HttpRequestDecoder httpRequestDecoder = new HttpRequestDecoder();
        private final SpdyFrameDecoder   spdyFrameDecoder   = new SpdyFrameDecoder();

        public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
            SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
            SimpleServerProvider provider = new SimpleServerProvider();

            if (sslHandler != null)
                provider = (SimpleServerProvider) NextProtoNego.get(sslHandler.getEngine());

            if (provider != null)
                log.debug("Protocol: {}", provider.getSelectedProtocol());

            if (provider != null && "spdy/2".equals(provider.getSelectedProtocol()))
                spdyFrameDecoder.handleUpstream(ctx, e);
            else
                httpRequestDecoder.handleUpstream(ctx, e);
        }
    }
}
