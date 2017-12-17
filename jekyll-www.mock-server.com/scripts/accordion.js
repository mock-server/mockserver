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
        // then open and scroll into view
        var accordionButton = document.getElementById(location.hash.slice(1));
        if (accordionButton) {
            accordionButton.classList.remove("active");
            accordionButton.nextElementSibling.style.display = "block";
            accordionButton.scrollIntoView(true);
        }
    }
}

window.onhashchange = locationHashChanged;
