package org.jlange.proxy.http;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

public class InboundPipelineFactory implements ChannelPipelineFactory {

    private final ChannelFactory outboundFactory;

    public InboundPipelineFactory(final ChannelFactory outboundFactory) {
        this.outboundFactory = outboundFactory;
    }

    @Override
    public ChannelPipeline getPipeline() {
        final ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("deflater", new HttpContentCompressor());
        pipeline.addLast("handler", new InboundHandler(outboundFactory));

        return pipeline;
    }

}
