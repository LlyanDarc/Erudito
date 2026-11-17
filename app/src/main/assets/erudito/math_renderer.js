(function (globalScope) {
    "use strict";

    var KATEX_MACROS = {
        "\\Z": "\\mathbb{Z}",
        "\\Q": "\\mathbb{Q}",
        "\\R": "\\mathbb{R}",
        "\\N": "\\mathbb{N}",
        "\\C": "\\mathbb{C}"
    };

    function renderSingleFormula(element, isBlock, katexInstance) {
        if (!element) {
            return false;
        }
        var tex = element.getAttribute("data-tex");
        if (!tex || !tex.trim()) {
            return false;
        }
        if (!katexInstance || typeof katexInstance.render !== "function") {
            element.classList.add("erudito-math-fallback");
            element.textContent = tex;
            return false;
        }

        try {
            katexInstance.render(tex, element, {
                displayMode: Boolean(isBlock),
                throwOnError: false,
                strict: false,
                trust: false,
                macros: KATEX_MACROS
            });
            return true;
        } catch (err) {
            element.classList.add("erudito-math-fallback");
            element.textContent = tex;
            return false;
        }
    }

    function wrapTables(rootDoc) {
        var tables = rootDoc.querySelectorAll("table");
        for (var i = 0; i < tables.length; i++) {
            var table = tables[i];
            var parent = table.parentElement;
            if (parent && parent.classList && parent.classList.contains("erudito-table-wrapper")) {
                continue;
            }
            if (parent) {
                var wrapper = rootDoc.createElement("div");
                wrapper.className = "erudito-table-wrapper";
                parent.insertBefore(wrapper, table);
                wrapper.appendChild(table);
            }
        }
    }

    function renderAll(rootDoc, katexInstance) {
        var doc = rootDoc || (typeof document !== "undefined" ? document : null);
        var katexLib = katexInstance || (globalScope ? globalScope.katex : null);
        if (!doc) {
            return { renderedCount: 0, fallbackCount: 0 };
        }

        wrapTables(doc);

        var renderedCount = 0;
        var fallbackCount = 0;

        var inlineNodes = doc.querySelectorAll(".erudito-math-inline[data-tex]");
        for (var i = 0; i < inlineNodes.length; i++) {
            if (renderSingleFormula(inlineNodes[i], false, katexLib)) {
                renderedCount++;
            } else {
                fallbackCount++;
            }
        }

        var blockNodes = doc.querySelectorAll(".erudito-math-block[data-tex]");
        for (var j = 0; j < blockNodes.length; j++) {
            if (renderSingleFormula(blockNodes[j], true, katexLib)) {
                renderedCount++;
            } else {
                fallbackCount++;
            }
        }

        return { renderedCount: renderedCount, fallbackCount: fallbackCount };
    }

    function setTheme(themeName) {
        if (typeof document === "undefined" || !document.body) {
            return;
        }
        var isDark = themeName === "dark";
        document.body.classList.toggle("erudito-dark", isDark);
        document.body.classList.toggle("erudito-light", !isDark);
        document.body.setAttribute("data-theme", isDark ? "dark" : "light");
    }

    function getContentHeight() {
        if (typeof document === "undefined") {
            return 0;
        }
        var content = document.querySelector(".erudito-content");
        var body = document.body;
        var docEl = document.documentElement;
        var h1 = content ? Math.ceil(content.getBoundingClientRect().height) : 0;
        var h2 = body ? body.scrollHeight : 0;
        var h3 = docEl ? docEl.scrollHeight : 0;
        return Math.max(h1, h2, h3);
    }

    var api = {
        macros: KATEX_MACROS,
        renderAll: renderAll,
        setTheme: setTheme,
        getContentHeight: getContentHeight
    };

    if (globalScope) {
        globalScope.EruditoMath = api;
    }

    if (typeof module !== "undefined" && module.exports) {
        module.exports = api;
    }

    if (typeof document !== "undefined") {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", function () {
                renderAll(document, globalScope ? globalScope.katex : null);
            });
        } else {
            renderAll(document, globalScope ? globalScope.katex : null);
        }
    }
})(typeof window !== "undefined" ? window : globalThis);
