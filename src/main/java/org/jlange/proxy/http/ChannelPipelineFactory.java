package org.jlange.proxy.http;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

public class ChannelPipelineFactory implements org.jboss.netty.channel.ChannelPipelineFactory {

    private final ClientSocketChannelFactory factory;

    public ChannelPipelineFactory(ClientSocketChannelFactory factory) {
        this.factory = factory;
    }

    @Override
    public ChannelPipeline getPipeline() {
        ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addLast("HttpRequestDecoder", new HttpRequestDecoder());
        pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
        pipeline.addLast("HttpResponseEncoder", new HttpResponseEncoder());
        pipeline.addLast("InboundHandler", new InboundHandler(factory));
        return pipeline;
    }

}
