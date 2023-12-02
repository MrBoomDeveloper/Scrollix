window.scrollix = {
    getTheme(name) {
        return `{}`;
    },

    getCurrentThemeName() {
        return "default";
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