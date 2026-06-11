var treeData = null;
var totalClasses = 0;
var includePrefixes = [];
var excludePrefixes = [];
var currentConfigPath = '';
var progressTimer = null;

function loadConfig() {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/class/tree/config', true);
    xhr.onload = function() {
        if (xhr.status === 200) {
            var response = JSON.parse(xhr.responseText);
            if (response.status) {
                var data = response.data;
                document.getElementById('includeLabel').innerText = data.include || '';
                document.getElementById('excludeLabel').innerText = data.exclude || '';
                includePrefixes = data.includePrefixes || [];
                excludePrefixes = data.excludePrefixes || [];
            }
        }
    };
    xhr.send();
}

function loadTree() {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/class/tree.json', true);
    xhr.onload = function() {
        if (xhr.status === 200) {
            var response = JSON.parse(xhr.responseText);
            if (response.status) {
                buildTree(response.data);
            } else {
                document.getElementById('treeContent').innerHTML = '<span class="text-danger">Error: ' + response.msg + '</span>';
            }
        }
    };
    xhr.send();
}

function buildTree(data) {
    treeData = data;
    totalClasses = 0;
    var html = '';

    for (var i = 0; i < data.length; i++) {
        var loader = data[i];
        totalClasses += loader.totalCount || 0;
        var wovenCount = loader.wovenCount || 0;
        var totalCount = loader.totalCount || 0;

        html += '<div class="tree-node loader-node">';
        html += '<div class="tree-item tree-loader" onclick="toggleNode(this)">';
        html += '<span class="tree-icon glyphicon glyphicon-folder-close"></span>';
        html += '<span class="node-name">' + loader.name + '</span>';
        html += '<span class="badge" style="margin-left:10px;">' + wovenCount + '/' + totalCount + '</span>';
        html += '</div>';
        html += '<div class="tree-children">';

        // Build package hierarchy
        if (loader.children && loader.children.length > 0) {
            html += buildPackageTree(loader.children, 0);
        }

        html += '</div>';
        html += '</div>';
    }

    document.getElementById('treeContent').innerHTML = html;
    document.getElementById('loaderCount').innerText = data.length;
    document.getElementById('classCount').innerText = totalClasses;
}

function buildPackageTree(children, level) {
    var html = '';
    for (var i = 0; i < children.length; i++) {
        var node = children[i];
        var indent = level * 10;

        if (node.isClass) {
            // Class node - leaf, show 0/1 or 1/1
            var typeClass = node.type == 1 ? 'type-1' : 'type-0';
            var woven = node.wovenCount || (node.type == 1 ? 1 : 0);
            var total = node.totalCount || 1;

            html += '<div class="tree-item tree-class ' + typeClass + '" data-class-name="' + node.fullPath + '" style="margin-left:' + indent + 'px;">';
            html += '<span class="tree-icon glyphicon glyphicon-file"></span>';
            html += '<span class="class-name">' + node.name + '</span>';
            html += '<span class="badge" style="margin-left:10px;">' + woven + '/' + total + '</span>';
            html += '<button class="btn btn-xs btn-default config-btn" data-path="' + node.fullPath + '" data-is-class="true" onclick="openConfigModal(this)">';
            html += '<span class="glyphicon glyphicon-cog"></span>';
            html += '</button>';
            html += '</div>';
        } else {
            // Package node - has children
            var wovenCount = node.wovenCount || 0;
            var totalCount = node.totalCount || 0;

            html += '<div class="tree-node package-node">';
            html += '<div class="tree-item tree-package" onclick="toggleNode(this)" style="margin-left:' + indent + 'px;">';
            html += '<span class="tree-icon glyphicon glyphicon-folder-close"></span>';
            html += '<span class="node-name">' + node.name + '</span>';
            // 显示 woven数量/总数量 格式
            html += '<span class="badge" style="margin-left:10px;">' + wovenCount + '/' + totalCount + '</span>';
            html += '<button class="btn btn-xs btn-default config-btn" data-path="' + node.fullPath + '" data-is-class="false" onclick="openConfigModal(this)">';
            html += '<span class="glyphicon glyphicon-cog"></span>';
            html += '</button>';
            html += '</div>';
            html += '<div class="tree-children">';

            // Recursively build children
            if (node.children && node.children.length > 0) {
                html += buildPackageTree(node.children, level + 1);
            }

            html += '</div>';
            html += '</div>';
        }
    }
    return html;
}

function openConfigModal(btn) {
    var path = btn.getAttribute('data-path');
    var isClass = btn.getAttribute('data-is-class') === 'true';
    currentConfigPath = path;

    document.getElementById('configModalTitle').innerText = isClass ? 'Configure Class' : 'Configure Package';
    document.getElementById('configModalPath').innerText = path;

    // Determine current filter status by checking prefix matches
    var matchedInclude = null;
    var matchedExclude = null;

    for (var i = 0; i < includePrefixes.length; i++) {
        if (path.startsWith(includePrefixes[i]) || includePrefixes[i] === '*') {
            matchedInclude = includePrefixes[i];
            break;
        }
    }

    for (var i = 0; i < excludePrefixes.length; i++) {
        if (path.startsWith(excludePrefixes[i])) {
            matchedExclude = excludePrefixes[i];
            break;
        }
    }

    var statusEl = document.getElementById('configModalStatus');
    // Exclude takes priority (matching filter() logic)
    if (matchedExclude) {
        statusEl.innerHTML = '<span class="config-status-excluded">Currently excluded by prefix: ' + matchedExclude + '</span>';
    } else if (matchedInclude) {
        statusEl.innerHTML = '<span class="config-status-included">Currently included by prefix: ' + matchedInclude + '</span>';
    } else {
        statusEl.innerHTML = '<span class="config-status-default">Not matched by any prefix (default excluded)</span>';
    }

    // Show/hide remove buttons based on current status
    document.getElementById('actionRemoveInclude').style.display = matchedInclude ? 'block' : 'none';
    document.getElementById('actionRemoveExclude').style.display = matchedExclude ? 'block' : 'none';

    $('#configModal').modal('show');
}

function doConfigAction(action) {
    var prefix = currentConfigPath;
    $.post('/class/tree/set', {action: action, prefix: prefix}, function(str) {
        var ret = JSON.parse(str);
        if (ret.status) {
            $('#configModal').modal('hide');
            // Show progress bar and start polling
            showProgress();
            startProgressPolling();
        } else {
            alert('Failed: ' + ret.msg);
        }
    });
}

function showProgress() {
    document.getElementById('progressSection').style.display = 'block';
    updateProgressBar(0, 0, 0);
}

function hideProgress() {
    document.getElementById('progressSection').style.display = 'none';
}

function updateProgressBar(total, done, failed) {
    var percent = total > 0 ? Math.round(done * 100 / total) : 0;
    var bar = document.getElementById('progressBar');
    bar.style.width = percent + '%';
    bar.setAttribute('aria-valuenow', percent);
    document.getElementById('progressPercent').innerText = percent + '%';
    document.getElementById('progressText').innerText =
        'Retransforming: ' + done + ' / ' + total + ' classes (' + failed + ' failed)';
}

function startProgressPolling() {
    if (progressTimer) {
        clearInterval(progressTimer);
    }
    progressTimer = setInterval(function() {
        $.get('/class/tree/progress', function(str) {
            var ret = JSON.parse(str);
            if (ret.status) {
                var p = ret.data;
                updateProgressBar(p.total, p.done, p.failed);
                if (p.status === 'complete') {
                    clearInterval(progressTimer);
                    progressTimer = null;
                    // Change progress bar to success style
                    var bar = document.getElementById('progressBar');
                    bar.classList.remove('active');
                    bar.classList.remove('progress-bar-striped');
                    bar.classList.add('progress-bar-success');
                    // Refresh data after retransform completes
                    loadConfig();
                    loadTree();
                    // Auto-hide progress after 3 seconds
                    setTimeout(hideProgress, 3000);
                }
            }
        });
    }, 300);
}

$('#actionAddInclude').click(function() { doConfigAction('addInclude'); });
$('#actionAddExclude').click(function() { doConfigAction('addExclude'); });
$('#actionRemoveInclude').click(function() { doConfigAction('removeInclude'); });
$('#actionRemoveExclude').click(function() { doConfigAction('removeExclude'); });

function toggleNode(item) {
    var children = item.nextElementSibling;
    var icon = item.querySelector('.tree-icon');

    if (children.classList.contains('expanded')) {
        children.classList.remove('expanded');
        icon.classList.remove('glyphicon-folder-open');
        icon.classList.add('glyphicon-folder-close');
    } else {
        children.classList.add('expanded');
        icon.classList.remove('glyphicon-folder-close');
        icon.classList.add('glyphicon-folder-open');
    }
}

function expandAll() {
    var allChildren = document.querySelectorAll('.tree-children');
    var allIcons = document.querySelectorAll('.tree-item .tree-icon');
    for (var i = 0; i < allChildren.length; i++) {
        allChildren[i].classList.add('expanded');
    }
    for (var i = 0; i < allIcons.length; i++) {
        allIcons[i].classList.remove('glyphicon-folder-close');
        allIcons[i].classList.add('glyphicon-folder-open');
    }
}

function collapseAll() {
    var allChildren = document.querySelectorAll('.tree-children');
    var allIcons = document.querySelectorAll('.tree-item .tree-icon');
    for (var i = 0; i < allChildren.length; i++) {
        allChildren[i].classList.remove('expanded');
    }
    for (var i = 0; i < allIcons.length; i++) {
        allIcons[i].classList.remove('glyphicon-folder-open');
        allIcons[i].classList.add('glyphicon-folder-close');
    }
}

function filterTree(keyword) {
    keyword = keyword.toLowerCase();
    var allNodes = document.querySelectorAll('.tree-node');

    for (var i = 0; i < allNodes.length; i++) {
        var node = allNodes[i];
        var nodeMatch = false;
        var hasClassMatch = false;

        // Check node name
        var nodeNameEl = node.querySelector('.node-name');
        if (nodeNameEl) {
            var nodeName = nodeNameEl.innerText.toLowerCase();
            nodeMatch = nodeName.indexOf(keyword) >= 0;
        }

        // Check class items within this node
        var classItems = node.querySelectorAll('.tree-class');
        for (var j = 0; j < classItems.length; j++) {
            var cls = classItems[j];
            var className = cls.getAttribute('data-class-name').toLowerCase();
            if (className.indexOf(keyword) >= 0 || nodeMatch) {
                cls.style.display = '';
                hasClassMatch = true;
            } else {
                cls.style.display = 'none';
            }
        }

        // Show/hide node and expand if has matching class
        if (nodeMatch || hasClassMatch) {
            node.style.display = '';
            if (hasClassMatch && keyword.length > 0) {
                // Expand all children up to this node
                expandToNode(node);
            }
        } else {
            // Check if any child nodes match
            var childNodes = node.querySelectorAll('.tree-node');
            var childMatch = false;
            for (var k = 0; k < childNodes.length; k++) {
                var childNameEl = childNodes[k].querySelector('.node-name');
                if (childNameEl && childNameEl.innerText.toLowerCase().indexOf(keyword) >= 0) {
                    childMatch = true;
                    break;
                }
            }

            if (childMatch) {
                node.style.display = '';
                node.querySelector('.tree-children').classList.add('expanded');
                var icon = node.querySelector('.tree-item .tree-icon');
                if (icon) {
                    icon.classList.remove('glyphicon-folder-close');
                    icon.classList.add('glyphicon-folder-open');
                }
            } else {
                node.style.display = 'none';
            }
        }
    }
}

function expandToNode(node) {
    // Expand all parent nodes leading to this node
    var parent = node.parentElement;
    while (parent) {
        if (parent.classList.contains('tree-children')) {
            parent.classList.add('expanded');
            var prevSibling = parent.previousElementSibling;
            if (prevSibling) {
                var icon = prevSibling.querySelector('.tree-icon');
                if (icon) {
                    icon.classList.remove('glyphicon-folder-close');
                    icon.classList.add('glyphicon-folder-open');
                }
            }
        }
        parent = parent.parentElement;
    }
}

document.getElementById('expandAll').addEventListener('click', expandAll);
document.getElementById('collapseAll').addEventListener('click', collapseAll);

document.getElementById('searchInput').addEventListener('input', function() {
    filterTree(this.value);
});

// Load config and tree data on page load
loadConfig();
loadTree();