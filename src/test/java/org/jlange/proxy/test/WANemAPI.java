package org.jlange.proxy.test;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;

public class WANemAPI {

    public static void main(String[] args) {
        WANemAPI api = new WANemAPI("http://192.168.178.32/");
        api.set(500, 12, 1);
        System.out.println(api.getBandwidth());
        System.out.println(api.getDelay());
        System.out.println(api.getPacketLoss());
        api.finalize();

        WANemAPI api2 = new WANemAPI("http://192.168.178.32/");
        api2.set(0, 0, 0);
        System.out.println(api2.getBandwidth());
        System.out.println(api2.getDelay());
        System.out.println(api2.getPacketLoss());
        api2.finalize();

    }

    private final WebDriver driver;
    private final String    baseUrl;

    /**
     * Get a new API connector to WANem
     * 
     * @param baseUrl
     *            The URL to the host. e.g. "http://192.168.178.32/"
     */
    public WANemAPI(String baseUrl) {
        // this.driver = new FirefoxDriver();
        this.driver = new HtmlUnitDriver();
        this.baseUrl = baseUrl;
    }

    /**
     * Setting some values to WANem on the webinterface
     * 
     * @param bandwidth
     *            the bandwidth in kbit/sec
     * @param delay
     *            the delay in ms
     * @param packetLoss
     *            the packet loss in percent
     */
    public void set(int bandwidth, int delay, int packetLoss) {
        driver.get(baseUrl + "/WANem/start_advance.php");
        driver.findElement(By.name("btnAdvanced")).click();
        driver.findElement(By.name("txtDelay1")).clear();
        driver.findElement(By.name("txtDelay1")).sendKeys(String.valueOf(delay));
        driver.findElement(By.name("txtLoss1")).clear();
        driver.findElement(By.name("txtLoss1")).sendKeys(String.valueOf(packetLoss));
        driver.findElement(By.name("txtBandwidth1")).clear();
        driver.findElement(By.name("txtBandwidth1")).sendKeys(String.valueOf(bandwidth));
        driver.findElement(By.name("btnApply")).click();
    }

    /**
     * Get the current bandwidth set in WANem
     * 
     * @return int in kbit/sec
     */
    public int getBandwidth() {
        return getField("txtBandwidth1");
    }

    /**
     * Get the current packet loss rate in WANem
     * 
     * @return int in percent
     */
    public int getPacketLoss() {
        return getField("txtLoss1");
    }

    /**
     * Get the current delay in WANem
     * 
     * @return int in ms
     */
    public int getDelay() {
        return getField("txtDelay1");
    }

    private int getField(String fieldName) {
        driver.get(baseUrl + "/WANem/start_advance.php");
        driver.findElement(By.name("btnAdvanced")).click();
        // driver.get(baseUrl + "/WANem/index-advanced.php");

        String value = driver.findElement(By.name(fieldName)).getAttribute("value");

        if (value == null || value.equals("")) {
            value = "0";
        }

        return Integer.valueOf(value);
    }

    @Override
    protected void finalize() {
        driver.close();
        try {
            super.finalize();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
