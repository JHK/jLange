package org.jlange.proxy;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.plugin.Benchmark;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.jlange.proxy.plugin.compressor.Compressor;
import org.littleshoot.proxy.HttpFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpResponseFilters implements org.littleshoot.proxy.HttpResponseFilters {

    private class ResponseFilter implements HttpFilter {

        private final ResponsePlugin[] responsePlugins;
        private final Logger           log = LoggerFactory.getLogger(ResponseFilter.class);

        public ResponseFilter() {
            responsePlugins = new ResponsePlugin[] { new Compressor() };
        }

        @Override
        public boolean filterResponses(final HttpRequest request) {
            boolean filter = false;

            for (ResponsePlugin plugin : responsePlugins) {
                filter = filter || plugin.filterResponses(request);
            }

            return filter;
        }

        @Override
        public HttpResponse filterResponse(final HttpRequest request, final HttpResponse response) {

            Benchmark bench = Benchmark.getInstance();

            for (ResponsePlugin plugin : responsePlugins) {
                if (plugin.isApplicable(request, response)) {
                    bench.start(plugin.toString());

                    plugin.run(request, response);
                    plugin.updateResponse(response);

                    bench.stop(plugin.toString());

                    log.debug(plugin.toString() + ": " + bench.getTotal(plugin.toString()).toString());
                }
            }

            return response;
        }

        @Override
        public int getMaxResponseSize() {
            return 2 * 1024 * 1024; // 2 MeB
        }

    }

    private ResponseFilter responseFilter;

    public HttpResponseFilters() {
        responseFilter = new ResponseFilter();
    }

    @Override
    public HttpFilter getFilter(String hostAndPort) {
        return responseFilter;
    }

}
