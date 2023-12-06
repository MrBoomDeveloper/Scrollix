window.scrollix = {
    getTheme(name) {
        return `{}`;
    },
    
    getCurrentThemeName() {
        return "default";
    },
    
    getSearchSuggestions(query) {
        
    },
    
    getCurrentTheme() {
        return JSON.stringify({
            background: "black"
        });
    },
    
    setThemeData(name, data) {
        alert("Successfully saved!");
    }
};