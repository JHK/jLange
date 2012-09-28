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

import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.npn.NextProtoNego;
import org.eclipse.jetty.npn.NextProtoNego.ServerProvider;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.spdy.DefaultSpdySettingsFrame;
import org.jboss.netty.handler.codec.spdy.SpdyFrameCodec;
import org.jboss.netty.handler.codec.spdy.SpdyHttpCodec;
import org.jboss.netty.handler.codec.spdy.SpdySessionHandler;
import org.jboss.netty.handler.codec.spdy.SpdySettingsFrame;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jlange.proxy.inbound.ssl.KeyStoreManager;
import org.jlange.proxy.inbound.ssl.SelfSignedKeyStoreManager;
import org.jlange.proxy.inbound.ssl.SslContextFactory;
import org.jlange.proxy.util.Config;
import org.jlange.proxy.util.IdleShutdownHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpdyPipelineFactory implements ChannelPipelineFactory {

    public static final String HTTP_1_1 = "http/1.1";
    public static final String SPDY_3   = "spdy/3";

    private final Logger       log      = LoggerFactory.getLogger(getClass());
    private final SSLContext   context;

    public SpdyPipelineFactory() {
        KeyStoreManager ksm = new SelfSignedKeyStoreManager();
        SslContextFactory scf = new SslContextFactory(ksm);
        this.context = scf.getServerContext();
    }

    public ChannelPipeline getPipeline() {
        final ChannelPipeline pipeline = Channels.pipeline();

        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(false);

        NextProtoNego.put(engine, new SimpleServerProvider());
        NextProtoNego.debug = log.isDebugEnabled();

        SslHandler ssl = new SslHandler(engine);
        ssl.setCloseOnSSLException(true);
        pipeline.addLast("ssl", ssl);
        pipeline.addLast("http_or_spdy", new HttpOrSpdyDecoder());

        return pipeline;
    }

    /**
     * This handler must be placed in the pipeline directly after a {@link SslHandler}. It will choose the pipeline for decoding the traffic
     * in the SSL session.
     */
    private final class HttpOrSpdyDecoder implements ChannelUpstreamHandler {

        public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
            final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
            final SimpleServerProvider provider = (SimpleServerProvider) NextProtoNego.get(sslHandler.getEngine());
            final String protocol = provider.getSelectedProtocol();

            if (protocol != null)
                log.debug("Channel {} - protocol: {}", e.getChannel().getId(), protocol);

            if (SPDY_3.equals(protocol)) {
                ChannelPipeline pipeline = ctx.getPipeline();
                pipeline.addLast("spdy", new SpdyFrameCodec(3, 8192, 16384, Config.COMPRESSION_LEVEL, 15, 8));
                pipeline.addLast("spdy_session_handler", new SpdySessionHandler(3, true));
                pipeline.addLast("spdy_setup", new SpdySetupHandler());
                pipeline.addLast("spdy_http", new SpdyHttpCodec(3, 2 * 1024 * 1024));
                pipeline.addLast("deflater", new HttpContentCompressor(Config.COMPRESSION_LEVEL));
                pipeline.addLast("proxy", new SpdyProxyHandler());

                // remove this handler, and process the requests as SPDY
                pipeline.remove(this);
                ctx.sendUpstream(e);
            } else if (HTTP_1_1.equals(protocol)) {
                ChannelPipeline pipeline = ctx.getPipeline();
                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("encoder", new HttpResponseEncoder());
                pipeline.addLast("deflater", new HttpContentCompressor(Config.COMPRESSION_LEVEL));
                pipeline.addLast("idle", new IdleShutdownHandler(300, 0));
                pipeline.addLast("proxy", new HttpProxyHandler());

                // remove this handler, and process the requests as HTTP
                pipeline.remove(this);
                ctx.sendUpstream(e);
            } else {
                // still in protocol negotiaton, so do nothing in here
            }
        }
    }

    private final class SimpleServerProvider implements ServerProvider {

        private String selectedProtocol = null;

        public void unsupported() {
            selectedProtocol = HTTP_1_1;
        }

        public List<String> protocols() {
            return Arrays.asList(SPDY_3, HTTP_1_1);
        }

        public void protocolSelected(String protocol) {
            selectedProtocol = protocol;
        }

        public String getSelectedProtocol() {
            return selectedProtocol;
        }

    }

    private final class SpdySetupHandler implements ChannelUpstreamHandler {

        private boolean setupComplete = false;

        @Override
        public void handleUpstream(final ChannelHandlerContext ctx, final ChannelEvent evt) {
            if (!setupComplete && ctx.getChannel().isConnected()) {
                log.info("Channel {} - apply default setup", ctx.getChannel().getId());
                setupComplete = true;
                SpdySettingsFrame frame = new DefaultSpdySettingsFrame();
                frame.setValue(SpdySettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS, 100);
                frame.setValue(SpdySettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE, 65536);
                frame.setValue(SpdySettingsFrame.SETTINGS_ROUND_TRIP_TIME, 200);
                ctx.getChannel().write(frame);
            }

            ctx.sendUpstream(evt);
        }
    }
}
