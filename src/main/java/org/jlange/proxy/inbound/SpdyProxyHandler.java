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

import java.util.LinkedList;
import java.util.Queue;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.util.HttpHeaders2;
import org.jlange.proxy.util.HttpResponseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpdyProxyHandler extends ProxyHandler implements ChannelHandler {

    private final Logger              log           = LoggerFactory.getLogger(getClass());
    private final Queue<HttpResponse> responseQueue = new LinkedList<HttpResponse>();

    @Override
    protected HttpResponseListener getWriteHttpResponseListener(final HttpRequest request, final Channel channel) {

        return new HttpResponseListener() {

            @Override
            public void responseReceived(final HttpResponse response) {
                log.debug("Stream {} - response received for request {}", getSpdyStreamId(response), request.getUri());

                synchronized (responseQueue) {
                    responseQueue.add(response);
                    if (responseQueue.element().equals(response))
                        sendResponse(channel, response);
                }
            }
        };
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpRequest request = (HttpRequest) e.getMessage();

        // just for logging
        log.debug("Channel {} - Stream {}", e.getChannel().getId(), getSpdyStreamId(request));
        log.info("Stream {} - request received - {}", getSpdyStreamId(request), request.getUri());
        log.debug(request.toString());

        super.messageReceived(ctx, e);
    }

    @Override
    protected HttpResponseListener getProtocolHttpResponseListener(final HttpRequest request) {

        final Integer spdyStreamId = getSpdyStreamId(request);

        return new HttpResponseListener() {
            @Override
            public void responseReceived(final HttpResponse response) {
                // Protocol Version
                response.setProtocolVersion(HttpVersion.HTTP_1_1);

                // SPDY
                response.setHeader(HttpHeaders2.SPDY.STREAM_ID, spdyStreamId);
                response.setHeader(HttpHeaders2.SPDY.STREAM_PRIO, 0);
                response.removeHeader(HttpHeaders.Names.CONNECTION);
                response.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
            }
        };
    }

    private static Integer getSpdyStreamId(final HttpMessage message) {
        final Integer spdyStreamId = HttpHeaders.getIntHeader(message, HttpHeaders2.SPDY.STREAM_ID, -1);
        if (spdyStreamId == -1 || spdyStreamId == null)
            LoggerFactory.getLogger(SpdyProxyHandler.class).warn("Found invalid http message:\n{}", message);
        return spdyStreamId;
    }

    private void sendResponse(final Channel channel, final HttpResponse response) {
        log.info("Stream {} - sending response - {}", getSpdyStreamId(response), response.getStatus());
        log.debug(response.toString());

        if (!channel.isConnected()) {
            // this happens when the browser closes the channel before a response was written, e.g. stop loading the page
            log.info("Stream {} - try to send response to closed channel - skipped", getSpdyStreamId(response));
            synchronized (responseQueue) {
                responseQueue.remove(response);
                if (!responseQueue.isEmpty())
                    sendResponse(channel, responseQueue.element());
            }
            return;
        }

        if (getSpdyStreamId(response) == -1) {
            // FIXME why does this happen?
            log.warn("Stream {} - invalid response!\n{}", getSpdyStreamId(response), response);
            synchronized (responseQueue) {
                responseQueue.remove(response);
                if (!responseQueue.isEmpty())
                    sendResponse(channel, responseQueue.element());
            }
            return;
        }

        ChannelFuture future = channel.write(response);
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) {
                synchronized (responseQueue) {
                    responseQueue.remove(response);
                    if (!responseQueue.isEmpty())
                        sendResponse(channel, responseQueue.element());
                }
            }
        });
    }
}
