package org.jlange.proxy.outbound;

import java.util.List;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.Tools;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HttpHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private final Logger                log = LoggerFactory.getLogger(getClass());
    private final Channel               inboundChannel;
    private final List<ResponsePlugin>  responsePlugins;
    private final ChannelFutureListener messageReceivedListener;

    HttpHandler(final Channel inboundChannel, final List<ResponsePlugin> responsePlugins,
            final ChannelFutureListener messageReceivedListener) {
        this.inboundChannel = inboundChannel;
        this.responsePlugins = responsePlugins;
        this.messageReceivedListener = messageReceivedListener;
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

        for (ResponsePlugin plugin : responsePlugins) {
            if (plugin.isApplicable(response)) {
                log.info("Outboundchannel {} - using plugin {}", outboundChannel.getId(), plugin.getClass().getName());
                plugin.run(response);
                plugin.updateResponse(response);
            }
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
        e.getFuture().addListener(messageReceivedListener);
    }
}