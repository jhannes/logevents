function showFacets(facets) {
    const currentFilter = new URLSearchParams(window.location.hash.substr(1));

    function includeExcludeSelect(name) {
        const includeExcludeSelect = document.createElement("select");
        includeExcludeSelect.name = name;
        includeExcludeSelect.appendChild(option(name, "include", "Include"));
        includeExcludeSelect.appendChild(option(name, "exclude", "Exclude"));
        return includeExcludeSelect;
    }

    function checkbox(name, value, text) {
        const checkboxDiv = document.createElement("div");
        const checked = (currentFilter.getAll(name).indexOf(value) >= 0) ? ' checked="checked"' : "";
        checkboxDiv.innerHTML = `<label title="${text || value}"><input type="checkbox" name=${name} value="${value}" ${checked}>${text || value}</label>`;
        return checkboxDiv;
    }

    function option(name, value, text) {
        const option = document.createElement("option");
        if (value) {
            option.setAttribute("value", value);
            if (currentFilter.getAll(name).indexOf(value) >= 0) {
                option.setAttribute("selected", "selected");
            }
            option.innerText = text || value;
        }
        return option;
    }


    document.getElementById("time").value = currentFilter.get("time");
    document.getElementById("date").value = currentFilter.get("date");
    const selectedInterval = document.querySelector("[name=interval] [value=" + currentFilter.get("interval") + "]");
    if (selectedInterval) {
        selectedInterval.selected = true;
    } else {
        const intervalSelect = document.querySelector("[name=interval]");
        const separator = createElementWithText("option", "--------------");
        separator.setAttribute("disabled", "disable");
        intervalSelect.insertBefore(separator, intervalSelect.firstChild);
        const option = createElementWithText("option", currentFilter.get("interval"));
        option.setAttribute("selected", "selected");
        intervalSelect.insertBefore(option, intervalSelect.firstChild);
    }
    const selectedLevel = document.querySelector("[name=level] [value=" + currentFilter.get("level") + "]");
    if (selectedLevel) {
        selectedLevel.selected = true;
    }

    if (currentFilter.get("timezoneOffset")) {
        document.getElementById("timezoneOffset").value = currentFilter.get("timezoneOffset");
    }

    document.getElementById("intervalMatchCount").innerText = facets.rowCount;
    document.getElementById("filterMatchCount").innerText = facets.filteredCount;
    if (facets.rowCount === 0) {
        document.getElementById("detailedFilter").style.display = "none";
        body.classList.add("drawerOpen");
    } else {
        document.getElementById("detailedFilter").style.display = "block";
    }

    const threads = document.getElementById("threads");
    const threadsSelect = document.createElement("select");
    threadsSelect.setAttribute("name", "thread");
    threadsSelect.appendChild(option("thread"));
    for (const thread of facets.threads.sort()) {
        threadsSelect.appendChild(option("thread", thread));
    }
    threads.innerHTML = "";
    threads.appendChild(threadsSelect);

    const loggersFieldset = document.getElementById("loggers");
    loggersFieldset.innerHTML = "";
    loggersFieldset.appendChild(includeExcludeSelect("includeLoggers"));
    for (const logger of facets.loggers.sort()) {
        loggersFieldset.appendChild(checkbox("logger", logger.name, logger.abbreviatedName));
    }

    const markersFieldset = document.getElementById("markers");
    markersFieldset.innerHTML = "";
    markersFieldset.appendChild(includeExcludeSelect("includeMarkers"))
    for (const marker of facets.markers.sort()) {
        markersFieldset.appendChild(checkbox("marker", marker));
    }

    const applicationsFieldset = document.getElementById("applications");
    applicationsFieldset.innerHTML = "";
    for (const node of facets.applications.sort()) {
        applicationsFieldset.appendChild(checkbox("application", node));
    }

    const nodesFieldset = document.getElementById("nodes");
    nodesFieldset.innerHTML = "";
    for (const node of facets.nodes.sort()) {
        nodesFieldset.appendChild(checkbox("node", node));
    }

    const mdcFilter = document.getElementById("mdcFilter");
    mdcFilter.innerHTML = "";
    for (const mdcEntry of facets.mdc) {
        const mdcLabel = createElementWithText("label", mdcEntry.name + ": ");

        const mdcSelect = document.createElement("select");
        mdcSelect.setAttribute("name", "mdc[" + mdcEntry.name + "]");

        const emptyMdcOption = document.createElement("option");
        mdcSelect.appendChild(emptyMdcOption);

        for (const alternative of mdcEntry.values) {
            mdcSelect.appendChild(option("mdc[" + mdcEntry.name + "]", alternative));
        }
        const mdcSelectDiv = document.createElement("div");
        mdcSelectDiv.appendChild(mdcSelect);
        mdcLabel.appendChild(mdcSelectDiv);
        const mdcDiv = document.createElement("div");
        mdcDiv.appendChild(mdcLabel);

        mdcFilter.appendChild(mdcDiv);
    }
}
