package org.jlange.proxy;

import java.net.MalformedURLException;
import java.net.URL;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;

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

    public static URL getURL(final HttpRequest request) {
        URL url = null;

        String uriString = request.getUri();
        
        if (request.getMethod().equals(HttpMethod.CONNECT)) {
            uriString = "https://" + uriString;
        }
        
        try {
            url = new URL(uriString);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
       
        return url;
    }
}
