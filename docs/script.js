/* ChaosBridge docs — lightweight, dependency-free interactions */
(function () {
    'use strict';

    /* ---------- Mobile navigation ---------- */
    var sidebar = document.getElementById('sidebar');
    var menuToggle = document.getElementById('menuToggle');

    function closeMenu() {
        if (sidebar) sidebar.classList.remove('open');
        if (menuToggle) menuToggle.setAttribute('aria-expanded', 'false');
    }

    if (menuToggle && sidebar) {
        menuToggle.addEventListener('click', function () {
            var open = sidebar.classList.toggle('open');
            menuToggle.setAttribute('aria-expanded', String(open));
        });

        sidebar.addEventListener('click', function (event) {
            if (event.target.closest('a')) closeMenu();
        });

        document.addEventListener('click', function (event) {
            if (!sidebar.classList.contains('open')) return;
            if (!sidebar.contains(event.target) && !menuToggle.contains(event.target)) {
                closeMenu();
            }
        });
    }

    /* ---------- Copy-to-clipboard ---------- */
    document.querySelectorAll('[data-copy]').forEach(function (button) {
        button.addEventListener('click', function () {
            var wrapper = button.closest('.code') || button.closest('.hero__terminal');
            var code = wrapper ? wrapper.querySelector('code') : null;
            if (!code) return;

            var text = code.innerText;
            var done = function () {
                var original = button.textContent;
                button.textContent = 'Copied!';
                button.classList.add('copied');
                window.setTimeout(function () {
                    button.textContent = original;
                    button.classList.remove('copied');
                }, 1600);
            };

            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(text).then(done, fallbackCopy.bind(null, text, done));
            } else {
                fallbackCopy(text, done);
            }
        });
    });

    function fallbackCopy(text, done) {
        var area = document.createElement('textarea');
        area.value = text;
        area.setAttribute('readonly', '');
        area.style.position = 'fixed';
        area.style.opacity = '0';
        document.body.appendChild(area);
        area.select();
        try {
            document.execCommand('copy');
            done();
        } catch (error) {
            /* clipboard unavailable — silently ignore */
        }
        document.body.removeChild(area);
    }

    /* ---------- Scroll spy ---------- */
    var links = Array.prototype.slice.call(document.querySelectorAll('.sidebar__link'));
    var targets = links
        .map(function (link) {
            var id = link.getAttribute('href');
            return id && id.charAt(0) === '#' ? document.querySelector(id) : null;
        })
        .filter(Boolean);

    if (!('IntersectionObserver' in window) || targets.length === 0) return;

    var activeId = null;

    function setActive(id) {
        if (id === activeId) return;
        activeId = id;
        links.forEach(function (link) {
            link.classList.toggle('active', link.getAttribute('href') === '#' + id);
        });
    }

    var observer = new IntersectionObserver(function (entries) {
        var visible = entries
            .filter(function (entry) { return entry.isIntersecting; })
            .sort(function (a, b) { return a.boundingClientRect.top - b.boundingClientRect.top; });

        if (visible.length > 0) {
            setActive(visible[0].target.id);
        }
    }, {
        rootMargin: '-25% 0px -65% 0px',
        threshold: 0
    });

    targets.forEach(function (target) { observer.observe(target); });
})();
