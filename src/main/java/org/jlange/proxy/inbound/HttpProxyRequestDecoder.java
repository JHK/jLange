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

import java.net.MalformedURLException;
import java.net.URL;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * {@link HttpRequest}s send to proxies look slightly different than direct requests to webserver. This hander takes care of these
 * differences and reforms the {@link HttpRequest} to look like a direct connection to a webserver. Additionally this handler takes care of
 * {@code Proxy-Connection} {@link HttpHeader} in the {@link HttpRequest} and sets the respective {@link HttpHeader} of the
 * {@link HttpResponse}.
 */
public class HttpProxyRequestDecoder extends SimpleChannelHandler implements ChannelHandler {

    private final static String PROXY_CONNECTION = "Proxy-Connection";
    private final static String KEEP_ALIVE       = "keep-alive";
    private Boolean             proxyKeepAlive;

    public HttpProxyRequestDecoder() {
        proxyKeepAlive = true;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest) e.getMessage();
        proxyKeepAlive = HttpHeaders.getHeader(request, PROXY_CONNECTION, KEEP_ALIVE).toLowerCase().equals(KEEP_ALIVE);
        request.removeHeader(PROXY_CONNECTION);
        updateRequest(request);
        ctx.sendUpstream(e);
    }

    @Override
    public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        if (e.getMessage() instanceof HttpResponse)
            HttpHeaders.setKeepAlive((HttpResponse) e.getMessage(), proxyKeepAlive);
        super.writeRequested(ctx, e);
    }

    private void updateRequest(final HttpRequest request) {
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
}
