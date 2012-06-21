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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.outbound.HttpPipelineFactory;
import org.jlange.proxy.outbound.HttpPluginResponseHandler;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.jlange.proxy.plugin.PluginProvider;
import org.jlange.proxy.util.HttpResponseListener;
import org.jlange.proxy.util.RemoteAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private final static String PROXY_CONNECTION = "Proxy-Connection";
    private final static String KEEP_ALIVE       = "keep-alive";
    private final static String SPDY_STREAM_ID   = "X-SPDY-Stream-ID";
    private final static String SPDY_STREAM_PRIO = "X-SPDY-Stream-Priority";
    private final static String HTTP_SCHEMA      = "http";

    private final Logger        log              = LoggerFactory.getLogger(getClass());

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        log.warn("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        if (!e.getCause().getClass().equals(IOException.class)) {
            e.getCause().printStackTrace();
        }
    }

    @Override
    public void channelBound(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Channel {} - created", e.getChannel().getId());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpRequest request = (HttpRequest) e.getMessage();

        // just for logging
        final String channelId = getLogChannelId(request, e.getChannel());
        log.info("Channel {} - request received - {}", channelId, request.getUri());
        log.debug(request.toString());

        // request to predefined response plugins
        // TODO: implement

        final ChannelPipelineFactory factory = new HttpPipelineFactory();
        final RemoteAddress address = RemoteAddress.parseRequest(request);
        final ChannelFuture outboundFuture = OutboundChannelPool.getInstance().getIdleOrNewChannelFuture(address, factory);
        log.debug("Channel {} - using outboundchannel {}", channelId, outboundFuture.getChannel().getId());

        // set actions when response arrives
        final HttpPluginResponseHandler outboundHandler = outboundFuture.getChannel().getPipeline().get(HttpPluginResponseHandler.class);
        outboundHandler.setResponsePlugins(PluginProvider.getInstance().getResponsePlugins(request));
        outboundHandler.addResponseListener(new ProxyPipelineResponseListener(request, e.getChannel()));

        // perform request on outbound channel
        request.removeHeader(SPDY_STREAM_ID);
        request.removeHeader(PROXY_CONNECTION);
        if (request.getUri().startsWith(HTTP_SCHEMA))
            updateRequestUri(request);
        outboundHandler.sendRequest(outboundFuture, request);
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Channel {} - closed", e.getChannel().getId());
    }

    private void updateRequestUri(final HttpRequest request) {
        try {
            final URL url = new URL(request.getUri());
            final StringBuilder sb = new StringBuilder();
            sb.append(url.getPath());
            if (url.getQuery() != null)
                sb.append("?").append(url.getQuery());
            request.setUri(sb.toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private String getLogChannelId(final HttpRequest request, final Channel channel) {
        String channelId = null;
        if (log.isInfoEnabled()) {
            channelId = channel.getId().toString();
            final String spdyStreamId = HttpHeaders.getHeader(request, SPDY_STREAM_ID);
            if (spdyStreamId != null)
                channelId += "+" + spdyStreamId;
        }
        return channelId;
    }

    private class ProxyResponseListener implements HttpResponseListener {

        private final String  channelId;
        private final String  spdyStreamId;
        private final Channel channel;
        private final Boolean proxyKeepAlive;

        public ProxyResponseListener(final HttpRequest request, final Channel channel) {
            channelId = getLogChannelId(request, channel);
            spdyStreamId = HttpHeaders.getHeader(request, SPDY_STREAM_ID);
            proxyKeepAlive = HttpHeaders.getHeader(request, PROXY_CONNECTION, KEEP_ALIVE).toLowerCase().equals(KEEP_ALIVE);
            this.channel = channel;
        }

        @Override
        public void responseReceived(final HttpResponse response) {
            // SPDY
            if (spdyStreamId != null) {
                response.setHeader(SPDY_STREAM_ID, spdyStreamId);
                response.setHeader(SPDY_STREAM_PRIO, 0);
            }

            // HTTP Version for proxy
            response.setProtocolVersion(HttpVersion.HTTP_1_1);

            // Keep alive
            HttpHeaders.setKeepAlive(response, proxyKeepAlive);

            log.info("Channel {} - sending response - {}", channelId, response.getStatus());
            log.debug(response.toString());

            if (channel.isConnected())
                channel.write(response);
            else
                // this happens when the browser closes the channel before a response was written, e.g. stop loading the page
                log.info("Channel {} - try to send response to closed channel - skipped", channelId);
        }
    }

    /**
     * If the channel is used for HTTP1.1 pipelines we have to keep track of the order of sent responses on a channel
     */
    private final Queue<HttpRequest>             requestPipeline  = new LinkedList<HttpRequest>();
    private final Map<HttpRequest, HttpResponse> responsePipeline = new HashMap<HttpRequest, HttpResponse>();

    private class ProxyPipelineResponseListener implements HttpResponseListener {

        private final Channel     channel;
        private final HttpRequest request;

        public ProxyPipelineResponseListener(final HttpRequest request, final Channel channel) {
            this.request = request;
            this.channel = channel;
            requestPipeline.add(request);
        }

        @Override
        public synchronized void responseReceived(final HttpResponse response) {
            // modify response headers
            response.setProtocolVersion(HttpVersion.HTTP_1_1);
            HttpHeaders.setKeepAlive(response, true);

            responsePipeline.put(request, response);

            if (request.equals(requestPipeline.peek()))
                sendResponse(request);
        }

        private void sendResponse(final HttpRequest request) {
            HttpResponse response = responsePipeline.get(request);

            if (request == null || response == null)
                return;

            log.info("Channel {} - sending response - {} for " + request.getUri(), channel.getId(), response.getStatus());
            log.debug(response.toString());

            // the received response matches to the first request in queue, start writing
            responsePipeline.remove(requestPipeline.poll());
            channel.write(response).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) {
                    if (future.isSuccess())
                        sendResponse(requestPipeline.peek());
                }
            });

        }
    }
}
