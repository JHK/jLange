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

import java.util.LinkedList;
import java.util.List;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.Tools;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.jlange.proxy.util.HttpResponseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpPluginHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private final Logger         log = LoggerFactory.getLogger(getClass());
    private HttpResponseListener httpResponseListener;
    private List<ResponsePlugin> responsePlugins;

    public void setResponseListener(final HttpResponseListener httpResponseListener) {
        this.httpResponseListener = httpResponseListener;
    }

    public HttpResponseListener getHttpResponseListener() {
        if (httpResponseListener != null)
            return httpResponseListener;
        else
            return new HttpResponseListener() {
                @Override
                public void responseReceived(HttpResponse response) {}
            };
    }

    public void setResponsePlugins(final List<ResponsePlugin> responsePlugins) {
        this.responsePlugins = responsePlugins;
    }

    public List<ResponsePlugin> getResponsePlugins() {
        if (responsePlugins != null)
            return responsePlugins;
        else
            return new LinkedList<ResponsePlugin>();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        Tools.closeOnFlush(e.getChannel());

        getHttpResponseListener().responseReceived(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY));
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
        log.info("Channel {} - closed", e.getChannel().getId());
        OutboundChannelPool.getInstance().closeChannel(e.getChannel().getId());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpResponse response = (HttpResponse) e.getMessage();

        log.info("Channel {} - response received - {}", e.getChannel().getId(), response.getStatus().toString());
        log.debug(response.toString());

        Boolean isKeepAlive = HttpHeaders.isKeepAlive(response);

        // apply plugins
        for (ResponsePlugin plugin : getResponsePlugins()) {
            if (plugin.isApplicable(response)) {
                log.info("Channel {} - using plugin {}", e.getChannel().getId(), plugin.getClass().getName());
                plugin.run(response);
                plugin.updateResponse(response);
            }
        }

        getHttpResponseListener().responseReceived(response);

        if (isKeepAlive)
            OutboundChannelPool.getInstance().setChannelIdle(e.getFuture());
        else
            Tools.closeOnFlush(e.getChannel());
    }
}
