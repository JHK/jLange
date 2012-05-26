package org.jlange.proxy.plugin;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.plugin.response.Compressor;
import org.jlange.proxy.plugin.response.ResponseHeaderOptimizer;

public class PluginProvider {

    private final static PluginProvider instance = new PluginProvider();

    public static PluginProvider getInstance() {
        return instance;
    }

    private final List<ResponsePlugin> plugins;

    private PluginProvider() {
        plugins = new ArrayList<ResponsePlugin>();

        plugins.add(new Compressor());
        plugins.add(new ResponseHeaderOptimizer());
    }

    public List<ResponsePlugin> getResponsePlugins() {
        return plugins;
    }

    public List<ResponsePlugin> getResponsePlugins(final HttpRequest request) {
        for (ResponsePlugin plugin : plugins)
            if (!plugin.isApplicable(request))
                plugins.remove(plugin);

        return plugins;
    }

    public List<ResponsePlugin> getResponsePlugins(final HttpRequest request, final HttpResponse response) {
        final List<ResponsePlugin> plugins = getResponsePlugins(request);

        for (ResponsePlugin plugin : plugins)
            if (!plugin.isApplicable(response))
                plugins.remove(plugin);

        return plugins;
    }
}
