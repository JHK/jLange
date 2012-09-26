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

import org.jlange.proxy.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginProvider {

    private final static Logger         log      = LoggerFactory.getLogger(PluginProvider.class);
    private final static PluginProvider instance = new PluginProvider();

    public static PluginProvider getInstance() {
        return instance;
    }

    private final List<ResponsePlugin>           responsePlugins;
    private final List<PredefinedResponsePlugin> predefinedResponsePlugins;

    private PluginProvider() {
        responsePlugins = new ArrayList<ResponsePlugin>();

        for (String plugin : Config.PLUGINS_RESPONSE) {
            try {
                Class<?> className = Class.forName(plugin);
                responsePlugins.add((ResponsePlugin) className.newInstance());
                log.info("Plugin loaded: {}", plugin);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                log.error("Could not load plugin: {}", e.getMessage());
            }
        }

        predefinedResponsePlugins = new ArrayList<PredefinedResponsePlugin>();

        for (String plugin : Config.PLUGINS_PREDEFINED) {
            try {
                Class<?> className = Class.forName(plugin);
                predefinedResponsePlugins.add((PredefinedResponsePlugin) className.newInstance());
                log.info("Plugin loaded: {}", plugin);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                log.error("Could not load plugin: {}", e.getMessage());
            }
        }
    }

    public List<ResponsePlugin> getResponsePlugins() {
        return responsePlugins;
    }

    public List<PredefinedResponsePlugin> getPredefinedResponsePlugins() {
        return predefinedResponsePlugins;
    }
}
