package org.jlange.proxy.inbound;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.util.HttpHeaders2;

/**
 * This class should do the same than the origin, but skip some certain types.
 */
public class HttpContentCompressor extends org.jboss.netty.handler.codec.http.HttpContentCompressor {

    public HttpContentCompressor() {
        super();
    }

    public HttpContentCompressor(final int compressionLevel) {
        super(compressionLevel);
    }

    @Override
    public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {

        final Object msg = e.getMessage();

        if (msg instanceof HttpResponse) {

            final HttpResponse response = (HttpResponse) msg;

            // skip certain types
            if (HttpHeaders2.isJPG(response) || HttpHeaders2.isPNG(response)) {
                ctx.sendDownstream(e);
            } else {
                super.writeRequested(ctx, e);
            }

        } else {
            super.writeRequested(ctx, e);
        }

    }
}