package org.jlange.proxy.outbound;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;

public class HttpPipelineFactory implements ChannelPipelineFactory {

    private final ChannelHandler handler;

    public HttpPipelineFactory(final ChannelHandler handler) {
        this.handler = handler;
    }

    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("encoder", new HttpRequestEncoder());
        pipeline.addLast("decoder", new HttpResponseDecoder(8192, 8192 * 2, 8192 * 2));
        pipeline.addLast("aggregator", new HttpChunkAggregator(2 * 1024 * 1024));
        pipeline.addLast("inflater", new HttpContentDecompressor());
        pipeline.addLast("handler", handler);

        return pipeline;
    }

}
