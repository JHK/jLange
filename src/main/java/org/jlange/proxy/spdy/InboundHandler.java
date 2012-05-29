package org.jlange.proxy.spdy;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

public class InboundHandler extends SimpleChannelUpstreamHandler {

    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        Object o = e.getMessage();
        System.err.println("yeahh!");
        o.equals(null);
        super.messageReceived(ctx, e);
    }
}
