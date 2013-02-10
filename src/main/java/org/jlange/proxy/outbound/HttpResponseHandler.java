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

import java.io.IOException;
import java.net.ConnectException;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.outbound.UserAgent.HttpResponseListener;

public class HttpResponseHandler extends SimpleChannelUpstreamHandler {

    private HttpResponseListener httpResponseListener;
    private Boolean              isKeepAlive;

    public void setResponseListener(HttpResponseListener httpResponseListener) {
        this.httpResponseListener = httpResponseListener;
    }

    public void sendRequest(ChannelFuture future, final HttpRequest request) {
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) {
                if (future.isSuccess())
                    future.getChannel().write(request);
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY);
        HttpHeaders.setContentLength(response, response.getContent().readableBytes());
        if (httpResponseListener != null) {
            httpResponseListener.responseReceived(response);
            httpResponseListener = null;
        }
        ctx.getChannel().close();

        // connection timed out or remote closed keep-alive connection
        if (e.getCause() instanceof ConnectException)
            return;
        // another way for remote closed keep-alive connection
        if (e.getCause() instanceof IOException)
            return;

        super.exceptionCaught(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        // return if there is no one to notify
        if (httpResponseListener == null)
            return;

        if (e.getMessage() instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) e.getMessage();
            isKeepAlive = HttpHeaders.isKeepAlive(response);
            httpResponseListener.responseReceived(response);
            if (!response.isChunked()) {
                httpResponseListener = null;
                finalizeChannelFuture(e.getFuture());
            }
        } else if (e.getMessage() instanceof HttpChunk) {
            HttpChunk chunk = (HttpChunk) e.getMessage();
            httpResponseListener.chunkReceived(chunk);
            if (chunk.isLast()) {
                httpResponseListener = null;
                finalizeChannelFuture(e.getFuture());
            }
        } else
            throw new IllegalStateException(e.getMessage().getClass().toString());
    }

    private void finalizeChannelFuture(ChannelFuture future) {
        if (isKeepAlive)
            future.addListener(OutboundChannelPool.IDLE);
        else
            future.addListener(ChannelFutureListener.CLOSE);
    }
}
