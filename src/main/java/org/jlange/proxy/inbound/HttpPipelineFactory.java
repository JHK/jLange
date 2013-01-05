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

import java.net.InetSocketAddress;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.jlange.proxy.util.Config;
import org.jlange.proxy.util.IdleShutdownHandler;
import org.jlange.proxy.util.RemoteAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpPipelineFactory implements ChannelPipelineFactory {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public ChannelPipeline getPipeline() {
        final ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("ssl_or_http", new SslOrHttpDecoder());

        return pipeline;
    }

    private final class SslOrHttpDecoder extends SimpleChannelUpstreamHandler {

        @Override
        public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
            final HttpRequest request = (HttpRequest) e.getMessage();

            final ChannelPipeline pipeline = ctx.getPipeline();
            if (request.getMethod().equals(HttpMethod.CONNECT)) {
                log.info("Channel {} - connect received - open a new channel to {}", e.getChannel().getId(), request.getUri());
                log.debug("Channel {} - {}", e.getChannel().getId(), request.toString());

                // open channel
                final RemoteAddress address = RemoteAddress.parseRequest(request);
                final ChannelFuture outboundFuture = openPassthroughChannel(address, pipeline.getChannel());

                // update pipeline listener
                final ChannelFutureListener updatePipeline = getUpdatePipelineListener(pipeline, outboundFuture.getChannel());

                // send OK when outbound channel is connected
                outboundFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(final ChannelFuture future) {
                        if (future.isSuccess()) {
                            pipeline.getChannel().setReadable(false);
                            pipeline.getChannel().write(getOkResponse()).addListener(updatePipeline);
                        }
                    }
                });
            } else {
                pipeline.addLast("deflater", new HttpContentCompressor(Config.COMPRESSION_LEVEL));
                pipeline.addLast("idle", new IdleShutdownHandler(300, 0));
                pipeline.addLast("mapping", new HttpRequestResponseHandler());
                pipeline.addLast("proxy", new ProxyHandler());

                pipeline.remove(this);
                ctx.sendUpstream(e);
            }
        }

        private HttpResponse getOkResponse() {
            return new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        }

        private ChannelFutureListener getUpdatePipelineListener(final ChannelPipeline pipeline, final Channel target) {
            return new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) {
                    pipeline.remove(HttpRequestDecoder.class);
                    pipeline.remove(HttpResponseEncoder.class);
                    pipeline.remove(SslOrHttpDecoder.class);

                    pipeline.addLast("passthrough", new PassthroughHandler(target));
                    pipeline.getChannel().setReadable(true);
                }
            };

        }

        private ChannelFuture openPassthroughChannel(final RemoteAddress address, final Channel target) {
            final ClientBootstrap outboundClient = new ClientBootstrap(OutboundChannelPool.getNioClientSocketChannelFactory());
            final ChannelPipeline outboundPipeline = Channels.pipeline();
            outboundPipeline.addLast("passthrough", new PassthroughHandler(target));
            outboundClient.setPipeline(outboundPipeline);
            return outboundClient.connect(new InetSocketAddress(address.getHost(), address.getPort()));
        }
    }

    private final class PassthroughHandler extends SimpleChannelUpstreamHandler {

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
            if (otherChannel != null && otherChannel.isConnected())
                otherChannel.close();
            e.getChannel().close();
        }
    }
}
