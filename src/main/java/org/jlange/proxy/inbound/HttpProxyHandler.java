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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.util.Config;
import org.jlange.proxy.util.HttpHeaders2;
import org.jlange.proxy.util.HttpResponseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyHandler extends AbstractProxyHandler implements ChannelHandler {

    private final static Logger                    log                = LoggerFactory.getLogger(HttpProxyHandler.class);

    /**
     * If the channel is used for HTTP1.1 pipelines the order of sent responses must be the same than we got the requests. The Queue
     * requestPipeline stores the order of the requests and the Map responsePipeline stores the corresponding responses.
     */
    private final Queue<HttpRequest>               requestQueue       = new LinkedList<HttpRequest>();
    private final Map<HttpRequest, ResponseWriter> requestResponseMap = new HashMap<HttpRequest, ResponseWriter>();

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        final HttpRequest request = (HttpRequest) e.getMessage();

        synchronized (requestQueue) {
            requestQueue.add(request);
        }

        // just for logging
        log.info("Channel {} - request received - {}", e.getChannel().getId(), request.getUri());
        log.debug(request.toString());

        super.messageReceived(ctx, e);
    }

    @Override
    protected HttpResponseListener getWriteHttpResponseListener(final HttpRequest request, final Channel channel) {

        return new HttpResponseListener() {

            @Override
            public void responseReceived(final HttpResponse response) {
                log.debug("Channel {} - response received for request {}", channel.getId(), request.getUri());

                ResponseWriter responseWriter = new ResponseWriter(request, channel, response);
                requestResponseMap.put(request, responseWriter);

                // responses must be sent in the same order they were received
                synchronized (requestQueue) {
                    while (requestResponseMap.containsKey(requestQueue.peek())) {
                        requestResponseMap.remove(requestQueue.remove()).write();
                    }
                }
            }
        };
    }

    @Override
    protected HttpResponseListener getProtocolHttpResponseListener(final HttpRequest request) {

        final Boolean proxyKeepAlive = HttpHeaders.getHeader(request, HttpHeaders2.Proxy.CONNECTION, HttpHeaders.Values.CLOSE)
                .toLowerCase().equals(HttpHeaders.Values.KEEP_ALIVE);

        return new HttpResponseListener() {
            @Override
            public void responseReceived(final HttpResponse response) {
                // set protocol version
                HttpHeaders2.setVia(response, response.getProtocolVersion(), Config.VIA_HOSTNAME, Config.VIA_COMMENT);
                response.setProtocolVersion(HttpVersion.HTTP_1_1);

                // Keep alive
                if (proxyKeepAlive != null)
                    HttpHeaders.setKeepAlive(response, proxyKeepAlive);
            }
        };
    }
}
