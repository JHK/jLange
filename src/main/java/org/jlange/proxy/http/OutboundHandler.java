package org.jlange.proxy.http;

import java.util.List;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.Tools;
import org.jlange.proxy.plugin.PluginProvider;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OutboundHandler extends SimpleChannelUpstreamHandler {

    private final Logger         log = LoggerFactory.getLogger(getClass());
    private final Channel        inboundChannel;
    private final HttpRequest    request;

    private List<ResponsePlugin> responsePlugins;

    OutboundHandler(final Channel inboundChannel, final HttpRequest request) {
        this.inboundChannel = inboundChannel;
        this.request = request;
    }

    @Override
    public void channelBound(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        responsePlugins = PluginProvider.getResponsePlugins(request);
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Channel {} - closed", e.getChannel().getId());
        Tools.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        System.err.println("Outbound Channel " + e.getChannel().getId() + " - " + e.getCause().getMessage());
        // log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        e.getCause().printStackTrace();
        Tools.closeOnFlush(e.getChannel());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpResponse response = (HttpResponse) e.getMessage();
        final Channel outboundChannel = e.getChannel();
        log.info("Channel {} - response received - {}", outboundChannel.getId(), response.getStatus().toString());

        responsePlugins = PluginProvider.getResponsePlugins(responsePlugins, response);
        for (ResponsePlugin plugin : responsePlugins) {
            log.info("Channel {} - using plugin {}", inboundChannel.getId(), plugin.getClass().getName());
            plugin.run(request, response);
            plugin.updateResponse(response);
        }

        // error handling for not connected inbound channel
        if (!inboundChannel.isConnected()) {
            log.error("Channel {} - inbound channel closed before sending response", inboundChannel.getId());
            log.debug("Channel {} - {}", response.toString());
            Tools.closeOnFlush(outboundChannel);
            return;
        }

        // write response
        log.info("Channel {} - sending response", inboundChannel.getId());
        log.debug("Channel {} - {}", inboundChannel.getId(), response.toString());
        inboundChannel.write(response);
        log.info("Channel {} - sending response finished", inboundChannel.getId());
    }
}
