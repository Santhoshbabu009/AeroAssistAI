/**
 * AeroAssist AI - E2E Web Application Selenium Test Suite
 * Validates login, chat sync, ordering, bookings, and dashboard interfaces.
 */

const { Builder, By, until } = require('selenium-webdriver');
const chrome = require('selenium-webdriver/chrome');
const assert = require('assert');

describe('AeroAssist E2E Tests', function () {
    this.timeout(30000); // 30 seconds timeout
    let driver;
    const targetUrl = 'http://localhost:5000/web/index.html'; // Dev server path

    before(async function () {
        // Setup Chrome in headless mode to prevent GUI block issues in CI pipelines
        let options = new chrome.Options();
        options.addArguments('--headless');
        options.addArguments('--no-sandbox');
        options.addArguments('--disable-dev-shm-usage');

        driver = await new Builder()
            .forBrowser('chrome')
            .setChromeOptions(options)
            .build();
    });

    after(async function () {
        if (driver) {
            await driver.quit();
        }
    });

    it('TC-001: Should load Passenger Login Form successfully', async function () {
        await driver.get(targetUrl);
        
        // Wait until login email input is visible
        const emailInput = await driver.wait(until.elementLocated(By.id('login-email')), 5000);
        assert.ok(emailInput, 'Login email field was not found on index page.');
    });

    it('TC-002: Should block invalid login credentials with alert', async function () {
        await driver.get(targetUrl);

        await driver.findElement(By.id('login-email')).sendKeys('invaliduser@gmail.com');
        await driver.findElement(By.id('login-password')).sendKeys('wrongpassword');
        await driver.findElement(By.id('btn-login')).click();

        // Check if an alert window is displayed
        await driver.wait(until.alertIsPresent(), 3000);
        let alert = await driver.switchTo().alert();
        let alertText = await alert.getText();
        
        assert.ok(alertText.includes('Failed') || alertText.includes('invalid') || alertText.includes('mismatch'), 
            `Unexpected alert message text: "${alertText}"`);
        await alert.accept();
    });

    it('TC-003: Should log in successfully as Passenger and load dashboard', async function () {
        await driver.get(targetUrl);

        await driver.findElement(By.id('login-email')).sendKeys('passenger@gmail.com');
        await driver.findElement(By.id('login-password')).sendKeys('password123');
        await driver.findElement(By.id('btn-login')).click();

        // Wait until dashboard dashboard target text or elements appear
        const dashboardText = await driver.wait(until.elementLocated(By.id('user-display-name')), 5000);
        assert.ok(dashboardText, 'Could not locate dashboard greeting.');
    });

    it('TC-004: Should send message to Chatbot and receive AI reply', async function () {
        // Navigate directly or toggle views
        await driver.findElement(By.id('tab-chat')).click();

        const inputField = await driver.findElement(By.id('chat-input-msg'));
        await inputField.sendKeys('Hello AeroAssist! Where is the food court?');
        await driver.findElement(By.id('chat-send')).click();

        // Wait until AI bubble renders in chat box
        const chatBox = await driver.wait(until.elementLocated(By.className('chat-bubble ai')), 8000);
        const text = await chatBox.getText();
        assert.ok(text.length > 0, 'AI responded with an empty chat bubble.');
    });

    it('TC-005: Should add restaurant burger item to global cart and check total price', async function () {
        await driver.findElement(By.id('tab-restaurants')).click();

        // Wait for list grid
        const restGrid = await driver.wait(until.elementLocated(By.id('restaurant-list')), 5000);
        await driver.findElement(By.className('btn-view-menu')).click();

        // Select and add Burger
        const addBtn = await driver.wait(until.elementLocated(By.className('btn-add-cart')), 5000);
        await addBtn.click();

        // Open Cart Page/View
        await driver.findElement(By.id('tab-cart')).click();
        const cartTotal = await driver.findElement(By.id('cart-total-price'));
        const totalText = await cartTotal.getText();
        
        assert.ok(parseFloat(totalText) > 0, 'Cart total price should be greater than 0.');
    });

    it('TC-006: Should place order successfully and redirect to Tracking Page', async function () {
        await driver.findElement(By.id('btn-checkout-place')).click();

        // Wait for Tracking view active stepper
        const trackingStatus = await driver.wait(until.elementLocated(By.id('tracking-step-1')), 5000);
        assert.ok(trackingStatus, 'Failed to navigate to order tracking layout.');
    });

    it('TC-007: Should switch to Vendor login and access dashboard', async function () {
        await driver.get(targetUrl);
        await driver.findElement(By.id('tab-vendor-login')).click();

        await driver.findElement(By.id('vendor-email')).sendKeys('starbucks@airport.com');
        await driver.findElement(By.id('vendor-password')).sendKeys('vendor123');
        await driver.findElement(By.id('vendor-login-btn')).click();

        // Confirm active vendor queue loads
        const orderQueueHeader = await driver.wait(until.elementLocated(By.id('vendor-queue-title')), 5000);
        assert.ok(orderQueueHeader, 'Vendor portal dashboard did not initialize.');
    });

    it('TC-008: Should accept active order in Vendor Queue', async function () {
        const acceptBtn = await driver.wait(until.elementLocated(By.className('btn-accept-order')), 5000);
        await acceptBtn.click();

        // Check if button text changes or order status changes in queue
        const progressStatus = await driver.wait(until.elementLocated(By.className('status-progress')), 5000);
        assert.ok(progressStatus, 'Order status did not transition to in progress.');
    });
});
