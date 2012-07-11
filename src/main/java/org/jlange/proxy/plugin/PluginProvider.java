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
import java.util.List;

import org.jlange.proxy.plugin.predefinedResponse.RequestPolicy;
import org.jlange.proxy.plugin.response.HypertextCompressor;
import org.jlange.proxy.plugin.response.ImageCompressor;
import org.jlange.proxy.plugin.response.ResponseHeaderOptimizer;
import org.jlange.proxy.plugin.response.WeakCacheHeader;
import org.jlange.proxy.util.Config;

public class PluginProvider {

    private final static PluginProvider instance = new PluginProvider();

    public static PluginProvider getInstance() {
        return instance;
    }

    private final List<ResponsePlugin>           responsePlugins;
    private final List<PredefinedResponsePlugin> predefinedResponsePlugins;

    private PluginProvider() {
        responsePlugins = new ArrayList<ResponsePlugin>();

        if (Config.isPluginEnabled(ResponseHeaderOptimizer.class))
            responsePlugins.add(new ResponseHeaderOptimizer());

        if (Config.isPluginEnabled(WeakCacheHeader.class))
            responsePlugins.add(new WeakCacheHeader());

        if (Config.isPluginEnabled(HypertextCompressor.class))
            responsePlugins.add(new HypertextCompressor());

        if (Config.isPluginEnabled(ImageCompressor.class))
            responsePlugins.add(new ImageCompressor());

        predefinedResponsePlugins = new ArrayList<PredefinedResponsePlugin>();

        if (Config.isPluginEnabled(RequestPolicy.class))
            predefinedResponsePlugins.add(new RequestPolicy());
    }

    public List<ResponsePlugin> getResponsePlugins() {
        return responsePlugins;
    }

    public List<PredefinedResponsePlugin> getPredefinedResponsePlugins() {
        return predefinedResponsePlugins;
    }
}
