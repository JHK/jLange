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
package org.jlange.proxy.strategy;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.Tools;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.jlange.proxy.outbound.PassthroughHandler;
import org.jlange.proxy.util.RemoteAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Passthrough implements ProxyStrategy {

    private final Logger  log = LoggerFactory.getLogger(getClass());
    private final Channel inboundChannel;
    private Channel       outboundChannel;

    public Passthrough(final Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    public ChannelFuture getOutboundChannelFuture(final RemoteAddress address) {
        ChannelFuture outboundFuture = OutboundChannelPool.getInstance().getNewChannelFuture(address, getChannelPipelineFactory());

        this.outboundChannel = outboundFuture.getChannel();
        log.info("{} <-> {}", inboundChannel.getId(), outboundChannel.getId());
        return outboundFuture;
    }

    private ChannelPipelineFactory getChannelPipelineFactory() {
        return new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() {
                final ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("passthrough", new PassthroughHandler(inboundChannel));
                return pipeline;
            }
        };
    }

    public ChannelFutureListener getRequestReceivedListener(HttpRequest request) {
        return new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture future) {
                // respond that a remote channel has opened
                log.info("Channel {} - sending response - connect ok", inboundChannel.getId());
                inboundChannel.write(getResponse());

                // rebuild inbound channel
                inboundChannel.setReadable(false);
                for (String name : inboundChannel.getPipeline().getNames())
                    inboundChannel.getPipeline().remove(name);
                inboundChannel.getPipeline().addLast("passthrough", new PassthroughHandler(outboundChannel));
                inboundChannel.setReadable(true);

                log.info("Channel {} - passthrough to outboundchannel {}", inboundChannel.getId(), outboundChannel.getId());
            }
        };
    }

    public ChannelFutureListener getResponseReceivedListener(HttpResponse response) {
        return null;
    }

    public ChannelFutureListener getOutboundChannelClosedListener() {
        return new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture future) {
                Tools.closeOnFlush(inboundChannel);
            }
        };
    }

    public ChannelFutureListener getInboundChannelClosedListener() {
        return new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture future) {
                Tools.closeOnFlush(outboundChannel);
            }
        };
    }

    @Override
    public ChannelFutureListener getOutboundExceptionCaughtListener() {
        return getOutboundChannelClosedListener();
    }

    @Override
    public ChannelFutureListener getInboundExceptionCaughtListener() {
        return getInboundChannelClosedListener();
    }

    private HttpResponse getResponse() {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpHeaders.setKeepAlive(response, true);
        return response;
    }
}
