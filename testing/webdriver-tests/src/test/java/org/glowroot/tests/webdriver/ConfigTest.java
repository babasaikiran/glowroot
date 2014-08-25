/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.tests.webdriver;

import com.saucelabs.common.SauceOnDemandAuthentication;
import com.saucelabs.common.SauceOnDemandSessionIdProvider;
import com.saucelabs.junit.SauceOnDemandTestWatcher;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.runner.RunWith;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.server.SeleniumServer;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.glowroot.Containers;
import org.glowroot.container.Container;
import org.glowroot.container.config.UserInterfaceConfig;
import org.glowroot.tests.webdriver.config.ConfigSidebar;
import org.glowroot.tests.webdriver.config.GeneralPage;
import org.glowroot.tests.webdriver.config.PointcutListPage;
import org.glowroot.tests.webdriver.config.PointcutSection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@RunWith(WebDriverRunner.class)
public class ConfigTest {

    private static final boolean USE_LOCAL_IE = false;

    private static final TestName testNameWatcher = new TestName();

    private static Container container;
    private static SeleniumServer seleniumServer;
    private static WebDriver driver;

    private String remoteWebDriverSessionId;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.getSharedContainer();
        if (SauceLabs.useSauceLabs()) {
            // glowroot must listen on one of the ports that sauce connect proxies
            // see https://saucelabs.com/docs/connect#localhost
            UserInterfaceConfig userInterfaceConfig =
                    container.getConfigService().getUserInterfaceConfig();
            userInterfaceConfig.setPort(4000);
            container.getConfigService().updateUserInterfaceConfig(userInterfaceConfig);
        } else {
            seleniumServer = new SeleniumServer();
            seleniumServer.start();
            // single webdriver instance for much better performance
            if (USE_LOCAL_IE) {
                // currently tests fail with default nativeEvents=true
                // (can't select radio buttons on pointcut config page)
                DesiredCapabilities capabilities = DesiredCapabilities.internetExplorer();
                capabilities.setCapability("nativeEvents", false);
                driver = new InternetExplorerDriver(capabilities);
            } else {
                driver = new FirefoxDriver();
            }
            // 992 is bootstrap media query breakpoint for screen-md-min
            // 1200 is bootstrap media query breakpoint for screen-lg-min
            driver.manage().window().setSize(new Dimension(1200, 800));
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (!SauceLabs.useSauceLabs()) {
            driver.quit();
            seleniumServer.stop();
        }
        container.close();
    }

    @Before
    public void beforeEachTest() throws Exception {
        if (SauceLabs.useSauceLabs()) {
            // need separate webdriver instance per test in order to report each test separately in
            // saucelabs
            String testName = getClass().getName() + '.' + testNameWatcher.getMethodName();
            driver = SauceLabs.getWebDriver(testName);
            // need to capture sessionId since it is needed in sauceLabsTestWatcher, after
            // driver.quit() is called
            remoteWebDriverSessionId = ((RemoteWebDriver) driver).getSessionId().toString();
        }
    }

    @After
    public void afterEachTest() throws Exception {
        if (SauceLabs.useSauceLabs()) {
            driver.quit();
        }
        container.checkAndReset();
    }

    @Rule
    public TestWatcher getTestNameWatcher() {
        return testNameWatcher;
    }

    @Rule
    public TestWatcher getSauceLabsTestWatcher() {
        if (!SauceLabs.useSauceLabs()) {
            return null;
        }
        String sauceUsername = System.getenv("SAUCE_USERNAME");
        String sauceAccessKey = System.getenv("SAUCE_ACCESS_KEY");
        SauceOnDemandAuthentication authentication =
                new SauceOnDemandAuthentication(sauceUsername, sauceAccessKey);
        SauceOnDemandSessionIdProvider sessionIdProvider =
                new SauceOnDemandSessionIdProvider() {
                    @Override
                    public String getSessionId() {
                        return remoteWebDriverSessionId;
                    }
                };
        return new SauceOnDemandTestWatcher(sessionIdProvider, authentication);
    }

    @Test
    public void shouldUpdateGeneral() throws Exception {
        // given
        App app = new App(driver, "http://localhost:" + container.getUiPort());
        GlobalNavbar globalNavbar = new GlobalNavbar(driver);
        GeneralPage generalPage = new GeneralPage(driver);

        app.open();
        globalNavbar.getConfigurationLink().click();

        // when
        generalPage.getEnabledSwitchOff().click();
        generalPage.getStoreThresholdTextField().clear();
        generalPage.getStoreThresholdTextField().sendKeys("2345");
        generalPage.getStuckThresholdTextField().clear();
        generalPage.getStuckThresholdTextField().sendKeys("3456");
        generalPage.getMaxSpansTextField().clear();
        generalPage.getMaxSpansTextField().sendKeys("4567");
        generalPage.getSaveButton().click();
        // wait for save to complete
        new WebDriverWait(driver, 30).until(ExpectedConditions.not(
                ExpectedConditions.elementToBeClickable(generalPage.getSaveButton())));

        // then
        app.open();
        globalNavbar.getConfigurationLink().click();
        // need to give angular view a chance to render before assertions
        Thread.sleep(100);
        assertThat(generalPage.getEnabledSwitchOn().getAttribute("class").split(" "))
                .doesNotContain("active");
        assertThat(generalPage.getEnabledSwitchOff().getAttribute("class").split(" "))
                .contains("active");
        assertThat(generalPage.getStoreThresholdTextField().getAttribute("value"))
                .isEqualTo("2345");
        assertThat(generalPage.getStuckThresholdTextField().getAttribute("value"))
                .isEqualTo("3456");
        assertThat(generalPage.getMaxSpansTextField().getAttribute("value")).isEqualTo("4567");
    }

    @Test
    public void shouldAddPointcut() throws Exception {
        // given
        App app = new App(driver, "http://localhost:" + container.getUiPort());
        GlobalNavbar globalNavbar = new GlobalNavbar(driver);
        ConfigSidebar configSidebar = new ConfigSidebar(driver);
        PointcutListPage pointcutListPage = new PointcutListPage(driver);

        app.open();
        globalNavbar.getConfigurationLink().click();
        configSidebar.getPointcutsLink().click();

        // when
        createTracePointcut(pointcutListPage);

        // then
        app.open();
        globalNavbar.getConfigurationLink().click();
        configSidebar.getPointcutsLink().click();
        PointcutSection pointcutSection = pointcutListPage.getSection(0);
        // need to give angular view a chance to render before assertions
        Thread.sleep(100);
        assertThat(pointcutSection.getClassNameTextField().getAttribute("value"))
                .isEqualTo("org.glowroot.container.AppUnderTest");
        assertThat(pointcutSection.getMethodNameTextField().getAttribute("value"))
                .isEqualTo("executeApp");
        assertThat(pointcutSection.getAdviceKindTraceRadioButton().isSelected()).isTrue();
        assertThat(pointcutSection.getMetricNameTextField().getAttribute("value"))
                .isEqualTo("a metric");
        assertThat(pointcutSection.getMessageTemplateTextField().getAttribute("value"))
                .isEqualTo("a span");
        assertThat(pointcutSection.getStackTraceThresholdTextTextField()
                .getAttribute("value")).isEqualTo("");
        assertThat(pointcutSection.getTransactionTypeTextField().getAttribute("value"))
                .isEqualTo("a type");
        assertThat(pointcutSection.getTransactionNameTemplateTextField().getAttribute("value"))
                .isEqualTo("a trace");
        assertThat(pointcutSection.getTraceStoreThresholdMillisTextField().getAttribute("value"))
                .isEqualTo("123");
    }

    @Test
    public void shouldNotValidateOnDeletePointcut() throws Exception {
        // given
        App app = new App(driver, "http://localhost:" + container.getUiPort());
        GlobalNavbar globalNavbar = new GlobalNavbar(driver);
        ConfigSidebar configSidebar = new ConfigSidebar(driver);
        PointcutListPage pointcutListPage = new PointcutListPage(driver);

        app.open();
        globalNavbar.getConfigurationLink().click();
        configSidebar.getPointcutsLink().click();
        createTracePointcut(pointcutListPage);

        app.open();
        globalNavbar.getConfigurationLink().click();
        configSidebar.getPointcutsLink().click();
        PointcutSection pointcutSection = pointcutListPage.getSection(0);
        WebElement classNameTextField = pointcutSection.getClassNameTextField();

        // when
        Utils.clearInput(pointcutSection.getMetricNameTextField());
        pointcutSection.getDeleteButton().click();

        // then
        new WebDriverWait(driver, 30).until(ExpectedConditions.stalenessOf(classNameTextField));
    }

    @Test
    public void shouldAddMetricOnlyPointcut() throws Exception {
        // given
        App app = new App(driver, "http://localhost:" + container.getUiPort());
        GlobalNavbar globalNavbar = new GlobalNavbar(driver);
        ConfigSidebar configSidebar = new ConfigSidebar(driver);
        PointcutListPage pointcutPage = new PointcutListPage(driver);

        app.open();
        globalNavbar.getConfigurationLink().click();
        configSidebar.getPointcutsLink().click();

        // when
        createMetricPointcut(pointcutPage);

        // then
        app.open();
        globalNavbar.getConfigurationLink().click();
        configSidebar.getPointcutsLink().click();
        PointcutSection pointcutSection = pointcutPage.getSection(0);
        // need to give angular view a chance to render before assertions
        Thread.sleep(100);
        assertThat(pointcutSection.getClassNameTextField().getAttribute("value"))
                .isEqualTo("org.glowroot.container.AppUnderTest");
        assertThat(pointcutSection.getMethodNameTextField().getAttribute("value"))
                .isEqualTo("executeApp");
        assertThat(pointcutSection.getAdviceKindMetricRadioButton().isSelected()).isTrue();
        assertThat(pointcutSection.getMetricNameTextField().getAttribute("value"))
                .isEqualTo("a metric");
    }

    @Test
    public void shouldAddMetricAndSpanOnlyPointcut() throws Exception {
        // given
        App app = new App(driver, "http://localhost:" + container.getUiPort());
        GlobalNavbar globalNavbar = new GlobalNavbar(driver);
        ConfigSidebar configSidebar = new ConfigSidebar(driver);
        PointcutListPage pointcutListPage = new PointcutListPage(driver);

        app.open();
        globalNavbar.getConfigurationLink().click();
        configSidebar.getPointcutsLink().click();

        // when
        createSpanPointcut(pointcutListPage);

        // then
        app.open();
        globalNavbar.getConfigurationLink().click();
        configSidebar.getPointcutsLink().click();
        PointcutSection pointcutSection = pointcutListPage.getSection(0);
        // need to give angular view a chance to render before assertions
        Thread.sleep(100);
        assertThat(pointcutSection.getClassNameTextField().getAttribute("value"))
                .isEqualTo("org.glowroot.container.AppUnderTest");
        assertThat(pointcutSection.getMethodNameTextField().getAttribute("value"))
                .isEqualTo("executeApp");
        assertThat(pointcutSection.getAdviceKindSpanRadioButton().isSelected()).isTrue();
        assertThat(pointcutSection.getMetricNameTextField().getAttribute("value"))
                .isEqualTo("a metric");
        assertThat(pointcutSection.getMessageTemplateTextField().getAttribute("value"))
                .isEqualTo("a span");
        assertThat(pointcutSection.getStackTraceThresholdTextTextField()
                .getAttribute("value")).isEqualTo("");
    }

    private void createTracePointcut(PointcutListPage pointcutListPage) {
        pointcutListPage.getAddPointcutButton().click();
        PointcutSection pointcutSection = pointcutListPage.getSection(0);
        pointcutSection.getClassNameTextField().sendKeys("container.AppUnderTest");
        pointcutSection.clickClassNameAutoCompleteItem("org.glowroot.container.AppUnderTest");
        pointcutSection.getMethodNameTextField().sendKeys("exec");
        pointcutSection.clickMethodNameAutoCompleteItem("executeApp");
        pointcutSection.getAdviceKindTraceRadioButton().click();
        pointcutSection.getMetricNameTextField().clear();
        pointcutSection.getMetricNameTextField().sendKeys("a metric");
        pointcutSection.getMessageTemplateTextField().clear();
        pointcutSection.getMessageTemplateTextField().sendKeys("a span");
        pointcutSection.getTransactionTypeTextField().clear();
        pointcutSection.getTransactionTypeTextField().sendKeys("a type");
        pointcutSection.getTransactionNameTemplateTextField().clear();
        pointcutSection.getTransactionNameTemplateTextField().sendKeys("a trace");
        pointcutSection.getTraceStoreThresholdMillisTextField().clear();
        pointcutSection.getTraceStoreThresholdMillisTextField().sendKeys("123");
        pointcutSection.getAddButton().click();
        // getSaveButton() waits for the Save button to become visible (after adding is successful)
        pointcutSection.getSaveButton();
    }

    private void createMetricPointcut(PointcutListPage pointcutListPage) {
        pointcutListPage.getAddPointcutButton().click();
        PointcutSection pointcutSection = pointcutListPage.getSection(0);
        pointcutSection.getClassNameTextField().sendKeys("container.AppUnderTest");
        pointcutSection.clickClassNameAutoCompleteItem("org.glowroot.container.AppUnderTest");
        pointcutSection.getMethodNameTextField().sendKeys("exec");
        pointcutSection.clickMethodNameAutoCompleteItem("executeApp");
        pointcutSection.getAdviceKindMetricRadioButton().click();
        pointcutSection.getMetricNameTextField().clear();
        pointcutSection.getMetricNameTextField().sendKeys("a metric");
        pointcutSection.getAddButton().click();
        // getSaveButton() waits for the Save button to become visible (after adding is successful)
        pointcutSection.getSaveButton();
    }

    private void createSpanPointcut(PointcutListPage pointcutListPage) {
        pointcutListPage.getAddPointcutButton().click();
        PointcutSection pointcutSection = pointcutListPage.getSection(0);
        pointcutSection.getClassNameTextField().sendKeys("container.AppUnderTest");
        pointcutSection.clickClassNameAutoCompleteItem("org.glowroot.container.AppUnderTest");
        pointcutSection.getMethodNameTextField().sendKeys("exec");
        pointcutSection.clickMethodNameAutoCompleteItem("executeApp");
        pointcutSection.getAdviceKindSpanRadioButton().click();
        pointcutSection.getMetricNameTextField().clear();
        pointcutSection.getMetricNameTextField().sendKeys("a metric");
        pointcutSection.getMessageTemplateTextField().clear();
        pointcutSection.getMessageTemplateTextField().sendKeys("a span");
        pointcutSection.getAddButton().click();
        // getSaveButton() waits for the Save button to become visible (after adding is successful)
        pointcutSection.getSaveButton();
    }
}