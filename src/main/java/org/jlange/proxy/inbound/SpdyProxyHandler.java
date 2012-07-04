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
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.util.HttpHeaders2;
import org.jlange.proxy.util.HttpResponseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpdyProxyHandler extends AbstractProxyHandler implements ChannelHandler {

    private final static Logger         log           = LoggerFactory.getLogger(SpdyProxyHandler.class);

    private final Queue<ResponseWriter> responseQueue = new LinkedList<ResponseWriter>();
    private Boolean                     isWriting     = false;

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        final HttpRequest request = (HttpRequest) e.getMessage();

        // just for logging
        log.info("Channel {} - Stream {} - request received - {}{}", new Object[] { e.getChannel().getId(), getSpdyStreamId(request),
                HttpHeaders.getHost(request), request.getUri() });
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

    @Override
    protected HttpResponseListener getWriteHttpResponseListener(final HttpRequest request, final Channel channel) {
        return new HttpResponseListener() {
            @Override
            public void responseReceived(final HttpResponse response) {
                log.debug("Channel {} - Stream {} - response received for request {}", new Object[] { channel.getId(),
                        getSpdyStreamId(response), request.getUri() });

                ResponseWriter responseWriter = new ResponseWriter(request, channel, response);

                synchronized (responseQueue) {
                    responseQueue.add(responseWriter);

                    if (!isWriting) {
                        isWriting = true;
                        responseWriter.write();
                    }
                }
            }
        };
    }

    @Override
    public void writeComplete(final ChannelHandlerContext ctx, final WriteCompletionEvent e) {
        synchronized (responseQueue) {
            // cleanup finished request
            responseQueue.remove();

            if (!responseQueue.isEmpty())
                responseQueue.peek().write();
            else
                isWriting = false;
        }
    }

    private static Integer getSpdyStreamId(final HttpMessage message) {
        final Integer spdyStreamId = HttpHeaders.getIntHeader(message, HttpHeaders2.SPDY.STREAM_ID, -1);
        return spdyStreamId;
    }
}
