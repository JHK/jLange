package org.jlange.proxy.outbound;

import org.jboss.netty.channel.ChannelPipelineFactory;

/***
 * Interface for creating a {@link ChannelPipelineFactory} when it is needed. So it is just for lazy loading.
 * 
 * @author Julian Knocke
 * 
 */
public interface ChannelPipelineFactoryFactory {

    /**
     * Builds a {@link ChannelPipelineFactory}
     * 
     * @return {@link ChannelPipelineFactory}
     */
    public ChannelPipelineFactory getChannelPipelineFactory();
}
