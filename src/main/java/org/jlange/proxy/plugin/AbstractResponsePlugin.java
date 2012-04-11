package org.jlange.proxy.plugin;

import java.nio.charset.Charset;
import java.util.regex.Pattern;

import org.jboss.netty.handler.codec.http.HttpResponse;

public abstract class AbstractResponsePlugin {

    private Pattern       htmlContentType = Pattern.compile("text/html.*?");
    private static String html;

    public AbstractResponsePlugin(HttpResponse response) {
        setHtml(response);
    }

    protected String getHtml() {
        return html;
    }

    private void setHtml(HttpResponse response) {
        if (html != null)
            return;

        // TODO use response.getContentType();
        String contentType = response.getHeader("content-type");
        if (!htmlContentType.matcher(contentType).matches())
            return;

        // TODO: does this really work?
        html = response.getContent().toString(Charset.defaultCharset());
    }

    abstract public void run();
}
