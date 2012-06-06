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

import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.SSLEngine;

import org.htmlparser.http.HttpHeader;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jlange.proxy.outbound.HttpPipelineFactory;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.jlange.proxy.outbound.PassthroughHandler;
import org.jlange.proxy.util.RemoteAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link HttpRequest}s send to proxies look slightly different than direct requests to webserver. This hander takes care of these
 * differences and reforms the {@link HttpRequest} to look like a direct connection to a webserver. If the target is a SSL encrypted
 * webserver this handler builds a passthrough or opens the SSL connection, depending if there is a {@link SSLEngine} set at creation time.
 * Additionally this handler takes care of {@code Proxy-Connection} {@link HttpHeader} in the {@link HttpRequest} and sets the respective
 * {@link HttpHeader} of the {@link HttpResponse}.
 */
public class ProxyProtocolHandler extends SimpleChannelHandler implements ChannelHandler {
    private final Logger    log = LoggerFactory.getLogger(getClass());

    private final SSLEngine engine;

    /**
     * {@link RemoteAddress} to which the proxy is connected
     */
    private RemoteAddress   address;
    private Boolean         proxyKeepAlive;

    public ProxyProtocolHandler() {
        this(null);
    }

    public ProxyProtocolHandler(final SSLEngine engine) {
        this.engine = engine;
        this.address = null;
        this.proxyKeepAlive = true;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        final HttpRequest request = (HttpRequest) e.getMessage();

        if (request.getMethod().equals(HttpMethod.CONNECT)) {
            log.info("Channel {} - connect received - open a new channel to {}", e.getChannel().getId(), request.getUri());
            address = RemoteAddress.parseRequest(request);
            // TODO: outbound MITM connection is still unencrypted, try to encrypt it
            if (engine != null && address.getPort() == 443)
                address = new RemoteAddress(address.getHost(), 80);
            final ChannelPipelineFactory factory = getPipelineFactory(e.getChannel());
            final ChannelFuture outboundFuture = OutboundChannelPool.getInstance().getNewChannelFuture(address, factory);

            outboundFuture.addListener(new ChannelFutureListener() {
                public void operationComplete(final ChannelFuture outboundFuture) {
                    log.debug("Channel {} - channel opened - send ok", e.getChannel().getId());
                    final HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    HttpHeaders.setKeepAlive(response, true);
                    final ChannelFuture inboundFuture = ctx.getChannel().write(response);
                    if (engine == null)
                        inboundFuture.addListener(getPassthroughChannelFutureListener(outboundFuture));
                    else
                        inboundFuture.addListener(getMITMChannelFutureListener(outboundFuture));
                }
            });

        } else if (address == null) {
            log.debug("Channel {} - request received, update and forward", e.getChannel().getId());
            proxyKeepAlive = HttpHeaders.getHeader(request, "Proxy-Connection", "keep-alive").toLowerCase().equals("keep-alive");
            request.removeHeader("Proxy-Connection");
            updateRequest(request);
            ctx.sendUpstream(e);
        } else {
            log.debug("Channel {} - request received, forward", e.getChannel().getId());
            HttpHeaders.setHost(request, address.toString());
            ctx.sendUpstream(e);
        }
    }

    @Override
    public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        if (e.getMessage() instanceof HttpResponse)
            HttpHeaders.setKeepAlive((HttpResponse) e.getMessage(), proxyKeepAlive);
        super.writeRequested(ctx, e);
    }

    private ChannelPipelineFactory getPipelineFactory(final Channel inboundChannel) {
        final ChannelPipelineFactory factory;

        if (engine == null) {
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

    private void updateRequest(final HttpRequest request) {
        final StringBuilder sb = new StringBuilder();
        final String uriString = request.getUri();
        if (uriString.startsWith("http")) {
            try {
                final URL url = new URL(request.getUri());
                sb.append(url.getPath());
                if (url.getQuery() != null)
                    sb.append("?").append(url.getQuery());
                request.setUri(sb.toString());
            } catch (MalformedURLException e2) {
                log.error("Request: {}\nException: {}", request.toString(), e2.toString() + "\n" + e2.fillInStackTrace().toString());
            }
        }
    }

    private ChannelFutureListener getMITMChannelFutureListener(final ChannelFuture outboundFuture) {
        return new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture inboundFuture) {
                log.debug("Channel {} - update pipeline (MITM)", inboundFuture.getChannel().getId());
                final ChannelPipeline pipe = inboundFuture.getChannel().getPipeline();
                pipe.getChannel().setReadable(false);
                pipe.addFirst("ssl", new SslHandler(engine));
                pipe.remove("deflater");
                pipe.remove("idle");
                pipe.getChannel().setReadable(true);
                OutboundChannelPool.getInstance().setChannelIdle(outboundFuture);
            }
        };
    }

    private ChannelFutureListener getPassthroughChannelFutureListener(final ChannelFuture outboundFuture) {
        return new ChannelFutureListener() {
            public void operationComplete(ChannelFuture inboundFuture) {
                log.debug("Channel {} - update pipeline (passthrough)", inboundFuture.getChannel().getId());
                final Channel inboundChannel = inboundFuture.getChannel();
                inboundChannel.setReadable(false);
                while (inboundChannel.getPipeline().getFirst() != null)
                    inboundChannel.getPipeline().removeFirst();
                inboundChannel.getPipeline().addLast("passthrough", new PassthroughHandler(outboundFuture.getChannel()));
                inboundChannel.setReadable(true);
            }
        };
    }
}
