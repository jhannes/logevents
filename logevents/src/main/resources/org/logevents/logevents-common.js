function hourMinute(instant) {
    return instant.getHours().toString().padStart(2, "0") +
        ":" + instant.getMinutes().toString().padStart(2, "0");
}

function hourMinuteSecond(instant) {
    return instant.getHours().toString().padStart(2, "0") +
        ":" + instant.getMinutes().toString().padStart(2, "0") +
        ":" + instant.getSeconds().toString().padStart(2, "0");
}


function createElementWithText(tagName, textContent, className) {
    const element = document.createElement(tagName);
    element.innerText = textContent;
    if (className) {
        element.classList.add(className);
    }
    return element;
}
