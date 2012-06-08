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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.npn.NextProtoNego;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jlange.proxy.inbound.HttpProxyHandler;
import org.jlange.proxy.inbound.SimpleServerProvider;
import org.jlange.proxy.outbound.HttpPipelineFactory;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.jlange.proxy.outbound.PassthroughHandler;
import org.jlange.proxy.util.RemoteAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If the target is a SSL encrypted webserver this handler builds a passthrough or opens the SSL connection, depending if there is a
 * {@link SSLContext} set at creation time. This handler will change the pipeline of the current channel.
 */
public class HttpProxyConnectionHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {
    private final Logger     log = LoggerFactory.getLogger(getClass());

    private final SSLContext context;

    public HttpProxyConnectionHandler() {
        this(null);
    }

    public HttpProxyConnectionHandler(final SSLContext context) {
//        this.context = context;
        this.context = null;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        final HttpRequest request = (HttpRequest) e.getMessage();

        if (request.getMethod().equals(HttpMethod.CONNECT)) {
            log.info("Channel {} - connect received - open a new channel to {}", e.getChannel().getId(), request.getUri());
            log.debug("Channel {} - {}", e.getChannel().getId(), request.toString());
            RemoteAddress address = RemoteAddress.parseRequest(request);
            // TODO: outbound MITM connection is still unencrypted, try to encrypt it
            if (context != null && address.getPort() == 443)
                address = new RemoteAddress(address.getHost(), 80);
            final ChannelPipelineFactory factory = getPipelineFactory(e.getChannel());
            final ChannelFuture outboundFuture = OutboundChannelPool.getInstance().getNewChannelFuture(address, factory);

            outboundFuture.addListener(new ChannelFutureListener() {
                public void operationComplete(final ChannelFuture outboundFuture) {
                    log.info("Channel {} - channel opened - send ok", e.getChannel().getId());
                    ctx.getChannel().setReadable(false);
                    final ChannelFuture inboundFuture = ctx.getChannel().write(getHttpResponseOk());
                    if (context == null) {
                        inboundFuture.addListener(getPassthroughChannelFutureListener(outboundFuture.getChannel()));
                    } else {
                        inboundFuture.addListener(getMITMChannelFutureListener());
                        OutboundChannelPool.getInstance().setChannelIdle(outboundFuture);
                    }
                }
            });
        } else {
            ctx.sendUpstream(e);
        }
    }

    private HttpResponse getHttpResponseOk() {
        final HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        return response;
    }

    private ChannelPipelineFactory getPipelineFactory(final Channel inboundChannel) {
        final ChannelPipelineFactory factory;

        if (context == null) {
            factory = new ChannelPipelineFactory() {
                public ChannelPipeline getPipeline() {
                    ChannelPipeline pipeline = Channels.pipeline();
                    pipeline.addLast("passthrough", new PassthroughHandler(inboundChannel));
                    return pipeline;
                }
            };
        } else {
            factory = new HttpPipelineFactory();
        }

        return factory;
    }

    private ChannelFutureListener getMITMChannelFutureListener() {
        return new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture inboundFuture) {
                log.debug("Channel {} - update pipeline (MITM)", inboundFuture.getChannel().getId());
                final ChannelPipeline pipe = inboundFuture.getChannel().getPipeline();
                while (pipe.getFirst() != null)
                    pipe.removeFirst();

                SSLEngine engine = context.createSSLEngine();
                engine.setUseClientMode(false);

                NextProtoNego.put(engine, new SimpleServerProvider());
                if (log.isDebugEnabled())
                    NextProtoNego.debug = true;

                pipe.addLast("ssl", new SslHandler(engine));
                pipe.addLast("http_or_spdy", new HttpOrSpdyDecoder(new HttpProxyHandler()));
                pipe.getChannel().setReadable(true);
            }
        };
    }

    private ChannelFutureListener getPassthroughChannelFutureListener(final Channel outboundChannel) {
        return new ChannelFutureListener() {
            public void operationComplete(ChannelFuture inboundFuture) {
                log.debug("Channel {} - update pipeline (passthrough)", inboundFuture.getChannel().getId());
                final Channel inboundChannel = inboundFuture.getChannel();
                while (inboundChannel.getPipeline().getFirst() != null)
                    inboundChannel.getPipeline().removeFirst();
                inboundChannel.getPipeline().addLast("passthrough", new PassthroughHandler(outboundChannel));
                inboundChannel.setReadable(true);
            }
        };
    }
}
