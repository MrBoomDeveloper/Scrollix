const urlField = document.getElementById("url");
const reasonField = document.getElementById("reason");

const params = new URLSearchParams(location.search);
//urlField.innerText = params.get("url");
//reasonField.innerText = params.get("reason");

document.querySelector("#reload").addEventListener("click", () => {
    scrollix.reload();
});