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
package org.jlange.proxy.inbound;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This handler should just slows down write speed until the whole response was written. It skips the writeComplete event until the whole
 * response was written and warns if another response wants to be written before the first response completed.
 */
public class HttpResponseWriteDelayHandler extends SimpleChannelHandler {

    private final static Logger log          = LoggerFactory.getLogger(HttpResponseWriteDelayHandler.class);
    private final Integer       writeSpeed;

    private HttpResponse        response;
    private long                writtenBytes = 0L;

    public HttpResponseWriteDelayHandler(final Integer writeSpeed) {
        this.writeSpeed = writeSpeed;
    }

    @Override
    public void writeComplete(final ChannelHandlerContext ctx, final WriteCompletionEvent e) {
        // No HttpResponse write event, nothing upstream from here should be interested in
        if (response == null)
            return;

        log.debug("Channel {} - wrote {} bytes", ctx.getChannel().getId(), e.getWrittenAmount());

        this.writtenBytes += e.getWrittenAmount();

        if (writtenBytes >= response.getContent().readableBytes()) {
            log.debug("Channel {} - write complete", ctx.getChannel().getId());

            final ChannelEvent e2 = new WriteCompletionEvent() {
                @Override
                public ChannelFuture getFuture() {
                    return e.getFuture();
                }

                @Override
                public Channel getChannel() {
                    return e.getChannel();
                }

                @Override
                public long getWrittenAmount() {
                    return writtenBytes;
                }
            };

            // FIXME: this is bad, but it works!
            if (writeSpeed.intValue() != 0) {
                try {
                    Thread.sleep(writtenBytes / writeSpeed.intValue());
                } catch (InterruptedException e1) {}
            }

            this.writtenBytes = 0L;
            this.response = null;

            ctx.sendUpstream(e2);
        }
    }

    @Override
    public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent e) {
        if (response != null) {
            log.warn("Channel {} - writing too fast", ctx.getChannel().getId());
            writtenBytes = 0L;
        }

        if (e.getMessage() instanceof HttpResponse) {
            response = (HttpResponse) e.getMessage();

            log.debug("Channel {} - write requested ({} bytes)", ctx.getChannel().getId(), response.getContent().readableBytes());
        }

        ctx.sendDownstream(e);
    }
}
