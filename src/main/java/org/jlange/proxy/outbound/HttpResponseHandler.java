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
package org.jlange.proxy.outbound;

import java.util.LinkedList;
import java.util.Queue;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
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
import org.jlange.proxy.util.HttpResponseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpResponseHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private final Logger                log = LoggerFactory.getLogger(getClass());
    private Queue<HttpResponseListener> httpResponseListenerQueue;

    public HttpResponseHandler() {
        httpResponseListenerQueue = new LinkedList<HttpResponseListener>();
    }

    public void addResponseListener(final HttpResponseListener httpResponseListener) {
        this.httpResponseListenerQueue.add(httpResponseListener);
    }

    public void sendRequest(final ChannelFuture future, final HttpRequest request) {
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) {
                log.info("Channel {} - sending request - {}", future.getChannel().getId(), request.getUri());
                future.getChannel().write(request);
            }
        });
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        e.getChannel().close();

        responseReceived(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY));
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Channel {} - closed", e.getChannel().getId());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpResponse response = (HttpResponse) e.getMessage();

        log.info("Channel {} - response received - {}", e.getChannel().getId(), response.getStatus().toString());
        log.debug(response.toString());

        if (HttpHeaders.isKeepAlive(response))
            e.getFuture().addListener(OutboundChannelPool.IDLE);
        else
            e.getFuture().addListener(ChannelFutureListener.CLOSE);

        responseReceived(response);
    }

    private void responseReceived(final HttpResponse response) {
        HttpResponseListener httpResponseListener;
        while ((httpResponseListener = httpResponseListenerQueue.poll()) != null)
            httpResponseListener.responseReceived(response);
    }
}
