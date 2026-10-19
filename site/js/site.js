/* ==========================================================================
   AuraSpend — site behaviour
   --------------------------------------------------------------------------
   No dependencies, no network requests. Four jobs:
     1. the light/dark theme toggle (the choice is remembered)
     2. swapping the app screenshots to the pair that matches the theme
     3. the mobile menu
     4. revealing sections as they scroll into view

   The theme has to be applied before the first paint, so the script that
   reads the stored choice lives inline in <head>. This file only handles
   what happens after that.
   ========================================================================== */

(function () {
    "use strict";

    var root = document.documentElement;
    var STORAGE_KEY = "auraspend-theme";

    function stored() {
        try {
            var v = localStorage.getItem(STORAGE_KEY);
            return v === "light" || v === "dark" ? v : null;
        } catch (e) {
            return null;
        }
    }

    function systemTheme() {
        return window.matchMedia("(prefers-color-scheme: dark)").matches
            ? "dark"
            : "light";
    }

    function active() {
        return root.getAttribute("data-theme") || systemTheme();
    }

    /* ── Screenshots ──────────────────────────────────────────────────────
       The app screens exist in a light and a dark capture. The one that
       matches the current theme is the one shown. A single <img> is
       swapped rather than a <picture>, because the theme is an explicit
       user choice here: the OS media query would override it. */

    function paintScreenshots(dark) {
        var nodes = document.querySelectorAll("img[data-dark-src]");
        for (var i = 0; i < nodes.length; i++) {
            var img = nodes[i];
            var want = dark
                ? img.getAttribute("data-dark-src")
                : img.getAttribute("data-light-src");
            if (want && img.getAttribute("src") !== want) {
                img.setAttribute("src", want);
            }
        }
    }

    function paint() {
        var dark = active() === "dark";
        var button = document.querySelector(".theme-toggle");
        if (button) {
            button.setAttribute(
                "aria-label",
                dark ? "Switch to light theme" : "Switch to dark theme"
            );
        }
        paintScreenshots(dark);
    }

    /* ── Theme toggle ─────────────────────────────────────────────────── */

    var toggle = document.querySelector(".theme-toggle");
    if (toggle) {
        toggle.addEventListener("click", function () {
            var next = active() === "dark" ? "light" : "dark";
            root.setAttribute("data-theme", next);
            try {
                localStorage.setItem(STORAGE_KEY, next);
            } catch (e) {
                /* private mode: the choice simply does not persist */
            }
            paint();
        });
    }

    var media = window.matchMedia("(prefers-color-scheme: dark)");
    if (media.addEventListener) {
        media.addEventListener("change", function () {
            /* An explicit choice wins; only follow the system when there is none. */
            if (!stored()) {
                root.removeAttribute("data-theme");
                paint();
            }
        });
    }

    paint();

    /* ── Mobile menu ──────────────────────────────────────────────────── */

    var menuButton = document.querySelector(".menu-toggle");
    var nav = document.getElementById("site-nav");
    if (menuButton && nav) {
        menuButton.addEventListener("click", function () {
            var open = menuButton.getAttribute("aria-expanded") === "true";
            menuButton.setAttribute("aria-expanded", String(!open));
            menuButton.setAttribute("aria-label", open ? "Open menu" : "Close menu");
            nav.classList.toggle("is-open", !open);
        });

        nav.addEventListener("click", function (event) {
            if (event.target.tagName !== "A") return;
            nav.classList.remove("is-open");
            menuButton.setAttribute("aria-expanded", "false");
            menuButton.setAttribute("aria-label", "Open menu");
        });
    }

    /* ── Scroll reveal ──────────────────────────────────────────────────
       A section fades up once. If IntersectionObserver is missing, or the
       reader asked for reduced motion, the content is simply visible. */

    var revealables = document.querySelectorAll(".reveal");
    if (revealables.length) {
        var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        if (reduced || !("IntersectionObserver" in window)) {
            for (var i = 0; i < revealables.length; i++) {
                revealables[i].classList.add("is-in");
            }
        } else {
            var observer = new IntersectionObserver(
                function (entries) {
                    entries.forEach(function (entry) {
                        if (!entry.isIntersecting) return;
                        entry.target.classList.add("is-in");
                        observer.unobserve(entry.target);
                    });
                },
                { rootMargin: "0px 0px -8% 0px", threshold: 0.05 }
            );
            for (var j = 0; j < revealables.length; j++) {
                observer.observe(revealables[j]);
            }
        }
    }
})();
