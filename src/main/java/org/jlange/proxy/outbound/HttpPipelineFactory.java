package org.jlange.proxy.outbound;

import java.util.List;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jlange.proxy.plugin.ResponsePlugin;

public class HttpPipelineFactory implements ChannelPipelineFactory {

    private final Channel               inboundChannel;
    private final List<ResponsePlugin>  responsePlugins;
    private final ChannelFutureListener messageReceivedListener;

    public HttpPipelineFactory(final Channel inboundChannel, final List<ResponsePlugin> responsePlugins,
            final ChannelFutureListener messageReceivedListener) {
        this.inboundChannel = inboundChannel;
        this.responsePlugins = responsePlugins;
        this.messageReceivedListener = messageReceivedListener;
    }

    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("encoder", new HttpRequestEncoder());
        pipeline.addLast("decoder", new HttpResponseDecoder(8192, 8192 * 2, 8192 * 2));
        pipeline.addLast("aggregator", new HttpChunkAggregator(2 * 1024 * 1024));
        pipeline.addLast("inflater", new HttpContentDecompressor());
        pipeline.addLast("handler", new HttpHandler(inboundChannel, responsePlugins, messageReceivedListener));

        return pipeline;
    }

}
