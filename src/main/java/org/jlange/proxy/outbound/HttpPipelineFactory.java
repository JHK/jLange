/*
 * Copyright (C) 2012 Julian Knocke
 * 
 * This file is part of jLange.
 * 
 * jLange is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * jLange is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with jLange. If not, see <http://www.gnu.org/licenses/>.
 */
package org.jlange.proxy.outbound;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jlange.proxy.util.Config;
import org.jlange.proxy.util.IdleShutdownHandler;

public class HttpPipelineFactory implements ChannelPipelineFactory {

    public ChannelPipeline getPipeline() {
        final ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("encoder", new HttpRequestEncoder());
        pipeline.addLast("decoder", new HttpResponseDecoder(8192, 8192 * 2, 8192 * 2));
        pipeline.addLast("inflater", new HttpContentDecompressor());
        pipeline.addLast("plugin", new PluginHandler());
        pipeline.addLast("idle", new IdleShutdownHandler(0, Config.OUTBOUND_TIMEOUT));
        pipeline.addLast("handler", new HttpResponseHandler());

        return pipeline;
    }
}
