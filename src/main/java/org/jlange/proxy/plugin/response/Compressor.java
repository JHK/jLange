package org.jlange.proxy.plugin.response;

import java.nio.charset.Charset;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.jlange.proxy.plugin.Tools;
import org.mozilla.javascript.EvaluatorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.htmlcompressor.compressor.HtmlCompressor;

public class Compressor implements ResponsePlugin {

    private final Logger         log = LoggerFactory.getLogger(Compressor.class);
    private final HtmlCompressor compressor;
    private String               content;
    private Charset              encoding;

    public Compressor() {
        compressor = new HtmlCompressor();
        compressor.setCompressCss(true);
        compressor.setCompressJavaScript(true);
        compressor.setRemoveComments(true);
        compressor.setRemoveIntertagSpaces(true);
        compressor.setRemoveScriptAttributes(true);
        compressor.setRemoveQuotes(true);
        compressor.setSimpleBooleanAttributes(true);

        if (log.isDebugEnabled())
            compressor.setGenerateStatistics(true);
    }

    public Boolean isApplicable(final HttpRequest request) {
        return true;
    }

    public Boolean isApplicable(final HttpResponse response) {
        return response.getStatus().equals(HttpResponseStatus.OK)
                && (Tools.isHtml(response) || Tools.isJavascript(response) || Tools.isCSS(response));
    }

    public void run(final HttpResponse response) {
        encoding = Tools.getCharset(response);
        content = response.getContent().toString(encoding);

        try {
            content = compressor.compress(content);
        } catch (EvaluatorException e) {
            log.error(e.getMessage());
        }

        if (log.isDebugEnabled()) {
            Integer savedBytes = compressor.getStatistics().getOriginalMetrics().getFilesize()
                    - compressor.getStatistics().getCompressedMetrics().getFilesize();

            log.debug(compressor.getStatistics().toString());
            log.debug("saved " + (savedBytes < 1024 ? savedBytes + " Bytes" : savedBytes / 1024 + " KiB"));
        }
    }

    public void updateResponse(HttpResponse response) {
        // check if the plugin ran successfully
        if (content == null)
            return;

        ChannelBuffer buffer = ChannelBuffers.copiedBuffer(content, encoding);
        HttpHeaders.setContentLength(response, buffer.readableBytes());
        response.setContent(buffer);
    }
}
