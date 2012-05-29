package org.jlange.proxy.outbound;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

public class PassthroughHandler extends SimpleChannelUpstreamHandler {

    private final Channel inboundChannel;

    public PassthroughHandler(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final ChannelBuffer buffer = (ChannelBuffer) e.getMessage();

        if (inboundChannel.isConnected())
            inboundChannel.write(buffer);
    }
}
