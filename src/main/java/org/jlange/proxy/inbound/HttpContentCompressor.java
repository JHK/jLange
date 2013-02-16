/*
 * Copyright (C) 2013 Julian Knocke
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

import java.net.SocketAddress;
import java.util.regex.Pattern;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

public class HttpContentCompressor extends org.jboss.netty.handler.codec.http.HttpContentCompressor {

    static final Pattern xapplication = Pattern.compile("^x-application/");
    static final Pattern application  = Pattern.compile("^application/");
    static final Pattern text         = Pattern.compile("^text/");

    public HttpContentCompressor(Integer compressionLevel) {
        super(compressionLevel);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) e.getMessage();

            if (response.isChunked()) {
                // ensure content length is not set for chunked responses
                response.removeHeader(HttpHeaders.Names.CONTENT_LENGTH);
                super.writeRequested(ctx, e);
            } else {
                // ensure content length is always set for unchunked responses
                if (HttpHeaders.getContentLength(response) == 0)
                    HttpHeaders.setContentLength(response, response.getContent().readableBytes());

                // apply content compression only for sane content types like text based
                String contentType = HttpHeaders.getHeader(response, HttpHeaders.Names.CONTENT_TYPE, "");
                if (shouldCompress(contentType))
                    super.writeRequested(ctx, e);
                else {
                    ctx.sendDownstream(e);
                    // dirty hack: fake response to poll acceptEncoding from HttpContentEncoder
                    try {
                        super.writeRequested(fakeContext, fakeEvent);
                    } catch (IllegalStateException exception) {}
                }
            }
        } else {
            super.writeRequested(ctx, e);
        }
    }

    private static boolean shouldCompress(String contentType) {
        if (text.matcher(contentType).find())
            return true;
        if (application.matcher(contentType).find())
            return true;
        if (xapplication.matcher(contentType).find())
            return true;
        return false;
    }

    private final static MessageEvent          fakeEvent   = new MessageEvent() {
                                                               @Override
                                                               public ChannelFuture getFuture() {
                                                                   return null;
                                                               }

                                                               @Override
                                                               public Channel getChannel() {
                                                                   return null;
                                                               }

                                                               @Override
                                                               public SocketAddress getRemoteAddress() {
                                                                   return null;
                                                               }

                                                               @Override
                                                               public Object getMessage() {
                                                                   HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                                                                           HttpResponseStatus.OK);
                                                                   response.setHeader(HttpHeaders.Names.CONTENT_ENCODING,
                                                                           HttpHeaders.Values.DEFLATE);
                                                                   return response;
                                                               }
                                                           };

    private final static ChannelHandlerContext fakeContext = new ChannelHandlerContext() {
                                                               @Override
                                                               public void setAttachment(Object attachment) {}

                                                               @Override
                                                               public void sendUpstream(ChannelEvent e) {}

                                                               @Override
                                                               public void sendDownstream(ChannelEvent e) {}

                                                               @Override
                                                               public ChannelPipeline getPipeline() {
                                                                   return null;
                                                               }

                                                               @Override
                                                               public String getName() {
                                                                   return null;
                                                               }

                                                               @Override
                                                               public ChannelHandler getHandler() {
                                                                   return null;
                                                               }

                                                               @Override
                                                               public Channel getChannel() {
                                                                   return null;
                                                               }

                                                               @Override
                                                               public Object getAttachment() {
                                                                   return null;
                                                               }

                                                               @Override
                                                               public boolean canHandleUpstream() {
                                                                   return false;
                                                               }

                                                               @Override
                                                               public boolean canHandleDownstream() {
                                                                   return false;
                                                               }
                                                           };
}
