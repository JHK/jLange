package org.jlange.proxy.util;

import java.net.MalformedURLException;
import java.net.URL;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class RemoteAddress {

    public static RemoteAddress parseRequest(HttpRequest request) {
        final RemoteAddress address;
        if (request.getMethod().equals(HttpMethod.CONNECT))
            address = parseString(request.getUri());
        else
            address = parseString(HttpHeaders.getHost(request));
        return address;
    }

    public static RemoteAddress parseString(String uri) {
        final RemoteAddress address;
        if (uri.startsWith("http")) {
            // consider string as url
            try {
                address = new RemoteAddress(new URL(uri));
            } catch (MalformedURLException e) {
                e.printStackTrace();
                throw new RuntimeException(uri);
            }
        } else if (uri.contains(":")) {
            // consider string host:port
            String[] hostAndPort = uri.split(":");
            address = new RemoteAddress(hostAndPort[0], new Integer(hostAndPort[1]));
        } else {
            address = new RemoteAddress(uri, 80);
        }
        return address;
    }

    private final String  host;
    private final Integer port;

    public RemoteAddress(String host, Integer port) {
        this.host = host;
        this.port = port;
    }

    public RemoteAddress(URL url) {
        host = url.getHost();
        port = url.getPort() == -1 ? 80 : url.getPort();
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RemoteAddress) {
            RemoteAddress other = (RemoteAddress) obj;
            return other.getHost().equals(host) && other.getPort() == port;
        } else
            return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        return sb.append(host).append(":").append(port).toString();
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
