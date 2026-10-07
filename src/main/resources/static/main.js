/* ChaosBridge console — live dashboard, chaos management, metrics */
(function ($) {
    'use strict';

    const REFRESH_MS = 3000;
    const state = { chaosConfig: null, loading: false };

    const FIELD_HINTS = {
        latency: 'Delay per chunk, in seconds (e.g. 2 = two seconds)',
        bytePerSecond: 'Maximum bytes forwarded per second (e.g. 1024)',
        packetLossRate: 'Drop probability from 0.0 (none) to 1.0 (drop everything)'
    };

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    const escapeHtml = (value) =>
        String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');

    const formatNumber = (value) => (Number(value) || 0).toLocaleString();

    const formatBytes = (value) => {
        let n = Number(value) || 0;
        if (n < 1024) return n + ' B';
        const units = ['KB', 'MB', 'GB', 'TB'];
        let i = -1;
        do {
            n /= 1024;
            i++;
        } while (n >= 1024 && i < units.length - 1);
        return n.toFixed(n >= 10 ? 0 : 1) + ' ' + units[i];
    };

    const formatDuration = (ms) => {
        const total = Math.floor((Number(ms) || 0) / 1000);
        if (total < 60) return total + 's';
        const minutes = Math.floor(total / 60);
        const seconds = total % 60;
        if (minutes < 60) return minutes + 'm ' + seconds + 's';
        return Math.floor(minutes / 60) + 'h ' + (minutes % 60) + 'm';
    };

    const showModal = (id) => bootstrap.Modal.getOrCreateInstance(document.getElementById(id)).show();
    const hideModal = (id) => bootstrap.Modal.getOrCreateInstance(document.getElementById(id)).hide();

    const showFormError = (selector, xhr) => {
        let message = 'Request failed (' + (xhr && xhr.status) + ')';
        try {
            const body = JSON.parse(xhr.responseText);
            if (body && body.message) message = body.message;
        } catch (ignored) {
            /* not JSON */
        }
        $(selector).prop('hidden', false).text(message);
    };

    const cleanForm = ($form) => {
        $form.find('input').each(function () {
            $(this).val('');
        });
    };

    /* ------------------------------------------------------------------ */
    /* Dashboard                                                           */
    /* ------------------------------------------------------------------ */

    const loadDashboard = () => {
        if (state.loading) return;
        state.loading = true;

        $.ajax({
            url: '/metrics',
            type: 'GET',
            dataType: 'json',
            success: function (response) {
                renderStats(response.totals || {});
                renderProxies(response.data || []);
                renderHosts(response.hosts || []);
                if ($('#metricsModal').hasClass('show')) {
                    const key = $('#metrics-body').data('key');
                    if (key) loadMetrics(key);
                }
            },
            error: function (xhr) {
                console.error('Failed to load metrics:', xhr.responseText);
            },
            complete: function () {
                state.loading = false;
            }
        });
    };

    const renderStats = (totals) => {
        $('#stat-proxies').text(formatNumber(totals.proxies));
        $('#stat-active').text(formatNumber(totals.activeConnections));
        $('#stat-connections').text(formatNumber(totals.connections));
        $('#stat-upstream').text(formatBytes(totals.upstreamBytes));
        $('#stat-downstream').text(formatBytes(totals.downstreamBytes));
        $('#stat-dropped').text(formatNumber(totals.droppedChunks));
    };

    const renderProxies = (rows) => {
        const $tbody = $('#proxy-list-table').empty();
        $('#proxies-empty').prop('hidden', rows.length > 0);

        rows.forEach(function (row) {
            const key = escapeHtml(row.key);
            const chaosCount = Number(row.chaosCount) || 0;
            const chaosBadge = chaosCount > 0
                ? `<span class="cb-badge cb-badge--chaos">${escapeHtml(chaosCount)} active</span>`
                : `<span class="cb-badge cb-badge--none">none</span>`;
            const status = row.active
                ? `<span class="cb-badge cb-badge--on">Running</span>`
                : `<span class="cb-badge cb-badge--off">Stopped</span>`;

            $tbody.append(
                `<tr>
                    <td class="cb-host">${escapeHtml(row.serverHost)}</td>
                    <td class="cb-mono">${escapeHtml(row.port)}</td>
                    <td>${status}</td>
                    <td>${chaosBadge}</td>
                    <td class="cb-mono">${formatNumber(row.activeConnections)} / ${formatNumber(row.connections)}</td>
                    <td class="cb-mono">${formatBytes(row.upstreamBytes)}</td>
                    <td class="cb-mono">${formatBytes(row.downstreamBytes)}</td>
                    <td class="cb-mono">${formatNumber(row.droppedChunks)}</td>
                    <td>
                        <div class="cb-actions">
                            <button class="cb-btn cb-btn--ghost cb-btn--sm metrics-btn" data-key="${key}">Metrics</button>
                            <button class="cb-btn cb-btn--ghost cb-btn--sm add-chaos-btn" data-key="${key}">Add chaos</button>
                            <button class="cb-btn cb-btn--danger cb-btn--sm stop-btn" data-key="${key}">Stop</button>
                        </div>
                    </td>
                </tr>`
            );
        });
    };

    const renderHosts = (hosts) => {
        const $tbody = $('#hosts-table').empty();
        $('#hosts-empty').prop('hidden', hosts.length > 0);

        hosts.forEach(function (host) {
            $tbody.append(
                `<tr>
                    <td class="cb-host">${escapeHtml(host.host)}</td>
                    <td class="cb-mono">${formatNumber(host.proxies)}</td>
                    <td class="cb-mono">${formatNumber(host.activeConnections)}</td>
                    <td class="cb-mono">${formatBytes(host.upstreamBytes)}</td>
                    <td class="cb-mono">${formatBytes(host.downstreamBytes)}</td>
                    <td class="cb-mono">${formatNumber(host.droppedChunks)}</td>
                    <td>${Number(host.chaosProfiles) > 0
                        ? `<span class="cb-badge cb-badge--chaos">${formatNumber(host.chaosProfiles)}</span>`
                        : `<span class="cb-badge cb-badge--none">0</span>`}</td>
                </tr>`
            );
        });
    };

    /* ------------------------------------------------------------------ */
    /* Metrics modal                                                       */
    /* ------------------------------------------------------------------ */

    const metricCard = (label, value) =>
        `<div class="cb-metric"><span class="cb-metric__label">${escapeHtml(label)}</span>
            <span class="cb-metric__value">${escapeHtml(value)}</span></div>`;

    const loadMetrics = (key) => {
        $.ajax({
            url: '/metrics/' + key,
            type: 'GET',
            dataType: 'json',
            success: function (m) {
                const chaos = m.chaos || [];
                const chaosRows = chaos.length
                    ? chaos.map(function (c) {
                        const detail = Object.fromEntries(
                            Object.entries(c).filter(([k]) => ['id', 'type', 'line'].indexOf(k) === -1)
                        );
                        return `<tr>
                            <td class="cb-mono">${escapeHtml(c.type)}</td>
                            <td>${escapeHtml(c.line)}</td>
                            <td class="cb-mono">${escapeHtml(JSON.stringify(detail))}</td>
                            <td><button class="cb-btn cb-btn--danger cb-btn--sm remove-chaos-btn" data-chaos-id="${escapeHtml(c.id)}">Remove</button></td>
                        </tr>`;
                    }).join('')
                    : `<tr><td colspan="4" class="cb-empty">No chaos attached to this proxy.</td></tr>`;

                $('#metricsModalLabel').text('Metrics · ' + m.serverHost + ':' + m.serverPort);
                $('#metrics-body').data('key', key).html(
                    `<div class="cb-metrics-grid">
                        ${metricCard('Status', m.active ? 'Running' : 'Stopped')}
                        ${metricCard('Active conns', formatNumber(m.activeConnections))}
                        ${metricCard('Total conns', formatNumber(m.connections))}
                        ${metricCard('Failed conns', formatNumber(m.failedConnections))}
                        ${metricCard('Upstream', formatBytes(m.upstreamBytes))}
                        ${metricCard('Downstream', formatBytes(m.downstreamBytes))}
                        ${metricCard('Dropped chunks', formatNumber(m.droppedChunks))}
                        ${metricCard('Latency chunks', formatNumber(m.latencyChunks))}
                        ${metricCard('Throttled chunks', formatNumber(m.throttledChunks))}
                        ${metricCard('Uptime', formatDuration(m.uptimeMs))}
                    </div>
                    <p class="cb-subhead">Active chaos</p>
                    <div class="cb-table-wrap">
                        <table class="cb-table">
                            <thead><tr><th>Type</th><th>Line</th><th>Detail</th><th></th></tr></thead>
                            <tbody>${chaosRows}</tbody>
                        </table>
                    </div>`
                );
            },
            error: function (xhr) {
                $('#metrics-body').html(`<p class="cb-form-error">Could not load metrics (${xhr.status}).</p>`);
            }
        });
    };

    const openMetrics = (key) => {
        loadMetrics(key);
        showModal('metricsModal');
    };

    /* ------------------------------------------------------------------ */
    /* Chaos configuration                                                 */
    /* ------------------------------------------------------------------ */

    const withChaosConfig = (callback) => {
        if (state.chaosConfig) {
            callback(state.chaosConfig);
            return;
        }
        $.getJSON('/chaosConfig', function (config) {
            state.chaosConfig = config;
            callback(config);
        });
    };

    const openAddChaos = (key) => {
        withChaosConfig(function (config) {
            const $form = $('#add-chaos-form');
            $form.data('key', key);
            $('#add-chaos-target').text(key);
            $('#add-chaos-error').prop('hidden', true).text('');

            const $type = $form.find('.chaos-type-select-form').empty();
            config.forEach(function (c) {
                $type.append($('<option>').val(c.type).text(c.type));
            });

            const renderFields = function () {
                const selected = $type.val();
                const selectedConfig = config.find(function (c) { return c.type === selected; });
                const $container = $form.find('.field-container').empty();
                (selectedConfig ? selectedConfig.fields : []).forEach(function (field) {
                    const hint = FIELD_HINTS[field] || '';
                    $container.append(
                        `<div class="cb-field">
                            <label class="cb-label">${escapeHtml(field)}</label>
                            <input type="text" class="cb-input" name="${escapeHtml(field)}" placeholder="${escapeHtml(hint || field)}">
                            ${hint ? `<small class="cb-hint">${escapeHtml(hint)}</small>` : ''}
                        </div>`
                    );
                });
            };

            $type.off('change').on('change', renderFields);
            renderFields();

            showModal('addChaosModal');
        });
    };

    /* ------------------------------------------------------------------ */
    /* Event wiring                                                        */
    /* ------------------------------------------------------------------ */

    $(function () {
        loadDashboard();
        setInterval(loadDashboard, REFRESH_MS);

        $('#refreshBtn').on('click', loadDashboard);

        $('#openStartProxy').on('click', function () {
            $('#start-proxy-error').prop('hidden', true).text('');
            showModal('startProxyModal');
        });

        $('.start-proxy-form-btn').on('click', function () {
            const $form = $('#start-proxy-form');
            const data = {
                port: $form.find('input[name=port]').val(),
                serverHost: $form.find('input[name=serverHost]').val(),
                serverPort: $form.find('input[name=serverPort]').val()
            };

            $.ajax({
                url: '/proxy',
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(data),
                success: function () {
                    hideModal('startProxyModal');
                    cleanForm($form);
                    loadDashboard();
                },
                error: function (xhr) {
                    showFormError('#start-proxy-error', xhr);
                }
            });
        });

        $('.apply-chaos-btn').on('click', function () {
            const $form = $('#add-chaos-form');
            const key = $form.data('key');
            const formData = {
                chaosType: $form.find('.chaos-type-select-form').val(),
                line: $form.find('.chaos-line-select-form').val()
            };

            $form.find('.field-container').find('input').each(function () {
                formData[$(this).attr('name')] = $(this).val();
            });

            $.ajax({
                url: '/addChaos/' + key,
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(formData),
                success: function () {
                    hideModal('addChaosModal');
                    cleanForm($form);
                    loadDashboard();
                },
                error: function (xhr) {
                    showFormError('#add-chaos-error', xhr);
                }
            });
        });

        // Delegated handlers so they survive the auto-refreshing tables.
        $('#proxy-list-table').on('click', '.stop-btn', function () {
            const key = $(this).data('key');
            if (!window.confirm('Stop proxy ' + key + '?')) return;
            $.ajax({
                url: '/proxy/' + key,
                type: 'DELETE',
                success: loadDashboard,
                error: function (xhr) { console.error('Failed to stop proxy:', xhr.responseText); }
            });
        });

        $('#proxy-list-table').on('click', '.add-chaos-btn', function () {
            openAddChaos($(this).data('key'));
        });

        $('#proxy-list-table').on('click', '.metrics-btn', function () {
            openMetrics($(this).data('key'));
        });

        $('#metrics-body').on('click', '.remove-chaos-btn', function () {
            const key = $('#metrics-body').data('key');
            const chaosId = $(this).data('chaos-id');
            $.ajax({
                url: '/removeChaos/' + key + '/' + chaosId,
                type: 'DELETE',
                success: function () {
                    loadMetrics(key);
                    loadDashboard();
                },
                error: function (xhr) { console.error('Failed to remove chaos:', xhr.responseText); }
            });
        });
    });
})(jQuery);
