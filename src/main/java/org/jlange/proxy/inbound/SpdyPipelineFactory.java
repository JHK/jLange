package org.jlange.proxy.inbound;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jlange.proxy.inbound.ssl.SecureSslContextFactory;

public class SpdyPipelineFactory implements ChannelPipelineFactory {

    public ChannelPipeline getPipeline() throws Exception {
        final ChannelPipeline pipeline = Channels.pipeline();

        SSLEngine engine = SecureSslContextFactory.getServerContext().createSSLEngine();
        engine.setUseClientMode(false);

        pipeline.addLast("ssl", new SslHandler(engine));

        // On top of the SSL handler, add the text line codec.
        // pipeline.addLast("framer", new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));

        // pipeline.addLast("decoder", new StringDecoder());
        // pipeline.addLast("encoder", new StringEncoder());

        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("encoder", new HttpResponseEncoder());

        // pipeline.addLast("decoder", new SpdyFrameDecoder());
        // pipeline.addLast("encoder", new SpdyFrameEncoder());

        pipeline.addLast("handler", new SpdyHandler());

        // pipeline.addLast("deflater", new HttpContentCompressor(9));
        // pipeline.addLast("handler", new InboundHandler());

        return pipeline;
    }
}
