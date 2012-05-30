package org.jlange.proxy.inbound.strategy;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.outbound.PassthroughHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Passthrough implements ProxyStrategy {

    private final Logger  log = LoggerFactory.getLogger(getClass());
    private final Channel inboundChannel;

    public Passthrough(final Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    public ChannelPipelineFactory getChannelPipelineFactory() {
        return new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() {
                final ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("passthrough", new PassthroughHandler(inboundChannel));
                return pipeline;
            }
        };
    }

    public ChannelFutureListener getChannelActionListener() {
        return new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture f) {
                log.info("Inboundchannel {} - sending response - connect ok", inboundChannel.getId());
                HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                HttpHeaders.setKeepAlive(response, true);
                inboundChannel.write(response);
                inboundChannel.setReadable(false);

                for (String name : inboundChannel.getPipeline().getNames())
                    inboundChannel.getPipeline().remove(name);
                inboundChannel.getPipeline().addLast("handler", new PassthroughHandler(f.getChannel()));
                inboundChannel.setReadable(true);
                log.info("Inboundchannel {} - passthrough to outboundchannel {}", inboundChannel.getId(), f.getChannel().getId());
            }
        };
    }
}
