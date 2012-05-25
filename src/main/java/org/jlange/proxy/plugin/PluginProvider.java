package org.jlange.proxy.plugin;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.plugin.response.Compressor;
import org.jlange.proxy.plugin.response.ResponseHeaderOptimizer;

public class PluginProvider {

    public static List<ResponsePlugin> getResponsePlugins() {
        final List<ResponsePlugin> plugins = new ArrayList<ResponsePlugin>();

        plugins.add(new Compressor());
        plugins.add(new ResponseHeaderOptimizer());

        return plugins;
    }

    public static List<ResponsePlugin> getResponsePlugins(final HttpRequest request) {
        final List<ResponsePlugin> plugins = getResponsePlugins();

        for (ResponsePlugin plugin : plugins)
            if (!plugin.isApplicable(request))
                plugins.remove(plugin);

        return plugins;
    }

    public static List<ResponsePlugin> getResponsePlugins(final List<ResponsePlugin> filteredPlugins, final HttpResponse response) {
        final List<ResponsePlugin> plugins = filteredPlugins;

        for (ResponsePlugin plugin : plugins)
            if (!plugin.isApplicable(response))
                plugins.remove(plugin);

        return plugins;
    }
}
