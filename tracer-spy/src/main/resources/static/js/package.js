function buildTree(node, level) {
    var html = '<div class="tree-node" data-path="' + node.fullPath + '" style="padding: 5px 0;">';
    var icon = node.children && node.children.length > 0 ? 'glyphicon-plus' : 'glyphicon-minus';
    html += '<span class="glyphicon tree-icon ' + icon + '"></span>';
    html += '<strong>' + node.name + '</strong>';
    html += '<span class="tree-count tree-woven">woven: ' + node.totalWoven + '</span>';
    html += '<span class="tree-count tree-notwoven">not woven: ' + node.totalNotWoven + '</span>';
    html += '</div>';

    if (node.children && node.children.length > 0) {
        html += '<div class="tree-children" id="children_' + node.fullPath.replace(/[^a-zA-Z0-9]/g, '_') + '">';
        for (var i = 0; i < node.children.length; i++) {
            html += buildTree(node.children[i], level + 1);
        }
        html += '</div>';
    }
    return html;
}

function loadData() {
    $.ajax({
        url: '/package/get.json',
        type: 'GET',
        success: function(ret) {
            if (ret.status) {
                var data = ret.data;
                $('#loader_count').text(data.loaderCount);
                $('#total_classes').text(data.totalClasses);
                $('#total_woven').text(data.totalWoven);
                $('#total_notwoven').text(data.totalNotWoven);

                var treeHtml = '';
                if (data.packages && data.packages.length > 0) {
                    for (var i = 0; i < data.packages.length; i++) {
                        treeHtml += buildTree(data.packages[i], 0);
                    }
                } else {
                    treeHtml = '<p class="text-muted">No packages found</p>';
                }
                $('#package_tree').html(treeHtml);

                $('.tree-node').click(function() {
                    var icon = $(this).find('.tree-icon');
                    var childrenId = 'children_' + $(this).data('path').replace(/[^a-zA-Z0-9]/g, '_');
                    var children = $('#' + childrenId);
                    if (children.length) {
                        if (children.hasClass('expanded')) {
                            children.removeClass('expanded');
                            icon.removeClass('glyphicon-minus').addClass('glyphicon-plus');
                        } else {
                            children.addClass('expanded');
                            icon.removeClass('glyphicon-plus').addClass('glyphicon-minus');
                        }
                    }
                });
            } else {
                $('#package_tree').html('<p class="text-danger">Error: ' + ret.msg + '</p>');
            }
        },
        error: function() {
            $('#package_tree').html('<p class="text-danger">Failed to load data</p>');
        }
    });
}

$('#refresh_btn').click(function() {
    loadData();
});

loadData();