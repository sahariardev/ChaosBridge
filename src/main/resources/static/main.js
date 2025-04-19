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

            for (var d of data.data) {
                const proxyDetailHtmlTemplate =
                    `<tr>
                            <td>${d.port}</td>
                            <td>${d.serverHost}</td>
                            <td>${d.serverPort}</td>
                            <td><span class="badge bg-success">Running</span></td>
                            <td>
                                <button class="btn btn-sm btn-danger stop-btn" data-key=${d.key}>Stop</button>
                                <button class="btn btn-sm btn-warning add-chaos-btn" data-bs-toggle="modal" data-bs-target="#addChaosModal">
                                    Add Chaos
                                </button>
                            </td>
                        </tr>
                   `;
                $proxyListTable.append(proxyDetailHtmlTemplate);
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
                            const $chaosTypeSelect = $('.chaos-type-select-form');
                            $chaosTypeSelect.html('');

                            for (let r of response) {
                                let $option = $(`<option value="${r.type}">${r.type}</option>`);
                                $chaosTypeSelect.append($option);
                            }

                            $chaosTypeSelect.on('change', function() {
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
            }
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
});