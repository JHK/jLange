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
import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.npn.NextProtoNego;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jlange.proxy.inbound.ssl.HttpOrSpdyDecoder;
import org.jlange.proxy.inbound.ssl.KeyStoreManager;
import org.jlange.proxy.inbound.ssl.SelfSignedKeyStoreManager;
import org.jlange.proxy.inbound.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpdyPipelineFactory implements ChannelPipelineFactory {

    private final Logger     log = LoggerFactory.getLogger(getClass());
    private final SSLContext context;

    public SpdyPipelineFactory() {
        KeyStoreManager ksm = new SelfSignedKeyStoreManager();
        SslContextFactory scf = new SslContextFactory(ksm);
        this.context = scf.getServerContext();
    }

    public ChannelPipeline getPipeline() {
        final ChannelPipeline pipeline = Channels.pipeline();

        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(false);

        NextProtoNego.put(engine, new SimpleServerProvider());
        if (log.isDebugEnabled())
            NextProtoNego.debug = true;

        pipeline.addLast("ssl", new SslHandler(engine));
        pipeline.addLast("http_or_spdy", new HttpOrSpdyDecoder(new HttpProxyHandler()));

        return pipeline;
    }
}