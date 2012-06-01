package org.jlange.proxy;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;

public class Tools {

    public static String readBuffer(final ChannelBuffer buffer) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < buffer.capacity(); i++) {
            byte b = buffer.getByte(i);
            sb.append((char) b);
        }

        return sb.toString();
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    public static void closeOnFlush(final Channel ch) {
        if (ch != null && ch.isConnected())
            ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }
}
