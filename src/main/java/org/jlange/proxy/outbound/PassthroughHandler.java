package org.jlange.proxy.outbound;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PassthroughHandler extends SimpleChannelUpstreamHandler {

    private final Logger  log = LoggerFactory.getLogger(getClass());
    private final Channel otherChannel;

    public PassthroughHandler(Channel otherChannel) {
        this.otherChannel = otherChannel;
    }

    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        log.debug("Channel {} - message received", e.getChannel().getId());

        final ChannelBuffer buffer = (ChannelBuffer) e.getMessage();

        if (otherChannel.isConnected())
            otherChannel.write(buffer);
    }

    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
        log.info("Channel {} - closed", e.getChannel().getId());
        if (otherChannel != null && otherChannel.isConnected())
            otherChannel.close();
    }

    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getStackTrace());
        otherChannel.close();
        e.getChannel().close();
    }
}
