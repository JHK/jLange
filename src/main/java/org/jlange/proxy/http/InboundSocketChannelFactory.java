package org.jlange.proxy.http;

import java.util.concurrent.ExecutorService;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

/***
 * A class extending {@link NioServerSocketChannelFactory} to keep track of opened server channels.
 * 
 * @author Julian Knocke
 */
public class InboundSocketChannelFactory extends NioServerSocketChannelFactory implements ChannelFactory {

    private final ChannelGroup allChannels;

    public InboundSocketChannelFactory(final ExecutorService newCachedThreadPool, final ExecutorService newCachedThreadPool2) {
        super(newCachedThreadPool, newCachedThreadPool2);

        allChannels = new DefaultChannelGroup("server");
    }

    @Override
    public ServerSocketChannel newChannel(final ChannelPipeline pipeline) {
        final ServerSocketChannel channel = super.newChannel(pipeline);

        allChannels.add(channel);
        channel.getCloseFuture().addListener(new ChannelFutureListener() {
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
