package org.jlange.proxy.inbound.strategy;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jlange.proxy.outbound.HttpHandler;
import org.jlange.proxy.outbound.HttpPipelineFactory;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.jlange.proxy.plugin.PluginProvider;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpToHttp implements ProxyStrategy {

    private final Logger              log = LoggerFactory.getLogger(getClass());
    private final Channel             inboundChannel;
    private final OutboundChannelPool outboundChannelPool;
    private final HttpRequest         request;

    public HttpToHttp(final Channel inboundChannel, final HttpRequest request, final OutboundChannelPool outboundChannelPool) {
        this.inboundChannel = inboundChannel;
        this.request = request;
        this.outboundChannelPool = outboundChannelPool;
    }

    private ChannelHandler getHandler() {
        List<ResponsePlugin> responsePlugins = PluginProvider.getInstance().getResponsePlugins(request);

        HttpHandler handler = new HttpHandler(inboundChannel, responsePlugins);
        handler.addMessageReceivedListener(outboundChannelPool.getConnectionIdleFutureListener());

        return handler;
    }

    public ChannelPipelineFactory getChannelPipelineFactory() {
        return new HttpPipelineFactory(getHandler());
    }

    public ChannelFutureListener getChannelActionListener() {
        return new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {

                // update request
                HttpHeaders.setKeepAlive(request, true);

                try {
                    final StringBuilder sb = new StringBuilder();
                    final URL url = new URL(request.getUri());
                    sb.append(url.getPath());
                    if (url.getQuery() != null)
                        sb.append("?").append(url.getQuery());
                    request.setUri(sb.toString());
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }

                log.info("Outboundchannel {} - sending request - {}", future.getChannel().getId(), request.getUri());
                log.debug("{}", request.toString());
                future.getChannel().write(request);
            }
        };
    }
}
