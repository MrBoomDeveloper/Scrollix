const CALLBACK_DELAY = 75;

const sectionsEl = document.querySelector("#sections");
const titleEl = document.querySelector("#title");
const backEl = document.querySelector("#back");

let currentSectionPath = [];

backEl.onclick = () => {
    if(currentSectionPath.length == 0) return;

    setTimeout(() => {
        openBackRecursively(homeSections, 0);
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
                backEl.style.display = data == homeSections ? "none" : "block";

                for(const [id, item] of Object.entries(data.items)) {
                    const el = document.createElement("div");
                    el.className = "";

                    const key = JSON.stringify(currentSectionPath) + id;
                    let value = Boolean(localStorage.getItem(key));

                    switch(item.type ?? "info") {
                        case "section": {
                            el.style.display = "flex";
                            el.style.flexDirection = "column";
                            el.style.gap = ".2rem";

                            el.innerHTML = `
                                <p>${item.title}</p>
                                ${item.description ? `<p>${item.description}</p>` : ""}
                            `;

                            el.onclick = () => setTimeout(() => {
                                currentSectionPath.push(id);
                                setSection(item);
                            }, CALLBACK_DELAY);
                            break;
                        }

                        case "toggle": {
                            el.style.display = "flex";
                            el.style.alignItems = "center";
                            el.style.gap = "1rem";

                            render();

                            function render() {
                                el.innerHTML = `
                                    <div style="flex-grow: 1; gap: .2rem; display: flex; flex-direction: column">
                                        <p>${item.title}</p>
                                        ${item.description ? `<p>${item.description}</p>` : ""}
                                    </div>

                                    <p>${value}</p>
                                `;
                            }

                            el.onclick = () => {
                                localStorage.setItem(key, !value);
                                value = !value;

                                setTimeout(() => render(), CALLBACK_DELAY);
                            }

                            break;
                        }

                        default: {
                            el.innerHTML = `
                                <p>${item.title}</p>
                                ${item.description ? `<p>${item.description}</p>` : ""}
                            `;
                        }
                    }

        sectionsEl.appendChild(el);
    }
}

//const homeSections = scrollix.getSettings();
//setSection(homeSections);

(async () => {
    const settings = await scrollix.getSettings();
    setSection(settings);
})();