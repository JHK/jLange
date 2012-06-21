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

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpPluginResponseHandler extends HttpResponseHandler {

    private final Logger         log = LoggerFactory.getLogger(getClass());
    private List<ResponsePlugin> responsePlugins;

    @Override
    public void sendRequest(final ChannelFuture future, final HttpRequest request) {
        // request plugins
        // TODO: implement
        request.setProtocolVersion(HttpVersion.HTTP_1_1);
        HttpHeaders.setKeepAlive(request, true);

        super.sendRequest(future, request);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        final HttpResponse response = (HttpResponse) e.getMessage();

        // apply plugins
        for (ResponsePlugin plugin : getResponsePlugins()) {
            if (plugin.isApplicable(response)) {
                log.info("Channel {} - using plugin {}", e.getChannel().getId(), plugin.getClass().getName());
                plugin.run(response);
                plugin.updateResponse(response);
            }
        }

        super.messageReceived(ctx, e);
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

}
