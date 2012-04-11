package org.jlange.proxy.test;

import java.net.MalformedURLException;

import junit.framework.TestCase;

import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.http.Server;
import org.jlange.proxy.test.client.HttpRequestClient;

public class Integration extends TestCase {

    Integer port = 8080;
    Server  proxy;

    @Override
    protected void setUp() throws Exception {

        proxy = new Server(port);
        proxy.start();

        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {

        proxy.stop();

        super.tearDown();
    }

    // TODO make real tests... this ones always succeeds
    public void testChunkedHtml() throws MalformedURLException {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://fritz.box:80/index.html");
        request.setHeader("Host", "backend:80");

        HttpRequestClient client2 = new HttpRequestClient();
        client2.request(request, "localhost", port);
    }

    public void testDynamicHtmlWithoutChunks() throws MalformedURLException {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://backend:80/mythweb/");
        request.setHeader("Host", "backend:80");

        HttpRequestClient client1 = new HttpRequestClient();
        client1.request(request, "localhost", port);
    }

    public void testStaticHtml() throws MalformedURLException {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://backend:80/index.html");
        request.setHeader("Host", "backend:80");

        HttpRequestClient client1 = new HttpRequestClient();
        client1.request(request, "localhost", port);
    }

}
