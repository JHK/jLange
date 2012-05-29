package org.jlange.proxy.inbound;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

public class SpdyHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        Object o = e.getMessage();
        System.err.println("yeahh!");
        o.equals(null);
        super.messageReceived(ctx, e);
    }
}
