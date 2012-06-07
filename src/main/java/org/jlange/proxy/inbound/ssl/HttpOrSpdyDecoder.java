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
package org.jlange.proxy.inbound.ssl;

import static org.jlange.proxy.inbound.SimpleServerProvider.HTTP_1_1;
import static org.jlange.proxy.inbound.SimpleServerProvider.SPDY_2;

import org.eclipse.jetty.npn.NextProtoNego;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.spdy.SpdyFrameDecoder;
import org.jboss.netty.handler.codec.spdy.SpdyFrameEncoder;
import org.jboss.netty.handler.codec.spdy.SpdyHttpDecoder;
import org.jboss.netty.handler.codec.spdy.SpdyHttpEncoder;
import org.jboss.netty.handler.codec.spdy.SpdySessionHandler;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jlange.proxy.inbound.SimpleServerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This handler must be placed in the pipeline directly after a {@link SslHandler}. It will choose the pipeline for decoding the traffic in
 * the SSL session.
 */
public class HttpOrSpdyDecoder implements ChannelUpstreamHandler {

    private final Logger         log = LoggerFactory.getLogger(getClass());
    private final ChannelHandler handler;

    public HttpOrSpdyDecoder(final ChannelHandler handler) {
        this.handler = handler;
    }

    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
        final SimpleServerProvider provider = (SimpleServerProvider) NextProtoNego.get(sslHandler.getEngine());
        final String protocol = provider.getSelectedProtocol();

        if (protocol != null)
            log.debug("Protocol: {}", protocol);

        if (SPDY_2.equals(protocol)) {
            ChannelPipeline pipeline = ctx.getPipeline();
            pipeline.addLast("decoder", new SpdyFrameDecoder());
            pipeline.addLast("encoder", new SpdyFrameEncoder());
            pipeline.addLast("spdy_session_handler", new SpdySessionHandler(true));
            pipeline.addLast("spdy_http_encoder", new SpdyHttpEncoder());
            pipeline.addLast("spdy_http_decoder", new SpdyHttpDecoder(2 * 1024 * 1024));
            pipeline.addLast("handler", handler);

            // remove this handler, and process the requests as SPDY
            pipeline.remove(this);
            ctx.sendUpstream(e);
        } else if (HTTP_1_1.equals(protocol)) {
            ChannelPipeline pipeline = ctx.getPipeline();
            pipeline.addLast("decoder", new HttpRequestDecoder());
            pipeline.addLast("encoder", new HttpResponseEncoder());
            pipeline.addLast("handler", handler);

            // remove this handler, and process the requests as HTTP
            pipeline.remove(this);
            ctx.sendUpstream(e);
        } else {
            // still in protocol negotiaton, so do nothing in here
        }
    }
}
