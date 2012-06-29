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
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.util.HttpHeaders2;
import org.jlange.proxy.util.HttpResponseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyHandler extends ProxyHandler implements ChannelHandler {

    private final Logger                         log             = LoggerFactory.getLogger(getClass());

    /**
     * If the channel is used for HTTP1.1 pipelines the order of sent responses must be the same than we got the requests. The Queue
     * requestPipeline stores the order of the requests and the Map responsePipeline stores the corresponding responses.
     */
    private final Queue<HttpRequest>             requestPipeline = new LinkedList<HttpRequest>();
    private final Map<HttpRequest, HttpResponse> responseMap     = new HashMap<HttpRequest, HttpResponse>();

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        final HttpRequest request = (HttpRequest) e.getMessage();

        synchronized (requestPipeline) {
            requestPipeline.add(request);
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
            public synchronized void responseReceived(final HttpResponse response) {
                log.debug("Channel {} - response received for request {}", channel.getId(), request.getUri());

                // we try to synchronize threads with responses. This allows more code to run in parallel
                synchronized (responseMap) {
                    responseMap.put(request, response);
                }

                sendResponse(requestPipeline.peek());
            }

            private void sendResponse(final HttpRequest request) {
                synchronized (requestPipeline) {
                    // responses must be sent in the same order they were received
                    if (request == null || !request.equals(requestPipeline.peek()))
                        return;
                }

                final HttpResponse response;
                synchronized (responseMap) {
                    // response must be available to send it
                    if (responseMap.containsKey(request))
                        response = responseMap.remove(request);
                    else
                        return;
                }

                log.info("Channel {} - sending response - {} for {}{}",
                        new Object[] { channel.getId(), response.getStatus(), HttpHeaders.getHost(request), request.getUri() });
                log.debug(response.toString());

                if (!channel.isConnected()) {
                    // this happens when the browser closes the channel before a response was written, e.g. stop loading the page
                    log.info("Channel {} - try to send response to closed channel - skipped", channel.getId());
                    synchronized (requestPipeline) {
                        synchronized (responseMap) {
                            responseMap.remove(requestPipeline.remove(request));
                        }
                    }
                    sendResponse(requestPipeline.peek());
                    return;
                }

                channel.write(response).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(final ChannelFuture future) {
                        synchronized (requestPipeline) {
                            // the response to this request was written completely
                            requestPipeline.remove(request);
                        }

                        sendResponse(requestPipeline.peek());
                    }
                });
            }
        };
    }

    @Override
    protected HttpResponseListener getProtocolHttpResponseListener(final HttpRequest request) {

        final Boolean proxyKeepAlive = HttpHeaders.getHeader(request, HttpHeaders2.Proxy.CONNECTION).equals(HttpHeaders.Values.KEEP_ALIVE);

        return new HttpResponseListener() {
            @Override
            public void responseReceived(final HttpResponse response) {
                // set protocol version
                response.setProtocolVersion(HttpVersion.HTTP_1_1);

                // Keep alive
                if (proxyKeepAlive != null)
                    HttpHeaders.setKeepAlive(response, proxyKeepAlive);
            }
        };
    }
}
