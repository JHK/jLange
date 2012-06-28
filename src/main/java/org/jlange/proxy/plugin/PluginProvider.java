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
package org.jlange.proxy.plugin;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jlange.proxy.plugin.response.Compressor;
import org.jlange.proxy.plugin.response.ImageCompressor;
import org.jlange.proxy.plugin.response.ResponseHeaderOptimizer;
import org.jlange.proxy.plugin.response.WeakCacheHeader;

public class PluginProvider {

    private final static PluginProvider instance = new PluginProvider();

    public static PluginProvider getInstance() {
        return instance;
    }

    private final List<ResponsePlugin> plugins;

    private PluginProvider() {
        plugins = new ArrayList<ResponsePlugin>();

        plugins.add(new ResponseHeaderOptimizer());
        plugins.add(new WeakCacheHeader());
        plugins.add(new Compressor());
        plugins.add(new ImageCompressor());
    }

    public List<ResponsePlugin> getResponsePlugins() {
        return plugins;
    }

    public List<ResponsePlugin> getResponsePlugins(final HttpRequest request) {
        List<ResponsePlugin> plugins = new LinkedList<ResponsePlugin>();

        for (ResponsePlugin plugin : this.plugins)
            if (plugin.isApplicable(request))
                plugins.add(plugin);

        return plugins;
    }
}
