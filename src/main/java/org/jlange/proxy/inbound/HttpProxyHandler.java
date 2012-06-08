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

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.Tools;
import org.jlange.proxy.outbound.HttpPipelineFactory;
import org.jlange.proxy.outbound.HttpPluginHandler;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.jlange.proxy.plugin.PluginProvider;
import org.jlange.proxy.util.HttpResponseListener;
import org.jlange.proxy.util.RemoteAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        if (e.getChannel().isConnected())
            e.getChannel().write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY));

        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        Tools.closeOnFlush(e.getChannel());
    }

    @Override
    public void channelBound(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Channel {} - created", e.getChannel().getId());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpRequest request = (HttpRequest) e.getMessage();
        final RemoteAddress address = RemoteAddress.parseRequest(request);

        log.info("Channel {} - request received - {}", e.getChannel().getId(), address + request.getUri());
        log.debug(request.toString());

        // request plugins
        // TODO: implement
        HttpHeaders.setKeepAlive(request, true);

        // request to predefined response plugins
        // TODO: implement

        final ChannelPipelineFactory factory = new HttpPipelineFactory();
        final ChannelFuture outboundFuture = OutboundChannelPool.getInstance().getIdleOrNewChannelFuture(address, factory);

        // set actions when response arrives
        final HttpPluginHandler outboundHandler = outboundFuture.getChannel().getPipeline().get(HttpPluginHandler.class);
        outboundHandler.setResponsePlugins(PluginProvider.getInstance().getResponsePlugins(request));
        outboundHandler.setResponseListener(new HttpResponseListener() {
            @Override
            public void responseReceived(final HttpResponse response) {
                log.info("Channel {} - sending response - {}", e.getChannel().getId(), response.getStatus());
                if (e.getChannel().isConnected())
                    e.getChannel().write(response);
                else
                    log.warn("Channel {} - try to write response on closed channel - skipped", e.getChannel().getId());
            }
        });

        // perform request on outbound channel
        outboundFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) {
                log.info("Channel {} - sending request", future.getChannel().getId());
                future.getChannel().write(request);
            }
        });
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Channel {} - closed", e.getChannel().getId());
    }
}
