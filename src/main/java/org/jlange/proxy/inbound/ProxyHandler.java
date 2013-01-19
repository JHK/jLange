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

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.outbound.UserAgent;
import org.jlange.proxy.outbound.UserAgent.HttpResponseListener;
import org.jlange.proxy.util.Config;
import org.jlange.proxy.util.HttpProxyHeaders;

public class ProxyHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private UserAgent ua;

    public ProxyHandler() {
        ua = new UserAgent();
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        final RequestResponse requestResponse = (RequestResponse) e.getMessage();

        // update request
        final HttpRequest request = requestResponse.removeRequest();
        updateRequestUri(request);
        HttpProxyHeaders.setVia(request, Config.VIA_HOSTNAME, Config.VIA_COMMENT);
        HttpProxyHeaders.setForwardedFor(request, ctx.getChannel().getRemoteAddress());
        final boolean keepAlive = getAndCleanProxyKeepAlive(request);

        ua.request(request, new HttpResponseListener() {
            @Override
            public void responseReceived(HttpResponse response) {
                requestResponse.setResponse(updateResponse(response, keepAlive));
                if (ctx.getChannel().isConnected())
                    ctx.getChannel().write(requestResponse);
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (e.getCause() instanceof IOException) {
            // swallow remote connection resets
        } else {
            super.exceptionCaught(ctx, e);
        }
    }

    /*
     * {@link HttpRequest}s to proxies are made in a slightly different schema. The URI of the {@link HttpRequest} may be absolute, whereas
     * the target server requires a relative one. So this function updates the {@link HttpRequest} in a way like the client would make the
     * request to the targeted server directly.
     */
    private void updateRequestUri(HttpRequest request) {
        if (request.getUri().toLowerCase().startsWith("http")) {
            try {
                final URL url = new URL(request.getUri());
                final StringBuilder sb = new StringBuilder();
                final String path = url.getPath();
                if (path.equals(""))
                    sb.append("/");
                else
                    sb.append(url.getPath());
                if (url.getQuery() != null)
                    sb.append("?").append(url.getQuery());
                request.setUri(sb.toString());

                //TODO: check if header 'host' needs to get updated
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }

    private HttpResponse updateResponse(HttpResponse response, boolean keepAlive) {
        response.setProtocolVersion(HttpVersion.HTTP_1_1);
        response.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
        HttpProxyHeaders.setVia(response, Config.VIA_HOSTNAME, Config.VIA_COMMENT);
        HttpHeaders.setKeepAlive(response, keepAlive);
        return response;
    }

    private boolean getAndCleanProxyKeepAlive(HttpRequest request) {
        String value = HttpHeaders.getHeader(request, HttpProxyHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE).toLowerCase();
        request.removeHeader(HttpProxyHeaders.Names.CONNECTION);
        return value.equals(HttpHeaders.Values.KEEP_ALIVE);
    }
}
