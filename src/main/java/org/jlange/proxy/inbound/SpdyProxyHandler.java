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

import java.io.IOException;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.util.HttpHeaders2;
import org.jlange.proxy.util.HttpResponseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpdyProxyHandler extends ProxyHandler implements ChannelHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    protected HttpResponseListener getHttpResponseListener(final HttpRequest request, final Channel channel) {
        return new ProxyResponseListener(request, channel);
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpRequest request = (HttpRequest) e.getMessage();

        // just for logging
        log.info("Channel {} - request received - {}", getLogChannelId(request, e.getChannel()), request.getUri());
        log.debug(request.toString());

        super.messageReceived(ctx, e);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        if (e.getCause() instanceof IOException) {
            log.warn("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        } else {
            super.exceptionCaught(ctx, e);
        }
    }

    private String getLogChannelId(final HttpRequest request, final Channel channel) {
        String channelId = null;
        if (log.isInfoEnabled()) {
            channelId = channel.getId().toString();
            final String spdyStreamId = HttpHeaders.getHeader(request, HttpHeaders2.SPDY.STREAM_ID);
            if (spdyStreamId != null)
                channelId += "+" + spdyStreamId;
        }
        return channelId;
    }

    private class ProxyResponseListener implements HttpResponseListener {

        private final String  channelId;
        private final String  spdyStreamId;
        private final Channel channel;

        public ProxyResponseListener(final HttpRequest request, final Channel channel) {
            channelId = getLogChannelId(request, channel);
            spdyStreamId = HttpHeaders.getHeader(request, HttpHeaders2.SPDY.STREAM_ID);
            this.channel = channel;
        }

        @Override
        public void responseReceived(final HttpResponse response) {
            // Protocol Version
            response.setProtocolVersion(HttpVersion.HTTP_1_1);

            // SPDY
            response.setHeader(HttpHeaders2.SPDY.STREAM_ID, spdyStreamId);
            response.setHeader(HttpHeaders2.SPDY.STREAM_PRIO, 0);

            log.info("Channel {} - sending response - {}", channelId, response.getStatus());
            log.debug(response.toString());

            if (channel.isConnected())
                channel.write(response);
            else
                // this happens when the browser closes the channel before a response was written, e.g. stop loading the page
                log.info("Channel {} - try to send response to closed channel - skipped", channelId);
        }
    }
}
