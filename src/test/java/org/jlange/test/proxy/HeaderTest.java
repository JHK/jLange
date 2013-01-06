package org.jlange.test.proxy;

import static org.junit.Assert.assertEquals;

import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.util.HttpProxyHeaders;
import org.junit.Test;

public class HeaderTest {

    @Test
    public void testVia() {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost:8000/");

        assertEquals(null, HttpHeaders.getHeader(request, HttpHeaders.Names.VIA));

        HttpProxyHeaders.setVia(request, HttpVersion.HTTP_1_1, "hostname", null);
        assertEquals("1.1 hostname", HttpHeaders.getHeader(request, HttpHeaders.Names.VIA));

        HttpProxyHeaders.setVia(request, HttpVersion.HTTP_1_0, "hostname2", "jLange");
        assertEquals("1.1 hostname, 1.0 hostname2 (jLange)", HttpHeaders.getHeader(request, HttpHeaders.Names.VIA));
    }

}
