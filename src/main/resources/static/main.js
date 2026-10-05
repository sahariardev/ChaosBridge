const escapeHtml = (value) =>
    String(value == null ? '' : value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');

$('.start-proxy-form-btn').on('click', (e) => {
    e.preventDefault();

    const $form = $('.create-new-proxy-form');
    const port = $form.find('input[name=port]').val();
    const serverHost = $form.find('input[name=serverHost]').val();
    const serverPort = $form.find('input[name=serverPort]').val();

    const data = {
        port, serverHost, serverPort
    }

    $.ajax({
        url: "/proxy",
        type: "POST",
        contentType: "application/json",
        data: JSON.stringify(data),
        success: function (response) {
            loadProxies();
            cleanForm($form);
            $('.btn-close').trigger('click');
        },
        error: function (xhr) {
            console.error("Error:", xhr.responseText);
        }
    });
});

const deleteProxy = (key, callBack) => {
    $.ajax({
        url: "/proxy/" + key,
        type: "DELETE",
        success: function (response) {
            callBack();
        },
        error: function (xhr) {
            console.error("Error:", xhr.responseText);
        }
    });
}

const deleteChaos = (key, chaosId, callBack) => {
    $.ajax({
        url: `/removeChaos/${key}/${chaosId}`,
        type: "DELETE",
        success: function (response) {
            callBack();
        },
        error: function (xhr) {
            console.error("Error:", xhr.responseText);
        }
    });
}

const loadProxies = () => {
    $.ajax({
        url: "/proxy",
        type: "GET",
        success: function (data) {
            const $proxyListTable = $('#proxy-list-table');
            $proxyListTable.html('');

            if (!data || !data.data) {
                return;
            }

            for (const d of data.data) {
                const key = escapeHtml(d.key);
                const proxyDetailHtmlTemplate =
                    `<tr id="${key}">
                            <td>${escapeHtml(d.port)}</td>
                            <td>${escapeHtml(d.serverHost)}</td>
                            <td>${escapeHtml(d.serverPort)}</td>
                            <td><span class="badge bg-success">Running</span></td>
                            <td>
                                <button class="btn btn-sm btn-danger stop-btn" data-key="${key}">Stop</button>
                                <button class="btn btn-sm btn-warning chaos-list-btn" data-bs-toggle="modal" data-bs-target="#chaosList" data-key="${key}">
                                    View Chaos List
                                </button>
                                <button class="btn btn-sm btn-warning add-chaos-btn" data-bs-toggle="modal" data-bs-target="#addChaosModal" data-key="${key}">
                                    Add Chaos
                                </button>
                            </td>
                        </tr>
                   `;
                $proxyListTable.append(proxyDetailHtmlTemplate);
            }

            $proxyListTable.find('.stop-btn').on('click', (e) => {
                deleteProxy($(e.currentTarget).data('key'), () => {
                    loadProxies();
                });
            });

            $proxyListTable.find('.add-chaos-btn').on('click', (e) => {
                const proxyKey = $(e.currentTarget).data('key');
                $.ajax({
                    url: "/chaosConfig",
                    type: "GET",
                    success: function (response) {
                        $('#add-chaos-form').data('key', proxyKey);

                        const $chaosTypeSelect = $('.chaos-type-select-form');
                        $chaosTypeSelect.html('');

                        for (let r of response) {
                            let $option = $(`<option value="${escapeHtml(r.type)}">${escapeHtml(r.type)}</option>`);
                            $chaosTypeSelect.append($option);
                        }

                        $chaosTypeSelect.off('change').on('change', function () {
                            const selectedValue = $(this).val();
                            const config = response.filter(r => r.type === selectedValue)[0];
                            const fields = config ? config.fields : [];

                            const $fieldContainer = $('.field-container');
                            $fieldContainer.html('');

                            for (let f of fields) {
                                let $field = $(`<div class="mb-3"><label class="form-label">${escapeHtml(f)}</label>
                                    <input type="text" name="${escapeHtml(f)}" class="form-control"></div>`);
                                $fieldContainer.append($field);
                            }
                        });

                        $chaosTypeSelect.trigger('change');
                    },
                    error: function (xhr) {
                        console.error("Error:", xhr.responseText);
                    }
                });
            });

            $proxyListTable.find('.chaos-list-btn').on('click', (e) => {
                const proxyKey = $(e.currentTarget).data('key');
                $.ajax({
                    url: "/allChaos/" + proxyKey,
                    type: "GET",
                    success: function (response) {
                        const $chaosListTable = $('#chaos-list-table');
                        $chaosListTable.html('');

                        if (!response || !response.message) {
                            return;
                        }

                        for (let chaos of response.message) {
                            const copyChaosData = JSON.parse(JSON.stringify(chaos));
                            delete copyChaosData.type;
                            delete copyChaosData.line;
                            delete copyChaosData.id;

                            const chaosId = escapeHtml(chaos.id);
                            const chaosDetailHtml =
                                `<tr>
                                        <td>${escapeHtml(chaos.type)}</td>
                                        <td>${escapeHtml(chaos.line)}</td>
                                        <td><pre class="mb-0">${escapeHtml(JSON.stringify(copyChaosData, null, 2))}</pre></td>
                                        <td>
                                            <button class="btn btn-sm btn-danger remove-chaos-btn" id="chaos-${chaosId}" data-key="${chaosId}" data-proxy-key="${escapeHtml(proxyKey)}">Remove</button>
                                        </td>
                                    </tr>`;

                            $chaosListTable.append(chaosDetailHtml);

                            $(`#chaos-${chaosId}`).on('click', function () {
                                deleteChaos($(this).data('proxy-key'), chaos.id, () => loadProxies());
                                $('.btn-close').trigger('click');
                            });
                        }
                    },
                    error: function (xhr) {
                        console.error("Error:", xhr.responseText);
                    }
                });
            });
        },
        error: function (xhr) {
            console.error("Error:", xhr.responseText);
        }
    });
}

const cleanForm = ($form) => {
    $form.find('input').each(function () {
        $(this).val('');
    });
}

$(function () {
    loadProxies();

    $('.apply-chaos-btn').on('click', (e) => {
        e.preventDefault();

        const $form = $('#add-chaos-form');
        const chaosType = $form.find('.chaos-type-select-form').val();
        const line = $form.find('.chaos-line-select-form').val();

        const formData = {
            chaosType, line
        };

        $form.find('.field-container').find('input').each(function () {
            formData[$(this).attr('name')] = $(this).val();
        });

        $.ajax({
            url: "/addChaos/" + $form.data('key'),
            type: "POST",
            contentType: "application/json",
            data: JSON.stringify(formData),
            success: function (response) {
                loadProxies();
                cleanForm($form);
                $('.btn-close').trigger('click');
            },
            error: function (xhr) {
                console.error("Error:", xhr.responseText);
            }
        });
    });
});
