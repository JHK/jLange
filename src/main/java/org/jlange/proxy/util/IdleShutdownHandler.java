/*
 * Copyright (C) 2012 Julian Knocke
 * 
 * This file is part of jLange.
 * 
 * jLange is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * jLange is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with jLange. If not, see <http://www.gnu.org/licenses/>.
 */
package org.jlange.proxy.util;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.timeout.IdleState;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdleShutdownHandler extends IdleStateHandler implements ChannelHandler {

    public final static Timer timer = new HashedWheelTimer();
    
    private final Logger log = LoggerFactory.getLogger(getClass());

    public IdleShutdownHandler(final int readerIdleTimeSeconds, final int writerIdleTimeSeconds) {
        super(timer, readerIdleTimeSeconds, writerIdleTimeSeconds, 0);
    }

    @Override
    protected void channelIdle(final ChannelHandlerContext ctx, final IdleState state, final long lastActivityTimeMillis) {
        log.info("Channel {} - shutdown due idle time exceeded", ctx.getChannel().getId());
        ctx.getChannel().close();
    }

}
