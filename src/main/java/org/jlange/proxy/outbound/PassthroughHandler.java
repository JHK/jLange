package org.jlange.proxy.outbound;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

public class PassthroughHandler extends SimpleChannelUpstreamHandler {

    private final Channel otherChannel;

    public PassthroughHandler(Channel otherChannel) {
        this.otherChannel = otherChannel;
    }

    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final ChannelBuffer buffer = (ChannelBuffer) e.getMessage();

        if (otherChannel.isConnected())
            otherChannel.write(buffer);
    }

    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (otherChannel.isConnected())
            otherChannel.close();
    }

}
