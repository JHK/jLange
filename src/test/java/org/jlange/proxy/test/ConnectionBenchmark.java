package org.jlange.proxy.test;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

public class ConnectionBenchmark {

    // prewarm benchmarks (e.g. fill caches, filter out first connection try)
    private final static boolean WARMUP         = true;
    private final static int     TIMES          = 20;
    private final static int     SLEEP_INTERVAL = 50;

    private final FirefoxDriver  driver;
    private final URL            url;
    private Date                 start;
    private StatisticalSummary   browserSummary;
    private StatisticalSummary   rttSummary;

    public static void main(String[] args) throws MalformedURLException {
        ConnectionBenchmark b = new ConnectionBenchmark("http://www.google.de");
        // ConnectionBenchmark b = new ConnectionBenchmark("http://192.168.178.2/index.html");
        // ConnectionBenchmark b = new ConnectionBenchmark("http://checker.samair.ru/");
        // ConnectionBenchmark b = new ConnectionBenchmark("http://www.spiegel.de");

        b.run();
        b.printSummary(System.out);
    }

    public ConnectionBenchmark(URL url) {
        this.url = url;
        this.driver = new FirefoxDriver(getCapabilities());
    }

    public ConnectionBenchmark(String url) throws MalformedURLException {
        this.url = new URL(url);
        this.driver = new FirefoxDriver(getCapabilities());

    }

    private DesiredCapabilities getCapabilities() {
        Proxy proxy = new Proxy();
        proxy.setHttpProxy("proxy.fritz.box:8080");

        DesiredCapabilities cap = new DesiredCapabilities();
        cap.setCapability(CapabilityType.PROXY, proxy);

        return cap;
    }

    public void run() {
        start = new Date(System.currentTimeMillis());

        rttBenchmarkRun();
        browserBenchmarkRun();

        driver.close();
    }

    public void printSummary(PrintStream ps) {
        ps.println("--- Summary ---");
        ps.println("Url:\t\t" + this.url);
        ps.println("Started:\t" + start.toString());
        ps.println("Finished:\t" + new Date(System.currentTimeMillis()).toString());

        ps.println("\nRTT statistics:");
        displayStatisticalSummary(ps, rttSummary);

        ps.println("\nBrowser statistics:");
        displayStatisticalSummary(ps, browserSummary);
    }

    public String getUrl() {
        return this.url.toString();
    }

    public StatisticalSummary getBrowserSummary() {
        return this.browserSummary;
    }

    public StatisticalSummary getRttSummary() {
        return this.rttSummary;
    }

    private void displayStatisticalSummary(PrintStream ps, StatisticalSummary summary) {
        if (summary == null)
            return;

        List<String> result = new LinkedList<String>();
        result.add("Iterations:\t" + Math.round(summary.getN()));
        result.add("Min:\t\t" + Math.round(summary.getMin()));
        result.add("Mean:\t\t" + Math.round(summary.getMean()));
        result.add("Max:\t\t" + Math.round(summary.getMax()));
        result.add("Stddev:\t\t" + Math.round(summary.getStandardDeviation()));

        StringBuilder sb = new StringBuilder();
        for (String s : result) {
            sb.append(s + "\n");
        }
        ps.println(sb.toString());
    }

    private void browserBenchmarkRun() {
        if (WARMUP) {
            driver.get(getUrl());
            sleep(1000);

            for (int i = 0; i < 2; i++)
                driver.get(getUrl());
        }

        SummaryStatistics summary = new SummaryStatistics();

        for (int i = 0; i < TIMES; i++) {
            long t0 = System.nanoTime();
            driver.get(getUrl());
            summary.addValue((System.nanoTime() - t0) / 1000000L);
            sleep(SLEEP_INTERVAL);
        }

        this.browserSummary = summary.getSummary();
    }

    private void rttBenchmarkRun() {
        SummaryStatistics summary = new SummaryStatistics();

        if (WARMUP)
            for (int i = 0; i < 2; i++)
                ping(url.getHost());

        for (int i = 0; i < TIMES; i++) {
            long t0 = System.nanoTime();
            ping(url.getHost());
            summary.addValue((System.nanoTime() - t0) / 1000000L);
            sleep(SLEEP_INTERVAL);
        }

        this.rttSummary = summary.getSummary();
    }

    private void ping(String host) {
        try {
            Socket so = new Socket(InetAddress.getByName(host), 80);
            so.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sleep(int duration) {
        // give the system time load anything
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
