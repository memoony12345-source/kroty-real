package com.example.domain.web

object MikroTikJsScripts {

    fun buildAutoLoginScript(
        voucherCode: String,
        password: String,
        passwordMode: String,
        learnedSelector: String?
    ): String {
        val safeCode = voucherCode.replace("'", "\\'").replace("\"", "\\\"")
        val safePass = when (passwordMode) {
            "SAME_AS_USER" -> safeCode
            "CUSTOM" -> password.replace("'", "\\'")
            else -> ""
        }
        val safeLearned = learnedSelector?.replace("'", "\\'") ?: ""

        return """
        (function() {
            try {
                var code = '$safeCode';
                var pass = '$safePass';
                var learned = '$safeLearned';
                var targetInput = null;

                if (learned && learned.length > 0) {
                    targetInput = document.querySelector(learned);
                }

                if (!targetInput) {
                    var inputs = Array.from(document.querySelectorAll('input'));
                    var bestScore = -1;
                    for (var i = 0; i < inputs.length; i++) {
                        var el = inputs[i];
                        var type = (el.getAttribute('type') || 'text').toLowerCase();
                        if (type === 'hidden' || type === 'submit' || type === 'button' || type === 'checkbox') continue;
                        var rect = el.getBoundingClientRect();
                        if (rect.width === 0 || rect.height === 0) continue;

                        var score = 10;
                        var name = (el.getAttribute('name') || '').toLowerCase();
                        var id = (el.getAttribute('id') || '').toLowerCase();
                        var ph = (el.getAttribute('placeholder') || '').toLowerCase();
                        var combined = name + ' ' + id + ' ' + ph;

                        if (name === 'username' || name === 'user' || name === 'code' || name === 'voucher') score += 60;
                        if (combined.indexOf('كرت') !== -1 || combined.indexOf('رمز') !== -1 || combined.indexOf('بطاقة') !== -1 || combined.indexOf('كود') !== -1) score += 50;
                        if (combined.indexOf('user') !== -1 || combined.indexOf('voucher') !== -1 || combined.indexOf('card') !== -1 || combined.indexOf('pin') !== -1) score += 40;
                        if (type === 'text' || type === 'tel' || type === 'number') score += 15;

                        if (score > bestScore) {
                            bestScore = score;
                            targetInput = el;
                        }
                    }
                }

                if (!targetInput) {
                    if (window.KrotyBridge) window.KrotyBridge.onLoginFieldMissing();
                    return 'NO_FIELD';
                }

                var maxLen = parseInt(targetInput.getAttribute('maxlength') || '0', 10);
                if (maxLen >= 4 && maxLen <= 20 && window.KrotyBridge) {
                    window.KrotyBridge.onDetectedMaxLength(maxLen);
                }

                targetInput.focus();
                targetInput.value = code;
                targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                targetInput.dispatchEvent(new Event('change', { bubbles: true }));

                var passInput = document.querySelector('input[type="password"], input[name="password"]');
                if (passInput) {
                    passInput.value = pass;
                    passInput.dispatchEvent(new Event('input', { bubbles: true }));
                    passInput.dispatchEvent(new Event('change', { bubbles: true }));
                }

                setTimeout(function() {
                    if (typeof window.doLogin === 'function') {
                        window.doLogin();
                    } else {
                        var btn = document.querySelector('button[type="submit"], input[type="submit"], button');
                        if (btn) {
                            btn.click();
                        } else if (targetInput.form) {
                            targetInput.form.submit();
                        }
                    }
                }, 250);

                return 'INJECTED';
            } catch (e) {
                return 'ERR:' + e.message;
            }
        })();
        """.trimIndent()
    }

    fun buildTouchToLearnScript(): String = """
        (function() {
            document.addEventListener('click', function(e) {
                var el = e.target;
                if (el && el.tagName && el.tagName.toLowerCase() === 'input') {
                    e.preventDefault();
                    e.stopPropagation();
                    el.style.outline = '3px solid #10B981';
                    var selector = '';
                    if (el.id) {
                        selector = '#' + el.id;
                    } else if (el.name) {
                        selector = 'input[name="' + el.name + '"]';
                    } else {
                        selector = 'input[type="' + (el.type || 'text') + '"]';
                    }
                    if (window.KrotyBridge) {
                        window.KrotyBridge.onSelectorLearned(selector);
                    }
                }
            }, true);
        })();
    """.trimIndent()

    fun buildPageScrapeScript(): String = """
        (function() {
            try {
                var html = document.documentElement ? document.documentElement.innerHTML : '';
                if (window.KrotyBridge) {
                    window.KrotyBridge.onPageHtmlScraped(window.location.href, html);
                }
            } catch (e) {}
        })();
    """.trimIndent()
}
