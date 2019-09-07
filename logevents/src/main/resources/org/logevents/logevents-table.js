function showLogEventTable() {
    function listItem(header, text, filter) {
        const li = document.createElement("li");
        if (filter) {
            const currentFilter = new URLSearchParams(window.location.search.substr(1));
            const time = currentFilter.get("time");
            const interval = currentFilter.get("interval");
            const level = currentFilter.get("level");
            const timezoneOffset = currentFilter.get("timezoneOffset");
            li.innerHTML = `<strong>${header}:</strong>
                <a href="?time=${time}&interval=${interval}&level=${level}&${filter}=${text}&timezoneOffset=${timezoneOffset}"
                >${text}</a>`;
        } else {
            li.innerHTML = `<strong>${header}:</strong> ${text}`;
        }
        return li;
    }

    const events = document.createElement("div");
    for (const index in logEvents) {
        const logEvent = logEvents[index];
        const eventCard = document.createElement("div");
        eventCard.classList.add("event");
        eventCard.dataset.eventIndex = index;

        eventCard.appendChild(createElementWithText("button", logEvent.levelIcon));
        eventCard.appendChild(document.createTextNode(hourMinuteSecond(new Date(logEvent.time))));
        eventCard.appendChild(createElementWithText("span", " [" + logEvent.abbreviatedLogger + "]: ", "loggerName"));

        if (logEvent.throwable) {
            eventCard.appendChild(document.createTextNode(" \u2757"));
        }

        const formattedMessage = document.createElement("div");
        formattedMessage.classList.add("formattedMessage");
        for (const messagePart of logEvent.message) {
            formattedMessage.appendChild(createElementWithText("span", messagePart.text, messagePart.type));
        }
        eventCard.appendChild(formattedMessage);

        const eventDetails = document.createElement("div");
        eventDetails.classList.add("details");
        const ul = document.createElement("ul");
        ul.appendChild(listItem("Logger", logEvent.logger, "logger"));
        ul.appendChild(listItem("Thread", logEvent.thread, "thread"));
        ul.appendChild(listItem("Node", logEvent.node, "node"));
        ul.appendChild(listItem("Application", logEvent.application, "application"));
        if (logEvent.marker) {
            ul.appendChild(listItem("Marker", logEvent.marker, "marker"));
        }
        if (logEvent.mdc && Object.entries(logEvent.mdc).length) {
            const mdcList = document.createElement("li");
            mdcList.innerHTML = "<strong>MDC</strong>";
            const mdcUl = document.createElement("ul");
            mdcList.appendChild(mdcUl);

            for (const mdc of logEvent.mdc) {
                mdcUl.appendChild(listItem(mdc.name, mdc.value, "mdc[" + mdc.name + "]"));
            }

            ul.appendChild(mdcList);
        }
        if (logEvent.throwable) {
            ul.appendChild(listItem("Exception", logEvent.throwable));

            const stackTrace = document.createElement("pre");
            for (const element of logEvent.stackTrace) {
                if (element.sourceLink) {
                    stackTrace.innerHTML += "<a target='source' href='" + element.sourceLink + "'>" + element.className + "#" + element.methodName + "</a>";
                } else if (element.className) {
                    stackTrace.innerHTML += element.className + "#" + element.methodName + "("
                        + element.fileName + ":" + element.lineNumber + ")";
                }
                if (element.ignoredFrames > 0) {
                    stackTrace.innerHTML += "[" + element.ignoredFrames + " skipped]";
                }
                stackTrace.innerHTML += "\n";
            }
            ul.appendChild(stackTrace);
        }

        eventDetails.appendChild(ul);
        eventCard.appendChild(eventDetails);

        events.appendChild(eventCard);
    }
    const main = document.querySelector("main");
    main.innerHTML = "";
    main.appendChild(events);
}
