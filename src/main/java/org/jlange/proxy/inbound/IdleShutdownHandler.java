package org.jlange.proxy.inbound;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.timeout.IdleState;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.jlange.proxy.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdleShutdownHandler extends IdleStateHandler implements ChannelHandler {

    public final static Timer timer = new HashedWheelTimer();
    
    private final Logger log = LoggerFactory.getLogger(getClass());

    public IdleShutdownHandler(int readerIdleTimeSeconds, int writerIdleTimeSeconds) {
        super(timer, readerIdleTimeSeconds, writerIdleTimeSeconds, 0);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleState state, long lastActivityTimeMillis) throws Exception {
        log.info("Channel {} - shutdown due idle time exceeded", ctx.getChannel().getId());
        Tools.closeOnFlush(ctx.getChannel());
    }

}
