window.scrollix = {
    getTheme(name) {
        return `{}`;
    },

    getCurrentThemeName() {
        return "default";
    },

    updateSettings(settings) {
        browser.runtime.sendNativeMessage("scrollix", {
            action: "update-settings",
            value: settings
        });
    },

    useCustomAction(action) {
        browser.runtime.sendNativeMessage("scrollix", {
            action: "use-custom-action",
            value: action
        });
    },

    openSearch() {
        browser.runtime.sendNativeMessage("scrollix", {
            action: "open-search"
        });
    },

    getSettings() {
        return browser.runtime.sendNativeMessage("scrollix", {
            action: "get-settings"
        });
    },

    reload() {
        browser.runtime.sendNativeMessage("scrollix", {
            action: "reload"
        });
    },

    async getSearchSuggestions(query) {
        return await browser.runtime.sendNativeMessage("scrollix", {
            action: "get-search-suggestions", query
        });
    },

    getCurrentTheme() {
        return JSON.stringify({
            background: "black"
        });
    },

    setThemeData(name, data) {
        alert("Successfully saved!");
    }
}