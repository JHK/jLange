package org.jlange.proxy.http;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;

public class OutboundPipelineFactory implements ChannelPipelineFactory {

    private final Channel inboundChannel;
    private final HttpRequest request;
    
    public OutboundPipelineFactory(final Channel inboundChannel, final HttpRequest request) {
        this.inboundChannel = inboundChannel;
        this.request = request;
    }
    
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("encoder", new HttpRequestEncoder());
        pipeline.addLast("decoder", new HttpResponseDecoder(8192, 8192 * 2, 8192 * 2));
        pipeline.addLast("inflater", new HttpContentDecompressor());
        pipeline.addLast("handler", new OutboundHandler(inboundChannel, request));

        return pipeline;
    }

}
