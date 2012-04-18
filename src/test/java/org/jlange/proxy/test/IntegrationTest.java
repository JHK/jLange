package org.jlange.proxy.test;

import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;

import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.http.Server;
import org.jlange.proxy.test.client.HttpRequestClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IntegrationTest {

    Integer port = 8080;
    Server  proxy;

    @Before
    public void setUp() throws Exception {

        proxy = new Server(port);
        proxy.start();
    }

    @After
    public void tearDown() throws Exception {

        proxy.stop();
    }

    // TODO make real tests... this ones always succeeds
    @Test
    public void testChunkedHtml() throws MalformedURLException {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://fritz.box:80/index.html");
        request.setHeader("Host", "backend:80");

        HttpRequestClient client2 = new HttpRequestClient();
        client2.request(request, "localhost", port);
        assertTrue(true);
    }

    @Test
    public void testDynamicHtmlWithoutChunks() throws MalformedURLException {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://backend:80/mythweb/");
        request.setHeader("Host", "backend:80");

        HttpRequestClient client1 = new HttpRequestClient();
        client1.request(request, "localhost", port);
    }

    @Test
    public void testStaticHtml() throws MalformedURLException {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://backend:80/index.html");
        request.setHeader("Host", "backend:80");

        HttpRequestClient client1 = new HttpRequestClient();
        client1.request(request, "localhost", port);
    }

}
