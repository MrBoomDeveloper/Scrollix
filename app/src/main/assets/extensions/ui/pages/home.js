const currentTheme = JSON.parse(scrollix.getCurrentTheme());
document.querySelector("html").style.backgroundColor = currentTheme.background;

const search = document.querySelector("#search");
if(search != null) {
    search.addEventListener("keypress", event => {
        if(event.key == "Enter") {
            window.open(`https://www.google.com/search?q=${search.value}`);
            return;
        }
    });
}

const timer = document.querySelector("#widget-timer");

if(timer != null) {
    updateTimer();
    setInterval(updateTimer, 1000);

    function updateTimer() {
        const date = new Date();

        timer.textContent =
            formatNumber(date.getHours()) + ":" +
            formatNumber(date.getMinutes()) + ":" +
            formatNumber(date.getSeconds());
    }
}

function formatNumber(number) {
    if(number <= 9) return "0" + number;
    return number;
}