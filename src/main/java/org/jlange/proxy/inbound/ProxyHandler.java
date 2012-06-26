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
import java.util.LinkedList;
import java.util.List;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.outbound.UserAgent;
import org.jlange.proxy.plugin.PluginProvider;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.jlange.proxy.util.HttpHeaders2;
import org.jlange.proxy.util.HttpResponseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ProxyHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private final Logger    log = LoggerFactory.getLogger(getClass());
    private final UserAgent ua  = new UserAgent();

    /**
     * Builds a {@link HttpResponseListener} to update the responses headers depending on the current protocol status/information
     * 
     * @param request Unchanged @{code HttpRequest}
     * @return a {@link HttpResponseListener} to update the responses headers
     */
    protected abstract HttpResponseListener getProtocolHttpResponseListener(final HttpRequest request);

    /**
     * Builds a {@code HttpResponseListener} to write the response to the given channel respecting the protocols restrictions
     * 
     * @param request a {@link HttpRequest} made to get the response
     * @param channel a {@link Channel} to write the final response on
     * @return a {@link HttpResponseListener} implementing the write strategy
     */
    protected abstract HttpResponseListener getWriteHttpResponseListener(final HttpRequest request, final Channel channel);

    /**
     * Builds a {@link HttpResponseListener} to update the response with all plugins applicable to the request and response
     * 
     * @param request the {@link HttpRequest} to check the applicability against
     * @return a {@link HttpResponseListener} applying all applicable plugins
     */
    protected HttpResponseListener getPluginHttpResponseListener(final HttpRequest request) {
        return new HttpResponseListener() {
            @Override
            public void responseReceived(final HttpResponse response) {
                // apply response plugins
                for (ResponsePlugin plugin : PluginProvider.getInstance().getResponsePlugins(request)) {
                    if (plugin.isApplicable(response)) {
                        log.debug("Using plugin {} - {}", plugin.getClass().getName(), request.getUri());
                        plugin.run(request, response);
                        plugin.updateResponse(response);
                    }
                }
            }
        };
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        final HttpRequest request = (HttpRequest) e.getMessage();
        final Channel inboundChannel = e.getChannel();

        // response plugins and response listener
        // they need to be first one in place, because the response may depend on the original request (like special proxy headers)
        final List<HttpResponseListener> httpResponseListenerList = new LinkedList<HttpResponseListener>();
        // TODO error response handling
        httpResponseListenerList.add(getPluginHttpResponseListener(request));
        httpResponseListenerList.add(getProtocolHttpResponseListener(request));
        httpResponseListenerList.add(getWriteHttpResponseListener(request, inboundChannel));

        // update requests
        request.removeHeader(HttpHeaders2.SPDY.STREAM_ID);
        request.removeHeader(HttpHeaders2.Proxy.CONNECTION);
        updateRequestUri(request);

        // request plugins
        // TODO: implement

        // request to predefined response plugins
        // TODO: implement

        ua.request(request, httpResponseListenerList);
    }

    @Override
    public void channelBound(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Channel {} - created", e.getChannel().getId());
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Channel {} - closed", e.getChannel().getId());
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getStackTrace());
    }

    /**
     * {@link HttpRequest}s to proxies are made in a slightly different schema. The URI of the {@link HttpRequest} may be absolute, whereas
     * the target server requires a relative one. So this function updates the {@link HttpRequest} in a way like the client would make the
     * request to the targeted server directly.
     * 
     * @param request the {@link HttpRequest} to update
     */
    private static void updateRequestUri(final HttpRequest request) {
        if (!request.getUri().toLowerCase().startsWith("http"))
            return;

        try {
            final URL url = new URL(request.getUri());
            final StringBuilder sb = new StringBuilder();
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
