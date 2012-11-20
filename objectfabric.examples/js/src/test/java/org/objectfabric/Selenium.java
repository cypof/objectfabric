/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.objectfabric;

import java.io.File;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerList;
import org.mortbay.jetty.handler.ResourceHandler;
import org.mortbay.resource.ResourceCollection;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import com.google.common.base.Function;

// TODO: finish
public class Selenium {

    private static org.mortbay.jetty.Server _jetty;

    private static ChromeDriverService _service;

    private WebDriver _driver;

    @BeforeClass
    public static void startServices() throws Exception {
        ResourceCollection dirs = new ResourceCollection( //
                new String[] { "src/main/resources", "src/main/js", });
        ResourceHandler resources = new ResourceHandler();
        resources.setBaseResource(dirs);
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { resources, new DefaultHandler() });
        _jetty = new org.mortbay.jetty.Server(8080);
        _jetty.setHandler(handlers);
        _jetty.start();

        String home = System.getProperty("user.home");

        _service = new ChromeDriverService.Builder() //
                .usingAnyFreePort() //
                .usingDriverExecutable(new File(home + "/libs/chromedriver.exe")) //
                .build();

        _service.start();
    }

    @AfterClass
    public static void createAndStopService() throws Exception {
        _service.stop();
        _jetty.stop();
    }

    @Before
    public void createDriver() {
        _driver = new RemoteWebDriver(_service.getUrl(), DesiredCapabilities.chrome());
    }

    @After
    public void quitDriver() {
        _driver.quit();
    }

    @Test
    public void testHelloWorld() {
        _driver.get("http://www.google.com");
        Wait<WebDriver> wait = getWait();

        wait.until(new Function<WebDriver, Object>() {

            @Override
            public Object apply(WebDriver _) {
                WebElement text = _driver.findElement(By.name("text"));
                return "Hello World!".equals(text) ? this : null;
            }
        });

        // WebElement text = _driver.findElement(By.name("text"));

        // searchBox.sendKeys("webdriver");
        // searchBox.quit();
        // assertEquals("webdriver - Google Search", _driver.getTitle());
    }

    private Wait<WebDriver> getWait() {
        return new FluentWait<WebDriver>(_driver) //
                .withTimeout(5, TimeUnit.SECONDS) //
                .pollingEvery(1, TimeUnit.SECONDS) //
                .ignoring(NoSuchElementException.class);
    }

    private WebElement fluentWait(final By locator) {
        Wait<WebDriver> wait = new FluentWait<WebDriver>(_driver) //
                .withTimeout(5, TimeUnit.SECONDS) //
                .pollingEvery(100, TimeUnit.MILLISECONDS) //
                .ignoring(NoSuchElementException.class);

        WebElement foo = wait.until(new Function<WebDriver, WebElement>() {

            public WebElement apply(WebDriver driver) {
                return driver.findElement(locator);
            }
        });

        return foo;
    }
}
