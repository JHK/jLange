package org.jlange.proxy.test.client;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpVersion;

public class HttpRequestClient {

    private final ClientBootstrap bootstrap;
    private final ChannelFactory  factory;
    private final Logger          log = Logger.getLogger(HttpRequestClient.class.getName());

    public HttpRequestClient() {
        factory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());

        bootstrap = new ClientBootstrap(factory);
        bootstrap.setOption("tcpNoDelay", true);
        bootstrap.setOption("keepAlive", true);
    }

    public void get(String urlString) throws MalformedURLException {
        URL url = new URL(urlString);

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url.toString());
        request.setHeader("Host", url.getHost() + ":" + url.getPort());
        this.request(request);
    }

    protected ChannelPipeline getPipeline(HttpRequest request) {
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("HttpResponseDecoder", new HttpResponseDecoder(8192, 8192 * 2, 8192 * 2));
        pipeline.addLast("HttpRequestEncoder", new HttpRequestEncoder());
        pipeline.addLast("HttpRequestHandler", new DebugHttpRequestHandler(request));

        return pipeline;
    }

    public void request(HttpRequest request) throws MalformedURLException {
        URL url = new URL(request.getUri());

        this.request(request, url.getHost(), url.getPort());
    }

    public void request(HttpRequest request, String host, Integer port) throws MalformedURLException {
        log.info("starting request to " + request.getUri());
        bootstrap.setPipeline(getPipeline(request));
        ChannelFuture connection = bootstrap.connect(new InetSocketAddress(host, port));
        connection.awaitUninterruptibly();
        connection.getChannel().getCloseFuture().awaitUninterruptibly();
        factory.releaseExternalResources();
        log.info("finished");
    }
}
