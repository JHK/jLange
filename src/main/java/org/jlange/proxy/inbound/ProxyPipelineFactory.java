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
package org.jlange.proxy.inbound;

import javax.net.ssl.SSLContext;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jlange.proxy.inbound.ssl.KeyStoreManager;
import org.jlange.proxy.inbound.ssl.SelfSignedKeyStoreManager;
import org.jlange.proxy.inbound.ssl.SslContextFactory;

public class ProxyPipelineFactory implements ChannelPipelineFactory {

    private final SSLContext context;

    public ProxyPipelineFactory() {
        KeyStoreManager ksm = new SelfSignedKeyStoreManager(false);
        SslContextFactory scf = new SslContextFactory(ksm);
        this.context = scf.getServerContext();
    }

    public ChannelPipeline getPipeline() {
        final ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("deflater", new HttpContentCompressor(9));
        pipeline.addLast("idle", new IdleShutdownHandler(60, 0));
        pipeline.addLast("proxy_protocol", new ProxyProtocolHandler(context));
        pipeline.addLast("handler", new HttpProxyHandler());

        return pipeline;
    }
}
