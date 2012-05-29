package org.jlange.proxy.outbound;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;

public class HttpPipelineFactory implements ChannelPipelineFactory {

    private final Channel inboundChannel;
    private final HttpRequest request;
    private final OutboundChannelPool outboundChannelpool;
    
    public HttpPipelineFactory(final Channel inboundChannel, final HttpRequest request, final OutboundChannelPool outboundChannelPool) {
        this.inboundChannel = inboundChannel;
        this.request = request;
        this.outboundChannelpool = outboundChannelPool;
    }
    
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("encoder", new HttpRequestEncoder());
        pipeline.addLast("decoder", new HttpResponseDecoder(8192, 8192 * 2, 8192 * 2));
        pipeline.addLast("aggregator", new HttpChunkAggregator(2 * 1024 * 1024));
        pipeline.addLast("inflater", new HttpContentDecompressor());
        pipeline.addLast("handler", new HttpHandler(inboundChannel, request, outboundChannelpool));

        return pipeline;
    }

}
