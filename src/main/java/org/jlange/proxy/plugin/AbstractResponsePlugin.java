package org.jlange.proxy.plugin;

import java.nio.charset.Charset;
import java.util.regex.Pattern;

import org.jboss.netty.handler.codec.http.HttpResponse;

public abstract class AbstractResponsePlugin {

    private static String html;
    private final Pattern htmlContentType = Pattern.compile("text/html.*?");

    public AbstractResponsePlugin(HttpResponse response) {
        setHtml(response);
    }

    protected String getHtml() {
        return AbstractResponsePlugin.html;
    }

    abstract public void run();

    private void setHtml(HttpResponse response) {
        if (AbstractResponsePlugin.html != null) return;

        // TODO use response.getContentType();
        String contentType = response.getHeader("content-type");
        if (!htmlContentType.matcher(contentType).matches()) return;

        // TODO: does this really work?
        AbstractResponsePlugin.html = response.getContent().toString(Charset.defaultCharset());
    }
}
