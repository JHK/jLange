package org.jlange.proxy.strategy;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.outbound.HttpHandler;
import org.jlange.proxy.outbound.HttpPipelineFactory;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.jlange.proxy.plugin.PluginProvider;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.jlange.proxy.util.RemoteAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpToHttp implements ProxyStrategy {

    private final Logger         log = LoggerFactory.getLogger(getClass());
    private final Channel        inboundChannel;
    private Channel              outboundChannel;
    private List<ResponsePlugin> responsePlugins;

    public HttpToHttp(final Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
        this.responsePlugins = new LinkedList<ResponsePlugin>();
    }
    
    public ChannelFuture getOutboundChannelFuture(final RemoteAddress address) {
        // try to reuse existing connections
        ChannelFuture outboundFuture = OutboundChannelPool.getInstance().getIdleChannelFuture(address);
        if (outboundFuture == null)
            outboundFuture = OutboundChannelPool.getInstance().getNewChannelFuture(address, getChannelPipelineFactory());

        this.outboundChannel = outboundFuture.getChannel();
        log.info("{} <-> {}", inboundChannel.getId(), outboundChannel.getId());
        return outboundFuture;
    }

    private ChannelPipelineFactory getChannelPipelineFactory() {
        return new HttpPipelineFactory(new HttpHandler());
    }

    public ChannelFutureListener getRequestReceivedListener(final HttpRequest request) {
        final ProxyStrategy strategy = this;
        return new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture future) {
                // keep alive strategy
                request.removeHeader("Proxy-Connection");
                HttpHeaders.setKeepAlive(request, true);

                // change the request to an default browser like request
                proxyToBrowserRequest(request);

                final HttpHandler handler = (HttpHandler) outboundChannel.getPipeline().get("handler");
                handler.setStrategy(strategy);

                responsePlugins = PluginProvider.getInstance().getResponsePlugins(request);

                log.info("Channel {} - sending request to {}", outboundChannel.getId(), HttpHeaders.getHost(request));
                log.debug(request.toString());
                outboundChannel.write(request);
            }
        };
    }

    protected void proxyToBrowserRequest(final HttpRequest request) {
        try {
            final StringBuilder sb = new StringBuilder();
            final URL url = new URL(request.getUri());
            sb.append(url.getPath());
            if (url.getQuery() != null)
                sb.append("?").append(url.getQuery());
            request.setUri(sb.toString());
        } catch (MalformedURLException e2) {
            log.error("Channel {} - {}\n" + e2.fillInStackTrace().toString(), inboundChannel.getId(), e2.toString());
        }
    }

    public ChannelFutureListener getResponseReceivedListener(final HttpResponse response) {
        return new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture future) {
                // error handling for not connected inbound channel
                if (!inboundChannel.isConnected()) {
                    log.error("Channel {} - inbound channel closed before sending response", inboundChannel.getId());
                    log.debug("Channel {} - {}", response.toString());
                    return;
                }

                // keep alive strategy
                Boolean isOutboundKeepAlive = HttpHeaders.isKeepAlive(response);
                HttpHeaders.setKeepAlive(response, true);

                // apply plugins
                for (ResponsePlugin plugin : responsePlugins) {
                    if (plugin.isApplicable(response)) {
                        log.info("Channel {} - using plugin {}", outboundChannel.getId(), plugin.getClass().getName());
                        plugin.run(response);
                        plugin.updateResponse(response);
                    }
                }

                // write response
                log.info("Channel {} - sending response - {}", inboundChannel.getId(), response.getStatus().toString());
                log.debug("Channel {} - {}", inboundChannel.getId(), response.toString());
                inboundChannel.write(response);

                // mark channel as idle for further requests
                if (isOutboundKeepAlive)
                    OutboundChannelPool.getInstance().setChannelIdle(future);
            }
        };
    }

    public ChannelFutureListener getOutboundChannelClosedListener() {
        return new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture future) {
                OutboundChannelPool.getInstance().closeChannel(future.getChannel().getId());
            }
        };
    }

    public ChannelFutureListener getInboundChannelClosedListener() {
        // nothing to do here
        return null;
    }

    public ChannelFutureListener getOutboundExceptionCaughtListener() {
        final HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY);
        return getResponseReceivedListener(response);
    }

    public ChannelFutureListener getInboundExceptionCaughtListener() {
        final HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY);
        return getResponseReceivedListener(response);
    }

}
