package org.jlange.proxy.http;

import java.util.List;

import org.jboss.netty.channel.Channel;
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

    // TODO: move request to inbound handler
    private final HttpRequest         request;
    private final Logger              log = LoggerFactory.getLogger(getClass());
    private final Channel             inboundChannel;
    private final OutboundChannelPool outboundChannelPool;

    OutboundHandler(final Channel inboundChannel, final HttpRequest request, final OutboundChannelPool outboundChannelPool) {
        this.inboundChannel = inboundChannel;
        this.request = request;
        this.outboundChannelPool = outboundChannelPool;
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        Tools.closeOnFlush(e.getChannel());
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
        log.info("Outboundchannel {} - closed", e.getChannel().getId());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpResponse response = (HttpResponse) e.getMessage();
        final Channel outboundChannel = e.getChannel();
        log.info("Outboundchannel {} - response received - {}", outboundChannel.getId(), response.getStatus().toString());

        final List<ResponsePlugin> responsePlugins = PluginProvider.getInstance().getResponsePlugins(request, response);
        for (ResponsePlugin plugin : responsePlugins) {
            log.info("Outboundchannel {} - using plugin {}", outboundChannel.getId(), plugin.getClass().getName());
            plugin.run(request, response);
            plugin.updateResponse(response);
        }

        // error handling for not connected inbound channel
        if (!inboundChannel.isConnected()) {
            log.error("Inboundchannel {} - inbound channel closed before sending response", inboundChannel.getId());
            log.debug("Inboundchannel {} - {}", response.toString());
            Tools.closeOnFlush(outboundChannel);
            return;
        }

        // write response
        log.info("Inboundchannel {} - sending response - {}", inboundChannel.getId(), response.getStatus().toString());
        log.debug("Inboundchannel {} - {}", inboundChannel.getId(), response.toString());
        inboundChannel.write(response);

        // add outbound channel to idle list
        e.getFuture().addListener(outboundChannelPool.getIdleConnectionListener(request));
    }
}
