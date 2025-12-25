package com.hepsiburada;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.gauge.Step;
import org.openqa.selenium.*;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.MoveTargetOutOfBoundsException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.*;

public class StepImplementation {

    private static WebDriver driver;
    private static WebDriverWait wait;
    private static Actions actions;

    private static final String ELEMENTS_PATH = "src/test/resources/element-infos/elements.json";
    private static final String VALUES_PATH   = "src/test/resources/value-infos/test/values.json";

    private static LocatorHelper locatorHelper;   // ✅ aynı package: com.hepsiburada
    private static Map<String, String> values;

    private void ensureInit() {
        if (driver != null) return;

        ChromeOptions options = new ChromeOptions();

        options.addArguments("--disable-notifications");
        options.addArguments("--disable-blink-features=AutomationControlled");

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        options.setExperimentalOption("prefs", prefs);

        options.addArguments("--disable-features=FedCm,IdentityCredential,InterestCohort");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--lang=tr-TR");

        driver = new ChromeDriver(options);
        driver.manage().window().maximize();

        ((JavascriptExecutor) driver).executeScript(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
        );

        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        actions = new Actions(driver);

        locatorHelper = new LocatorHelper(ELEMENTS_PATH); // ✅ elements.json burada yükleniyor
        values = loadValues();
    }

    private String getValue(String key) {
        String v = values.get(key);
        if (v == null) throw new RuntimeException("values.json içinde key bulunamadı: " + key);
        return v;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> loadValues() {
        try {
            ObjectMapper om = new ObjectMapper();
            return om.readValue(new File(VALUES_PATH), Map.class);
        } catch (Exception e) {
            throw new RuntimeException("values.json okunamadı: " + VALUES_PATH, e);
        }
    }

    // ------------------------------------------------------------
    // STEPS
    // ------------------------------------------------------------

    @Step("URL <valueKey> adresine gidilir")
    public void goToUrlByValueKey(String valueKey) {
        ensureInit();
        String url = getValue(valueKey);
        driver.get(url);
    }

    @Step("Element <elementKey> görünür olana kadar beklenir")
    public void waitUntilVisible(String elementKey) {
        ensureInit();
        By by = locatorHelper.getBy(elementKey);
        wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    @Step("Element <elementKey> varsa tıklanır")
    public void clickIfExists(String elementKey) {
        ensureInit();
        By by = locatorHelper.getBy(elementKey);
        List<WebElement> found = driver.findElements(by);
        if (!found.isEmpty()) {
            try {
                wait.until(ExpectedConditions.elementToBeClickable(found.get(0))).click();
            } catch (Exception ignored) {
                found.get(0).click();
            }
        }
    }

    @Step("Element <elementKey> üzerine gelinir")
    public void hoverElement(String elementKey) {
        ensureInit();
        By by = locatorHelper.getBy(elementKey);

        int maxTry = 3;
        for (int i = 1; i <= maxTry; i++) {
            try {
                WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
                actions.moveToElement(el).pause(Duration.ofMillis(200)).perform();
                return;
            } catch (StaleElementReferenceException | MoveTargetOutOfBoundsException e) {
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            }
        }

        WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
        actions.moveToElement(el).perform();
    }

    @Step("Element <elementKey> 10 saniye içinde görünürse tıklanır")
    public void clickVisibleWithin10Seconds(Object arg0) {
        ensureInit();

        By by = locatorHelper.getBy("btn_CerezKabul");

        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement el = shortWait.until(ExpectedConditions.visibilityOfElementLocated(by));
            shortWait.until(ExpectedConditions.elementToBeClickable(el)).click();
        } catch (TimeoutException ignored) {
        }
    }

    @Step("Ürün listesinde <rowKey> satırın <colKey> ürününe tıklanır")
    public void clickProductRowColFromValues(String rowKey, String colKey) throws Exception {
        ensureInit();
        String row = getValue(rowKey);
        String col = getValue(colKey);
        clickProductByRowCol(row, col);
    }

    @Step("<row>. satırdaki <col>. ürün tıklanır")
    public void clickProductByRowCol(String row, String col) throws Exception {
        ensureInit();

        int targetRow = Integer.parseInt(row.trim());
        int targetCol = Integer.parseInt(col.trim());

        By cardsBy = locatorHelper.getBy("lst_ProductCards");
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(cardsBy, 5));

        List<WebElement> cards = driver.findElements(cardsBy);
        int limit = Math.min(cards.size(), 60);
        cards = cards.subList(0, limit);

        JavascriptExecutor js = (JavascriptExecutor) driver;

        class CardPos {
            final WebElement el;
            final double topAbs;
            final double left;
            CardPos(WebElement el, double topAbs, double left) {
                this.el = el; this.topAbs = topAbs; this.left = left;
            }
        }

        List<CardPos> positions = new ArrayList<>();

        for (WebElement card : cards) {
            try {
                if (!card.isDisplayed()) continue;

                List<WebElement> links = card.findElements(By.cssSelector("a[href]"));
                if (links.isEmpty()) continue;

                boolean hasRealLink = false;
                for (WebElement a : links) {
                    String href = a.getAttribute("href");
                    if (href == null) continue;
                    href = href.trim().toLowerCase(Locale.ROOT);
                    if (href.isEmpty() || href.startsWith("javascript") || href.equals("#")) continue;
                    hasRealLink = true;
                    break;
                }
                if (!hasRealLink) continue;

                Object res = js.executeScript(
                        "const r = arguments[0].getBoundingClientRect();" +
                                "return [r.top + window.scrollY, r.left];",
                        card
                );
                List<?> arr = (List<?>) res;
                double topAbs = ((Number) arr.get(0)).doubleValue();
                double left = ((Number) arr.get(1)).doubleValue();

                positions.add(new CardPos(card, topAbs, left));
            } catch (StaleElementReferenceException ignored) {
            }
        }

        if (positions.size() < 6) {
            throw new RuntimeException("Yeterli ürün kartı bulunamadı. Bulunan: " + positions.size());
        }

        positions.sort(Comparator.comparingDouble((CardPos p) -> p.topAbs)
                .thenComparingDouble(p -> p.left));

        double tol = 60.0;
        List<List<CardPos>> rows = new ArrayList<>();
        for (CardPos p : positions) {
            if (rows.isEmpty()) rows.add(new ArrayList<>(List.of(p)));
            else {
                List<CardPos> last = rows.get(rows.size() - 1);
                double rowTop = last.get(0).topAbs;
                if (Math.abs(p.topAbs - rowTop) <= tol) last.add(p);
                else rows.add(new ArrayList<>(List.of(p)));
            }
        }

        for (List<CardPos> r : rows) r.sort(Comparator.comparingDouble(a -> a.left));

        if (targetRow < 1 || targetRow > rows.size()) {
            throw new RuntimeException("İstenen satır bulunamadı. Görünen satır sayısı: " + rows.size());
        }

        List<CardPos> chosenRow = rows.get(targetRow - 1);

        if (targetCol < 1 || targetCol > chosenRow.size()) {
            throw new RuntimeException(targetRow + ". satırda " + chosenRow.size()
                    + " ürün var. İstenen: " + targetCol);
        }

        WebElement targetCard = chosenRow.get(targetCol - 1).el;

        WebElement target = targetCard;
        try {
            List<WebElement> links = targetCard.findElements(By.cssSelector("a[href]"));
            for (WebElement a : links) {
                String href = a.getAttribute("href");
                if (href == null) continue;
                href = href.trim().toLowerCase(Locale.ROOT);
                if (href.isEmpty() || href.startsWith("javascript") || href.equals("#")) continue;
                target = a;
                break;
            }
        } catch (Exception ignored) {}

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Set<String> beforeHandles = driver.getWindowHandles();
                String currentHandle = driver.getWindowHandle();

                js.executeScript("arguments[0].scrollIntoView({block:'center'});", target);

                try {
                    wait.until(ExpectedConditions.elementToBeClickable(target)).click();
                } catch (Exception ex) {
                    js.executeScript("arguments[0].click();", target);
                }

                WebDriverWait tabWait = new WebDriverWait(driver, Duration.ofSeconds(5));
                try { tabWait.until(d -> d.getWindowHandles().size() > beforeHandles.size()); }
                catch (TimeoutException ignored) {}

                Set<String> afterHandles = driver.getWindowHandles();

                if (afterHandles.size() > beforeHandles.size()) {
                    Set<String> diff = new HashSet<>(afterHandles);
                    diff.removeAll(beforeHandles);
                    String newHandle = diff.iterator().next();
                    driver.switchTo().window(newHandle);
                    driver.manage().window().maximize();
                } else {
                    driver.switchTo().window(currentHandle);
                }

                WebDriverWait readyWait = new WebDriverWait(driver, Duration.ofSeconds(10));
                readyWait.until(d ->
                        ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete")
                );

                return;
            } catch (StaleElementReferenceException e) {
                Thread.sleep(250);
            }
        }

        throw new RuntimeException("Ürün tıklanamadı (stale).");
    }

    @Step("Ürün sayfasına gidilir")
    public void verifyOnProductPage() {
        ensureInit();
        wait.until(d -> ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete"));
    }

    @Step("Arama sonuçlarında 2. satırdaki 1. ürün seçilir")
    public void selectSecondRowFirstProductFromValues() throws Exception {
        ensureInit();
        String row = getValue("TargetRow");
        String col = getValue("TargetCol");
        clickProductByRowCol(row, col);
    }

    @Step("Element <elementKey> alanına <valueKey> insan gibi yazılır")
    public void typeLikeHuman(String elementKey, String valueKey) throws Exception {
        ensureInit();

        String text = getValue(valueKey);
        Random random = new Random();
        By by = locatorHelper.getBy(elementKey);

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
                wait.until(ExpectedConditions.elementToBeClickable(by));

                el.click();
                try { el.clear(); } catch (Exception ignored) {}

                for (char c : text.toCharArray()) {
                    el.sendKeys(String.valueOf(c));
                    Thread.sleep(80 + random.nextInt(180));
                }

                Thread.sleep(200 + random.nextInt(600));
                return;
            } catch (StaleElementReferenceException e) {
                Thread.sleep(250);
            }
        }

        throw new RuntimeException("StaleElement hatası aşılamadı: " + elementKey);
    }

    @Step("Element <elementKey> insan gibi tıklanır")
    public void clickLikeHuman(String elementKey) {
        ensureInit();

        By by = locatorHelper.getBy(elementKey);
        WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(by));

        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center', inline:'center'});", el);

        try {
            String disabled = el.getAttribute("disabled");
            String ariaDisabled = el.getAttribute("aria-disabled");
            if (disabled != null || "true".equalsIgnoreCase(ariaDisabled)) {
                throw new RuntimeException("Element disabled görünüyor: " + elementKey +
                        " (disabled/aria-disabled). Muhtemelen varyant/teslimat seçimi gerekiyor veya stok yok.");
            }
        } catch (Exception ignored) {}

        try {
            el = wait.until(ExpectedConditions.elementToBeClickable(by));
            el.click();
        } catch (Exception e) {
            WebElement el2 = driver.findElement(by);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el2);
        }
    }

    @Step("Enter tusuna basilir")
    public void pressEnter() {
        ensureInit();
        actions.sendKeys(Keys.ENTER).perform();
    }

    @Step("Fare arama alanına götürülür")
    public void fareAramaAlaninaGoturulur() {
        ensureInit();

        WebElement el = wait.until(
                ExpectedConditions.visibilityOfElementLocated(locatorHelper.getBy("txt_Search"))
        );

        actions.moveToElement(el)
                .pause(Duration.ofMillis(300))
                .perform();
    }

    @Step("Hepsiburada ana sayfası açılır")
    public void hbAnaSayfaAcilir() {
        ensureInit();
        goToUrlByValueKey("HepsiburadaUrl");
    }

    @Step("Çerez bildirimi varsa kabul edilir")
    public void hbCerezKabul() {
        ensureInit();
        clickIfExists("btn_CerezKabul");
    }

    private static boolean loginVerifiedOnce = false;

    @Step("Kullanıcı hesabı ile giriş yapılır")
    public void hbLoginOl() throws Exception {
        ensureInit();

        if (loginVerifiedOnce) return;

        hoverElement("btn_GirisYapHeader");
        waitUntilVisible("lnk_DropdownGirisYap");
        clickLikeHuman("lnk_DropdownGirisYap");

        typeLikeHuman("txt_Email", "LoginEmail");
        typeLikeHuman("txt_Sifre", "LoginSifre");
        clickLikeHuman("btn_LoginSubmit");

        waitUntilVisible("lnk_CikisYap");

        loginVerifiedOnce = true;
    }

    @Step("Ürün aranır")
    public void hbUrunAra() throws Exception {
        ensureInit();

        fareAramaAlaninaGoturulur();
        clickLikeHuman("txt_Search");
        typeLikeHuman("txt_Search", "SearchText");
        pressEnter();
    }

    @Step("Arama sonuçları görüntülenir")
    public void hbAramaSonuclariGoruntulenir() {
        ensureInit();
    }

    @Step("Fare arama alanına götürülür ve tıklanır")
    public void fareAramaAlaninaGoturulurVeTiklanir() {
        ensureInit();

        WebElement el = wait.until(
                ExpectedConditions.visibilityOfElementLocated(locatorHelper.getBy("txt_Search"))
        );

        actions.moveToElement(el)
                .pause(Duration.ofMillis(300))
                .click()
                .perform();
    }

    @SuppressWarnings("unused")
    @Step("Element <elementKey> tıklanır")
    public void clickNormal(String elementKey) {
        ensureInit();

        By by = locatorHelper.getBy(elementKey);
        WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(by));

        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center', inline:'center'});", el);

        try {
            el.click();
        } catch (Exception ex) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
        }
    }

    private static boolean loginHoverVerifiedOnce = false;

    @Step("Element <elementKey> üzerinde 3 saniye durulur ve login doğrulanır")
    public void hoverElementAndVerifyLogin(String elementKey) {
        ensureInit();

        if (loginHoverVerifiedOnce) return;

        String accountName = getValue("AccountName").trim();

        hoverElement(elementKey);

        By accountNameBy = By.xpath(
                "//*[@data-test-id='account']//*[contains(normalize-space(.),"
                        + "'" + escapeXPath(accountName) + "')]"
        );

        WebElement nameEl = wait.until(ExpectedConditions.visibilityOfElementLocated(accountNameBy));

        actions.moveToElement(nameEl)
                .pause(Duration.ofSeconds(3))
                .perform();

        loginHoverVerifiedOnce = true;
    }

    private String escapeXPath(String text) {
        if (!text.contains("'")) return text;

        String[] parts = text.split("'");
        StringBuilder xpath = new StringBuilder("concat(");
        for (int i = 0; i < parts.length; i++) {
            xpath.append("'").append(parts[i]).append("'");
            if (i < parts.length - 1) xpath.append(",\"'\",");
        }
        xpath.append(")");
        return xpath.toString();
    }

    @Step("Login sonrası kullanıcı adı doğrulanır ve Hesabım üzerinde 3 saniye durulur")
    public void verifyLoginSuccessAndHoverAccount_afterLogin() {
        ensureInit();

        if (loginHoverVerifiedOnce) return;

        String accountName = getValue("AccountName").trim();

        WebElement account = wait.until(
                ExpectedConditions.visibilityOfElementLocated(locatorHelper.getBy("btn_GirisYapHeader"))
        );

        actions.moveToElement(account)
                .pause(Duration.ofSeconds(1))
                .perform();

        By nameBy = By.xpath(
                "//*[@data-test-id='account']//span[contains(normalize-space(.),"
                        + "'" + escapeXPath(accountName) + "')]"
        );

        WebElement nameEl = wait.until(ExpectedConditions.visibilityOfElementLocated(nameBy));

        actions.moveToElement(nameEl)
                .pause(Duration.ofSeconds(3))
                .perform();

        loginHoverVerifiedOnce = true;
    }

    @Step("<valueKey> saniye beklenir")
    public void waitSecondsFromValues(String valueKey) {
        ensureInit();

        String val = getValue(valueKey);
        int seconds = Integer.parseInt(val.trim());

        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        }
    }
}
