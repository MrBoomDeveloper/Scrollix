const CALLBACK_DELAY = 75;

const sectionsEl = document.querySelector("#sections");
const titleEl = document.querySelector("#title");
const backEl = document.querySelector("#back");

let currentSectionPath = [];
let settings, values;

backEl.onclick = () => {
    if(currentSectionPath.length == 0) return;

    setTimeout(() => {
        openBackRecursively(settings, 0);
    }, CALLBACK_DELAY);
}

function openBackRecursively(state, index) {
    if(index >= currentSectionPath.length - 1) {
        setSection(state);
        currentSectionPath.pop();
        return;
    }

    openBackRecursively(state.items[currentSectionPath[index]], index + 1);
}

function setSection(data) {
    sectionsEl.innerHTML = "";
    titleEl.innerText = data.title ?? "Settings";
    backEl.style.display = (data == settings) ? "none" : "block";

    for(const [id, item] of Object.entries(data.items)) {
        const el = document.createElement("div");
        el.className = "list-item";

        switch(item.type ?? "info") {
            case "section": {
                el.innerHTML = `
                    <div class="list-item-info">
                        <p class="list-item-title">${item.title}</p>
                        ${item.description ? `<p class="list-item-description">${item.description}</p>` : ""}
                    </div>
                `;

                el.onclick = () => setTimeout(() => {
                    currentSectionPath.push(id);
                    setSection(item);
                }, CALLBACK_DELAY);
            } break;

            case "toggle": {
                render();

                function render() {
                    el.innerHTML = `
                        <div class="list-item-info">
                            <p class="list-item-title">${item.title}</p>
                            ${item.description ? `<p class="list-item-description">${item.description}</p>` : ""}
                        </div>

                        <p>${values[item.id]}</p>
                    `;
                }

                el.onclick = () => {
                    values[item.id] = !values[item.id];
                    scrollix.updateSettings(values);
                    setTimeout(() => render(), CALLBACK_DELAY);
                }
            } break;

            case "ratio": {
                render();

                function render() {
                    el.innerHTML = `
                        <div class="list-item-info">
                            <p class="list-item-title">${item.title}</p>
                            ${item.description ? `<p class="list-item-description">${item.description}</p>` : ""}
                        </div>

                        <p>${values[item.id]}</p>
                    `;
                }

                el.onclick = () => {
                    setTimeout(() => render(), CALLBACK_DELAY);
                }
            } break;

            case "action": {
                el.innerHTML = `
                    <div class="list-item-info">
                        <p class="list-item-title">${item.title}</p>
                        ${item.description ? `<p class="list-item-description">${item.description}</p>` : ""}
                    </div>
                `;

                el.onclick = () => {
                    alert("not available currently!");
                }
            } break;

            default: {
                el.innerHTML = `
                    <div class="list-item-info">
                        <p class="list-item-title">${item.title}</p>
                        ${item.description ? `<p class="list-item-description">${item.description}</p>` : ""}
                    </div>
                `;
            } break;
        }

        sectionsEl.appendChild(el);
    }
}

const port = browser.runtime.connectNative("scrollix");
port.onMessage.addListener(response => {
    switch(response.action) {
        case "retrieve-settings": {
            const [newSettingsInfo, newSettingsValues] = response.values;
            settings = newSettingsInfo;
            values = newSettingsValues;
            setSection(settings);
        } break;
    }
});

port.postMessage({action: "get-settings"});