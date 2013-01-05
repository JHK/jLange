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

import java.net.ConnectException;
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

    public void setResponseListener(final Queue<HttpResponseListener> httpResponseListenerQueue) {
        this.httpResponseListenerQueue = httpResponseListenerQueue;
    }

    public void sendRequest(final ChannelFuture future, final HttpRequest request) {
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) {
                if (future.isSuccess()) {
                    log.info("Channel {} - sending request - {}", future.getChannel().getId(), request.getUri());
                    log.debug("Channel {} - {}", future.getChannel().getId(), request);
                    future.getChannel().write(request);
                } else {
                    log.info("Channel {} - could not send request - {}", future.getChannel().getId(), request.getUri());
                }
            }
        });
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        if (e.getCause() instanceof ConnectException) {
            // connection timed out or remote closed keep-alive connection
            if (httpResponseListenerQueue.isEmpty()) {
                log.info("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
            } else {
                log.warn("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
            }
        } else {
            log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
            log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getStackTrace());
            e.getChannel().close();
        }
        final HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY);
        HttpHeaders.setContentLength(response, response.getContent().readableBytes());
        while (!httpResponseListenerQueue.isEmpty())
            httpResponseListenerQueue.remove().responseReceived(response);
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

        final Boolean isKeepAlive = HttpHeaders.isKeepAlive(response);

        while (!httpResponseListenerQueue.isEmpty())
            httpResponseListenerQueue.remove().responseReceived(response);

        if (isKeepAlive)
            e.getFuture().addListener(OutboundChannelPool.IDLE);
        else
            e.getFuture().addListener(ChannelFutureListener.CLOSE);

    }
}
