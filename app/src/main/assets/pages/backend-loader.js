if(!("scrollix" in window)) {
    document.write(`<script src="./backend.js"></script>`);

    const erudaSrc = 'https://cdn.jsdelivr.net/npm/eruda';
    document.write(`<script src="${erudaSrc}"></script>`);
    document.write('<script>eruda.init();</script>');
    
    console.error("STARTING IN A HEADLESS MODE! SOME FUNCTIONS MAY WORK NOT AS PLANNED!");
}



