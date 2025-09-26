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
                const proxyDetailHtmlTemplate =
                    `<tr id="${d.key}">
                            <td>${d.port}</td>
                            <td>${d.serverHost}</td>
                            <td>${d.serverPort}</td>
                            <td><span class="badge bg-success">Running</span></td>
                            <td>
                                <button class="btn btn-sm btn-danger stop-btn" data-key=${d.key}>Stop</button>
                                <button class="btn btn-sm btn-warning chaos-list-btn" data-bs-toggle="modal" data-bs-target="#chaosList" data-key=${d.key}>
                                    View Chaos List
                                </button>
                                <button class="btn btn-sm btn-warning add-chaos-btn" data-bs-toggle="modal" data-bs-target="#addChaosModal" data-key=${d.key}>
                                    Add Chaos
                                </button>
                            </td>
                        </tr>
                   `;
                $proxyListTable.append(proxyDetailHtmlTemplate);
            }

            $proxyListTable.find('.stop-btn').on('click', (e) => {
                deleteProxy($(e.target).data()['key'], () => {
                    loadProxies();
                });
            });

            $proxyListTable.find('.add-chaos-btn').on('click', (e) => {
                $.ajax({
                    url: "/chaosConfig/",
                    type: "GET",
                    success: function (response) {
                        $('#add-chaos-form').data({
                            key: $(e.target).data()['key']
                        });

                        const $chaosTypeSelect = $('.chaos-type-select-form');
                        $chaosTypeSelect.html('');

                        for (let r of response) {
                            let $option = $(`<option value="${r.type}">${r.type}</option>`);
                            $chaosTypeSelect.append($option);
                        }

                        $chaosTypeSelect.on('change', function () {
                            const selectedValue = $(this).val();
                            const fields = response.filter(r => r.type === selectedValue)[0].fields;

                            const $fieldContainer = $('.field-container');
                            $fieldContainer.html('');

                            for (let f of fields) {
                                let $field = $(`<div class="mb-3"><label class="form-label">${f}</label>
                                    <input type="text" name="${f}" class="form-control"></div>`);
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
                $.ajax({
                    url: "/allChaos/" + $(e.target).data()['key'],
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

                            const chaosDetailHtml =
                                `<tr>
                                        <td>${chaos.type}</td>
                                        <td>${chaos.line}</td>
                                        <td>${JSON.stringify(copyChaosData, null, 2)}</td>
                                        <td>
                                            <button class="btn btn-sm btn-danger remove-chaos-btn" id="chaos-${chaos.id}" data-key=${chaos.id}>Remove</button>
                                        </td>
                                    </tr>`;

                            $chaosListTable.append(chaosDetailHtml);

                            $(`#chaos-${chaos.id}`).on('click', function () {
                                deleteChaos(d.key, chaos.id, () => loadProxies());
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
    $form.find('input').each(() => {
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

        $form.find('.field-container').find('input').each((function () {
            formData[$(this).attr('name')] = $(this).val();
        }));

        $.ajax({
            url: "/addChaos/" + $form.data().key,
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