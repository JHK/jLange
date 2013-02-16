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

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.spdy.SpdyHttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogHandler extends SimpleChannelHandler {

    private final static Logger log = LoggerFactory.getLogger(LogHandler.class);

    private final boolean       detailedHttpMessages;

    public LogHandler() {
        this(false);
    }

    public LogHandler(boolean detailedHttpMessages) {
        this.detailedHttpMessages = detailedHttpMessages;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        log.info("Channel {} - message received - {}", getChannelId(ctx), getChannelMessage(e));
        super.messageReceived(ctx, e);
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        log.debug("Channel {} - handleUpstream", getChannelId(ctx));
        super.handleUpstream(ctx, e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        log.info("Channel {} - exceptionCaught - {}", getChannelId(ctx), e);
        super.exceptionCaught(ctx, e);
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.debug("Channel {} - channelOpen", getChannelId(ctx));
        super.channelOpen(ctx, e);
    }

    @Override
    public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.debug("Channel {} - channelBound", getChannelId(ctx));
        super.channelBound(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.info("Channel {} - channelConnected", getChannelId(ctx));
        super.channelConnected(ctx, e);
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.info("Channel {} - channelInterestChanged {}", getChannelId(ctx), new Object[] { e.getValue(), e.getState(),
                e.getChannel().isReadable(), e.getChannel().isWritable() });
        super.channelInterestChanged(ctx, e);
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.info("Channel {} - channelDisconnected", getChannelId(ctx));
        super.channelDisconnected(ctx, e);
    }

    @Override
    public void channelUnbound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.debug("Channel {} - channelUnbound", getChannelId(ctx));
        super.channelUnbound(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.debug("Channel {} - channelClosed", getChannelId(ctx));
        super.channelClosed(ctx, e);
    }

    @Override
    public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e) throws Exception {
        log.debug("Channel {} - writeComplete - writtenAmount: {}", getChannelId(ctx), e.getWrittenAmount());
        super.writeComplete(ctx, e);
    }

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        log.debug("Channel {} - handleDownstream", getChannelId(ctx));
        super.handleDownstream(ctx, e);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        log.info("Channel {} - writeRequested - {}", getChannelId(ctx), getChannelMessage(e));
        super.writeRequested(ctx, e);
    }

    @Override
    public void bindRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.debug("Channel {} - bindRequested", getChannelId(ctx));
        super.bindRequested(ctx, e);
    }

    @Override
    public void connectRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.debug("Channel {} - connectRequested", getChannelId(ctx));
        super.connectRequested(ctx, e);
    }

    @Override
    public void disconnectRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.debug("Channel {} - disconnectRequested", getChannelId(ctx));
        super.disconnectRequested(ctx, e);
    }

    @Override
    public void unbindRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.debug("Channel {} - unbindRequested", getChannelId(ctx));
        super.unbindRequested(ctx, e);
    }

    @Override
    public void closeRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.debug("Channel {} - closeRequested", getChannelId(ctx));
        super.closeRequested(ctx, e);
    }

    private String getChannelId(ChannelHandlerContext ctx) {
        return ctx.getChannel().getRemoteAddress() != null ? ctx.getChannel().getRemoteAddress().toString() : "unconnected";
    }

    private Object getChannelMessage(MessageEvent e) {
        if (detailedHttpMessages)
            return e.getMessage();

        if (e.getMessage() instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) e.getMessage();
            String requestString;
            if (HttpHeaders.getHeader(request, SpdyHttpHeaders.Names.STREAM_ID) == null)
                requestString = request.getUri();
            else
                requestString = HttpHeaders.getHost(request) + request.getUri() + " (X-SPDY-Stream-ID: "
                        + SpdyHttpHeaders.getStreamId(request) + ")";
            return requestString;
        } else if (e.getMessage() instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) e.getMessage();
            String responseString = response.getStatus().toString();
            if (HttpHeaders.getHeader(response, SpdyHttpHeaders.Names.STREAM_ID) != null)
                responseString += " (X-SPDY-Stream-ID: " + SpdyHttpHeaders.getStreamId(response) + ")";
            return responseString;
        }

        return e.getMessage();
    }
}
