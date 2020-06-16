var acc = document.getElementsByClassName("accordion");
for (var i = 0; i < acc.length; i++) {
    acc[i].addEventListener("click", function () {
        /* Toggle between adding and removing the "active" class,
        to highlight the button that controls the panel */
        this.classList.toggle("active");

        /* Toggle between hiding and showing the active panel */
        var panel = this.nextElementSibling;
        if (panel.style.display === "block") {
            panel.style.display = "none";
        } else {
            panel.style.display = "block";
        }
    });
}

function openDiv(accordionButton) {
    if (accordionButton) {
        var div = accordionButton.nextElementSibling;
        console.log("div = " + div);
        if (div) {
            accordionButton.classList.add("active");
            div.style.display = "block";
            accordionButton.scrollIntoView(true);
        }
    }
}

function locationHashChanged(e) {
    if (location.hash.indexOf("#button") !== -1) {
        var acc = document.getElementsByClassName("accordion");
        // first close other accordions
        for (var i = 0; i < acc.length; i++) {
            if (acc[i].classList) {
                acc[i].classList.remove("active");
            }
            if (acc[i].nextElementSibling) {
                acc[i].nextElementSibling.style.display = "none";
            }
        }

        var elementId = location.hash.slice(1);
        if (elementId) {
            var accordionTitleButton;
            // open title div
            if (elementId.indexOf("client") !== -1) {
                accordionTitleButton = document.getElementById("button_client");
            } else if (elementId.indexOf("match_by_openapi") !== -1) {
                accordionTitleButton = document.getElementById("button_match_by_openapi");
            } else if (elementId.indexOf("request") !== -1) {
                accordionTitleButton = document.getElementById("button_request");
            } else if (elementId.indexOf("response") !== -1) {
                accordionTitleButton = document.getElementById("button_response");
            } else if (elementId.indexOf("forward") !== -1) {
                accordionTitleButton = document.getElementById("button_forward");
            } else if (elementId.indexOf("callback") !== -1) {
                accordionTitleButton = document.getElementById("button_callback");
            } else if (elementId.indexOf("error") !== -1) {
                accordionTitleButton = document.getElementById("button_error");
            } else if (elementId.indexOf("recorded_reqs") !== -1) {
                accordionTitleButton = document.getElementById("button_recorded_reqs");
            } else if (elementId.indexOf("recorded_req_res") !== -1) {
                accordionTitleButton = document.getElementById("button_recorded_req_res");
            } else if (elementId.indexOf("active_expectations") !== -1) {
                accordionTitleButton = document.getElementById("button_active_expectations");
            } else if (elementId.indexOf("recorded_expectations") !== -1) {
                accordionTitleButton = document.getElementById("button_recorded_expectations");
            } else if (elementId.indexOf("recorded_log_events") !== -1) {
                accordionTitleButton = document.getElementById("button_recorded_log_events");
            } else if (elementId.indexOf("verify_req") !== -1) {
                accordionTitleButton = document.getElementById("button_verify_req");
            } else if (elementId.indexOf("verify_sequence") !== -1) {
                accordionTitleButton = document.getElementById("button_verify_sequence");
            }
            openDiv(accordionTitleButton);

            // then open and scroll into view
            var accordionButton = document.getElementById(elementId);
            openDiv(accordionButton);
        }

    }
}

window.onhashchange = locationHashChanged;
window.onload = locationHashChanged;
