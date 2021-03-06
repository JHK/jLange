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

import java.net.MalformedURLException;
import java.net.URL;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.outbound.OutboundChannelPool.ChannelPipelineFactoryBuilder;
import org.jlange.proxy.util.Config;
import org.jlange.proxy.util.HttpProxyHeaders;
import org.jlange.proxy.util.RemoteAddress;

public class UserAgent {

    public interface HttpResponseListener {

        public void responseReceived(final HttpResponse response);

        public void chunkReceived(final HttpChunk chunk);

    }

    private RemoteAddress proxy;

    public UserAgent() {
        proxy = Config.PROXY_CHAIN;
    }

    public void request(final HttpRequest request, final HttpResponseListener responseListener) throws MalformedURLException {
        final RemoteAddress address;
        if (proxy != null) {
            address = proxy;
            updateProxyRequest(request);
        } else {
            address = RemoteAddress.parseRequest(request);
            updateRequest(request);
        }

        // build channel pipeline if we need to create a new channel
        final ChannelPipelineFactoryBuilder builder = new ChannelPipelineFactoryBuilder() {
            @Override
            public ChannelPipelineFactory getChannelPipelineFactory() {
                return new HttpPipelineFactory();
            }
        };

        // things to do when we got a ChannelFuture finally
        ChannelFutureListener listener = new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) {
                // set actions when response arrives
                final HttpResponseHandler outboundHandler = future.getChannel().getPipeline().get(HttpResponseHandler.class);
                outboundHandler.setResponseListener(responseListener);

                // perform request on outbound channel
                outboundHandler.sendRequest(future, request);
            }
        };

        // initiate
        OutboundChannelPool.getInstance().getChannelFuture(address, builder, listener);
    }

    private void updateProxyRequest(final HttpRequest request) throws MalformedURLException {
        request.setProtocolVersion(HttpVersion.HTTP_1_1);
        request.removeHeader(HttpHeaders.Names.CONNECTION);
        request.setHeader(HttpProxyHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);

        if (!request.getUri().toLowerCase().startsWith("http")) {
            RemoteAddress address = RemoteAddress.parseRequest(request);
            String schema = address.getPort() == 443 ? "https" : "http";
            URL url = new URL(schema, address.getHost(), address.getPort(), request.getUri());

            request.setUri(url.toString());
            request.setHeader(HttpHeaders.Names.HOST, address.toString());
        }
    }

    private void updateRequest(final HttpRequest request) {
        request.setProtocolVersion(HttpVersion.HTTP_1_1);
        request.removeHeader(HttpHeaders.Names.CONNECTION);
    }
}
