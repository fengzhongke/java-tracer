var treeData = null;
var totalThreads = 0;

function buildTree(data) {
    treeData = data;
    totalThreads = 0;
    var html = '';

    // Count total threads
    for (var i = 0; i < data.length; i++) {
        totalThreads += data[i].count || 0;
    }

    for (var i = 0; i < data.length; i++) {
        var frame = data[i];
        html += buildFrameNode(frame, 0);
    }

    document.getElementById('treeContent').innerHTML = html;
    document.getElementById('threadCount').innerText = totalThreads;
    document.getElementById('frameCount').innerText = data.length + ' root frames';
}

function buildFrameNode(node, level) {
    var indent = level * 10;
    var displayName = node.fullName || node.name;
    var lineInfo = node.lineNumber > 0 ? ':' + node.lineNumber : '';
    var html = '';

    if (node.isThread) {
        // Thread leaf node
        var stateClass = 'state-' + (node.state || 'unknown').toLowerCase();
        html += '<div class="tree-item tree-thread-leaf ' + stateClass + '" style="margin-left:' + indent + 'px;">';
        html += '<span class="tree-icon glyphicon glyphicon-user"></span>';
        html += '<span class="thread-name">' + node.name + '</span>';
        html += '<span class="thread-id" style="margin-left:5px;">[' + node.id + ']</span>';
        html += '<span class="thread-state" style="margin-left:5px;">' + (node.state || '') + '</span>';
        html += '</div>';
    } else if (node.children && node.children.length > 0) {
        // Frame node with children - expandable
        html += '<div class="tree-node">';
        html += '<div class="tree-item tree-frame" onclick="toggleNode(this)" data-frame-name="' + displayName + '" style="margin-left:' + indent + 'px;">';
        html += '<span class="tree-icon glyphicon glyphicon-folder-close"></span>';
        html += '<span class="frame-name">' + node.name + '</span>';
        html += '<span class="frame-class" style="margin-left:5px;color:#666;">' + (node.className || '') + lineInfo + '</span>';
        html += '<span class="badge" style="margin-left:10px;">' + node.count + '</span>';
        html += '</div>';
        html += '<div class="tree-children">';

        // Build children recursively
        for (var i = 0; i < node.children.length; i++) {
            html += buildFrameNode(node.children[i], level + 1);
        }

        html += '</div>';
        html += '</div>';
    } else {
        // Leaf frame - no children
        html += '<div class="tree-item tree-frame" data-frame-name="' + displayName + '" style="margin-left:' + indent + 'px;">';
        html += '<span class="tree-icon glyphicon glyphicon-record"></span>';
        html += '<span class="frame-name">' + node.name + '</span>';
        html += '<span class="frame-class" style="margin-left:5px;color:#666;">' + (node.className || '') + lineInfo + '</span>';
        html += '</div>';
    }

    return html;
}

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
        var hasFrameMatch = false;

        // Check frame name and class
        var frameItems = node.querySelectorAll('.tree-frame');
        for (var j = 0; j < frameItems.length; j++) {
            var frame = frameItems[j];
            var frameName = frame.getAttribute('data-frame-name');
            if (frameName) {
                frameName = frameName.toLowerCase();
                if (frameName.indexOf(keyword) >= 0) {
                    frame.style.display = '';
                    hasFrameMatch = true;
                } else {
                    frame.style.display = 'none';
                }
            }
        }

        // Check thread leaf nodes
        var threadLeaves = node.querySelectorAll('.tree-thread-leaf');
        for (var j = 0; j < threadLeaves.length; j++) {
            var leaf = threadLeaves[j];
            var threadName = leaf.querySelector('.thread-name');
            var threadId = leaf.querySelector('.thread-id');
            var threadState = leaf.querySelector('.thread-state');
            var match = false;
            if (threadName && threadName.innerText.toLowerCase().indexOf(keyword) >= 0) match = true;
            if (threadId && threadId.innerText.toLowerCase().indexOf(keyword) >= 0) match = true;
            if (threadState && threadState.innerText.toLowerCase().indexOf(keyword) >= 0) match = true;
            if (match) {
                leaf.style.display = '';
                hasFrameMatch = true;
            } else {
                leaf.style.display = 'none';
            }
        }

        // Show/hide node and expand if has matching content
        if (hasFrameMatch) {
            node.style.display = '';
            if (keyword.length > 0) {
                expandToNode(node);
            }
        } else {
            // Check if any child nodes match
            var childNodes = node.querySelectorAll('.tree-node');
            var childMatch = false;
            for (var k = 0; k < childNodes.length; k++) {
                var child = childNodes[k];
                var childFrame = child.querySelector('.tree-frame');
                if (childFrame) {
                    var childFrameName = childFrame.getAttribute('data-frame-name');
                    if (childFrameName && childFrameName.toLowerCase().indexOf(keyword) >= 0) {
                        childMatch = true;
                        break;
                    }
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

// Load tree data
var xhr = new XMLHttpRequest();
xhr.open('GET', '/thread/tree.json', true);
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