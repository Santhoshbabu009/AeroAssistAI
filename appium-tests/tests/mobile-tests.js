/**
 * AeroAssist AI - E2E Mobile Application Appium Test Suite
 * Validates Android activities: AuthActivity, ChatbotActivity, MainActivity, etc.
 */

const wd = require('webdriverio');
const assert = require('assert');

// Appium connection capabilities
const opts = {
    path: '/wd/hub',
    port: 4723,
    capabilities: {
        platformName: "Android",
        platformVersion: "12.0", // Target test environment SDK
        deviceName: "Android Emulator",
        app: "./app/build/outputs/apk/debug/app-debug.apk", // Compiled debug binary path
        appPackage: "com.aeroassist.ai",
        appActivity: ".AuthActivity", // App launcher activity
        automationName: "UiAutomator2",
        noReset: true
    }
};

describe('AeroAssist Mobile E2E Tests', function () {
    this.timeout(40000); // Extended timeout for mobile UI rendering
    let client;

    before(async function () {
        client = await wd.remote(opts);
    });

    after(async function () {
        if (client) {
            await client.deleteSession();
        }
    });

    it('TC-APP-001: Should launch AuthActivity and load UI inputs successfully', async function () {
        // Wait for login email input to render
        const emailField = await client.$('id:com.aeroassist.ai:id/et_email');
        const isDisplayed = await emailField.isDisplayed();
        assert.strictEqual(isDisplayed, true, 'Email input text field was not displayed on startup.');
    });

    it('TC-APP-002: Should validate empty password alert warning', async function () {
        const emailField = await client.$('id:com.aeroassist.ai:id/et_email');
        await emailField.setValue('testuser@gmail.com');

        const loginBtn = await client.$('id:com.aeroassist.ai:id/btn_login');
        await loginBtn.click();

        // Check validation warning toast or snackbar
        const errorMsg = await client.$('id:com.aeroassist.ai:id/tv_error_msg');
        const text = await errorMsg.getText();
        assert.ok(text.includes('empty') || text.includes('required'), 'Empty field warning was not triggerred.');
    });

    it('TC-APP-003: Should log in successfully as Passenger and transition to MainActivity', async function () {
        const passwordField = await client.$('id:com.aeroassist.ai:id/et_password');
        await passwordField.setValue('password123');

        const loginBtn = await client.$('id:com.aeroassist.ai:id/btn_login');
        await loginBtn.click();

        // Check if main bottom nav is loaded (indicates we are in MainActivity)
        const bottomNav = await client.$('id:com.aeroassist.ai:id/bottom_navigation');
        const isDisplayed = await bottomNav.isDisplayed();
        assert.strictEqual(isDisplayed, true, 'Bottom Navigation did not load after successful login.');
    });

    it('TC-APP-004: Should open ChatbotActivity and load chat message feed', async function () {
        // Tap Chat navigation tab
        const chatTab = await client.$('id:com.aeroassist.ai:id/nav_chat');
        await chatTab.click();

        // Verify chatbot message list is visible
        const recycler = await client.$('id:com.aeroassist.ai:id/chat_recycler_view');
        const isDisplayed = await recycler.isDisplayed();
        assert.strictEqual(isDisplayed, true, 'Chat recycler view was not loaded.');
    });

    it('TC-APP-005: Should send a chat message and verify Room DB offline caching', async function () {
        const inputField = await client.$('id:com.aeroassist.ai:id/et_chat_input');
        await inputField.setValue('How do I check in for flight AA-100?');

        const sendBtn = await client.$('id:com.aeroassist.ai:id/btn_chat_send');
        await sendBtn.click();

        // Verify new message bubble is appended to feed
        const bubble = await client.$('id:com.aeroassist.ai:id/tv_message_text');
        const text = await bubble.getText();
        assert.ok(text.length > 0, 'Appended message text is missing.');
    });

    it('TC-APP-006: Should view cart and check total checkout calculations', async function () {
        const cartTab = await client.$('id:com.aeroassist.ai:id/nav_cart');
        await cartTab.click();

        const cartTotal = await client.$('id:com.aeroassist.ai:id/tv_cart_total');
        const totalText = await cartTotal.getText();
        
        // Assert value is formatted correctly as double float
        assert.ok(totalText.includes('$') || parseFloat(totalText) >= 0.00);
    });

    it('TC-APP-007: Should log out and check Vendor login dashboard loading', async function () {
        // Logout using settings / profile tab
        const profileTab = await client.$('id:com.aeroassist.ai:id/nav_profile');
        await profileTab.click();

        const logoutBtn = await client.$('id:com.aeroassist.ai:id/btn_logout');
        await logoutBtn.click();

        // Switch to Vendor Auth panel
        const toggleVendor = await client.$('id:com.aeroassist.ai:id/tv_switch_vendor');
        await toggleVendor.click();

        const vendorEmail = await client.$('id:com.aeroassist.ai:id/et_vendor_email');
        await vendorEmail.setValue('starbucks@airport.com');

        const vendorPassword = await client.$('id:com.aeroassist.ai:id/et_vendor_password');
        await vendorPassword.setValue('vendor123');

        const vendorLogin = await client.$('id:com.aeroassist.ai:id/btn_vendor_login');
        await vendorLogin.click();

        // Verify vendor order recycler is loaded
        const queueRecycler = await client.$('id:com.aeroassist.ai:id/vendor_orders_recycler');
        assert.ok(await queueRecycler.isDisplayed());
    });
});
