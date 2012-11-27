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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.plugin.PluginProvider;
import org.jlange.proxy.plugin.PredefinedResponsePlugin;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginHandler extends SimpleChannelHandler {

    private final static Map<String, Long> pluginStats = new HashMap<String, Long>();
    private final static Logger            log         = LoggerFactory.getLogger(PluginHandler.class);

    private List<ResponsePlugin>           responsePlugins;
    private HttpRequest                    request;

    public PluginHandler() {
        responsePlugins = new LinkedList<ResponsePlugin>();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpResponse response = (HttpResponse) e.getMessage();
        log.info("Channel {} - Response received", ctx.getChannel().getId());

        // apply response plugins
        for (ResponsePlugin plugin : responsePlugins) {
            if (!plugin.isApplicable(response))
                continue;
            long start = System.currentTimeMillis();
            plugin.run(request, response);
            logStats(plugin, start);
        }

        super.messageReceived(ctx, e);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest) e.getMessage();
        log.info("Channel {} - Request received {}", ctx.getChannel().getId(), request.getHeader(HttpHeaders.Names.HOST) + request.getUri());

        // apply request to predefined response plugins
        HttpResponse response;
        for (PredefinedResponsePlugin plugin : PluginProvider.getInstance().getPredefinedResponsePlugins()) {
            long start = System.currentTimeMillis();
            if ((response = plugin.getPredefinedResponse(request)) != null) {
                logStats(plugin, start);
                log.info("Channel {} - using predefined response plugin {}", ctx.getChannel().getId(), plugin.getClass().getName());
                Channels.fireMessageReceived(ctx.getChannel(), response);
                return;
            } else
                logStats(plugin, start);
        }

        // TODO: apply request plugins

        // send upstream as fast as possible
        super.writeRequested(ctx, e);

        // cleanup
        this.request = request;
        responsePlugins.clear();

        // set response plugins
        for (ResponsePlugin plugin : PluginProvider.getInstance().getResponsePlugins())
            if (plugin.isApplicable(request)) {
                log.debug("Channel {} - Using plugin {}", ctx.getChannel().getId(), plugin.getClass().getName());
                responsePlugins.add(plugin);
            } else {
                log.debug("Channel {} - Not using plugin {}", ctx.getChannel().getId(), plugin.getClass().getName());
            }
    }

    private void logStats(Object plugin, long start) {
        String pluginName = plugin.getClass().getName();
        long pluginDuration = System.currentTimeMillis() - start;
        log.info("Plugin {} took {} ms", pluginName, pluginDuration);

        if (log.isDebugEnabled()) {
            synchronized (pluginStats) {
                Long totalPluginDuration = pluginStats.get(pluginName);
                if (totalPluginDuration == null)
                    totalPluginDuration = new Long(0);
                pluginStats.put(pluginName, pluginDuration + totalPluginDuration);
            }
            log.debug("Total plugin durations: {}", pluginStats);
        }
    }
}
