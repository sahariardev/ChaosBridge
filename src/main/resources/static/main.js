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
            $('#proxy-list-table').html('');

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
                                <button class="btn btn-sm btn-warning" data-bs-toggle="modal" data-bs-target="#addChaosModal">Add
                                    Chaos
                                </button>
                            </td>
                        </tr>
                   `;
                $('#proxy-list-table').append(proxyDetailHtmlTemplate);
                $('#proxy-list-table').find('.stop-btn').on('click', (e) => {
                    deleteProxy($(e.target).data()['key'], () => {
                        loadProxies();
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