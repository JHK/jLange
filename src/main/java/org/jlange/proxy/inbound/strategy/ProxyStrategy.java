package org.jlange.proxy.inbound.strategy;

import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipelineFactory;

public interface ProxyStrategy {

    /**
     * Builds a {@link ChannelPipelineFactory} which will be used for outbound connections.
     * 
     * @return outbound {@link ChannelPipelineFactory}
     */
    public ChannelPipelineFactory getChannelPipelineFactory();

    /**
     * Builds a {@link ChannelFutureListener} which will be executed on a new or reused channel to initialize the connection
     * 
     * @return initializing {@link ChannelFutureListener}
     */
    public ChannelFutureListener getChannelActionListener();
}
