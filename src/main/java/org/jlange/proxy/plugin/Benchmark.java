package org.jlange.proxy.plugin;

import java.util.HashMap;
import java.util.Map;

public class Benchmark {

    private static Benchmark        instance;

    private final Map<String, Long> startValues;
    private final Map<String, Long> summaries;

    private Benchmark() {
        startValues = new HashMap<String, Long>();
        summaries = new HashMap<String, Long>();
    }

    public static Benchmark getInstance() {
        if (instance == null) {
            instance = new Benchmark();
        }

        return instance;
    }

    public void start(final String name) {
        startValues.put(name, System.nanoTime());
    }

    public Long stop(final String name) {
        Long stop = System.nanoTime();

        if (!startValues.containsKey(name))
            throw new IndexOutOfBoundsException();

        Long start = startValues.get(name);
        Long time = (stop - start) / 1000000L;

        Long total = getTotal(name);
        total += time;
        summaries.put(name, total);

        return time;
    }

    public Long getTotal(final String name) {
        if (!startValues.containsKey(name))
            throw new IndexOutOfBoundsException();

        if (!summaries.containsKey(name))
            summaries.put(name, new Long(0));

        return summaries.get(name);
    }
}
