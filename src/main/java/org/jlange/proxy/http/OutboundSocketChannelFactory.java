package org.jlange.proxy.http;

import java.util.concurrent.ExecutorService;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

/***
 * A class extending {@link NioServerSocketChannelFactory} to keep track of opened client channels.
 * 
 * @author Julian Knocke
 */
public class OutboundSocketChannelFactory extends NioClientSocketChannelFactory implements ClientSocketChannelFactory {

    private final ChannelGroup allChannels;

    public OutboundSocketChannelFactory(final ExecutorService newCachedThreadPool, final ExecutorService newCachedThreadPool2) {
        super(newCachedThreadPool, newCachedThreadPool2);

        allChannels = new DefaultChannelGroup("client");
    }

    @Override
    public SocketChannel newChannel(ChannelPipeline pipeline) {
        final SocketChannel channel = super.newChannel(pipeline);

        allChannels.add(channel);
        channel.getCloseFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                allChannels.remove(channel);
            }
        });

        return channel;
    }

    public void addChannel(final Channel e) {
        allChannels.add(e);
    }

    public ChannelGroup getChannels() {
        return allChannels;
    }

}
