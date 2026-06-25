var treeData = null;
var totalThreads = 0;
var originalHTML = '';
var originalThreadCount = 0;
var originalFrameCountText = '';
var selectedThreadIds = [];

function buildTree(data) {
    treeData = data;
    totalThreads = 0;
    var html = '';

    for (var i = 0; i < data.length; i++) {
        totalThreads += data[i].count || 0;
    }

    for (var i = 0; i < data.length; i++) {
        var frame = data[i];
        html += buildFrameNode(frame, 0, true);
    }

    document.getElementById('treeContent').innerHTML = html;
    originalHTML = html;
    originalThreadCount = totalThreads;
    originalFrameCountText = data.length + ' root frames';
    document.getElementById('threadCount').innerText = totalThreads;
    document.getElementById('frameCount').innerText = originalFrameCountText;
}

function buildFrameNode(node, level, showCheckbox) {
    var indent = level * 10;
    var displayName = node.fullName || node.name;
    var lineInfo = node.lineNumber > 0 ? ':' + node.lineNumber : '';
    var html = '';

    if (node.isThread) {
        // Thread leaf node
        var stateClass = 'state-' + (node.state || 'unknown').toLowerCase();
        html += '<div class="tree-item tree-thread-leaf ' + stateClass + '" style="margin-left:' + indent + 'px;">';
        if (showCheckbox) {
            html += '<input type="checkbox" class="thread-checkbox" data-thread-id="' + node.id + '" data-thread-name="' + node.name + '" data-thread-state="' + (node.state || '') + '">';
        }
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

        for (var i = 0; i < node.children.length; i++) {
            html += buildFrameNode(node.children[i], level + 1, showCheckbox);
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
    scopeExpandCollapse(document, 'expand');
}

function collapseAll() {
    scopeExpandCollapse(document, 'collapse');
}

function expandModalAll() {
    scopeExpandCollapse(document.getElementById('threadStackBody'), 'expand');
}

function collapseModalAll() {
    scopeExpandCollapse(document.getElementById('threadStackBody'), 'collapse');
}

function scopeExpandCollapse(root, action) {
    var allChildren = root.querySelectorAll('.tree-children');
    var allIcons = root.querySelectorAll('.tree-item .tree-icon');
    for (var i = 0; i < allChildren.length; i++) {
        if (action === 'expand') {
            allChildren[i].classList.add('expanded');
        } else {
            allChildren[i].classList.remove('expanded');
        }
    }
    for (var i = 0; i < allIcons.length; i++) {
        if (action === 'expand') {
            allIcons[i].classList.remove('glyphicon-folder-close');
            allIcons[i].classList.add('glyphicon-folder-open');
        } else {
            allIcons[i].classList.remove('glyphicon-folder-open');
            allIcons[i].classList.add('glyphicon-folder-close');
        }
    }
}

function expandToNode(node) {
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

// ========== Select All ==========

function toggleSelectAll() {
    var checkboxes = document.querySelectorAll('.thread-checkbox');
    var allChecked = true;
    for (var i = 0; i < checkboxes.length; i++) {
        if (!checkboxes[i].checked) { allChecked = false; break; }
    }
    for (var i = 0; i < checkboxes.length; i++) {
        checkboxes[i].checked = !allChecked;
    }
}

// ========== Search / Filter ==========

function collectMatches(data, keyword, ancestors) {
    var results = [];
    for (var i = 0; i < data.length; i++) {
        var node = data[i];
        var currentAncestors = ancestors.slice();

        if (node.isThread) {
            var threadMatch = false;
            if (node.name && node.name.toLowerCase().indexOf(keyword) >= 0) threadMatch = true;
            if (node.id !== undefined && String(node.id).toLowerCase().indexOf(keyword) >= 0) threadMatch = true;
            if (node.state && node.state.toLowerCase().indexOf(keyword) >= 0) threadMatch = true;

            var matchingFrame = null;
            for (var j = currentAncestors.length - 1; j >= 0; j--) {
                var f = currentAncestors[j];
                var fname = (f.fullName || f.name || '').toLowerCase();
                if (fname.indexOf(keyword) >= 0) {
                    matchingFrame = f;
                    break;
                }
            }

            if (threadMatch || matchingFrame) {
                results.push({ frame: matchingFrame, thread: node });
            }
        } else if (node.children && node.children.length > 0) {
            currentAncestors.push(node);
            results = results.concat(collectMatches(node.children, keyword, currentAncestors));
        } else {
            var fname = (node.fullName || node.name || '').toLowerCase();
            if (fname.indexOf(keyword) >= 0) {
                results.push({ frame: node, thread: null });
            }
        }
    }
    return results;
}

function filterTree(keyword) {
    keyword = keyword.toLowerCase();

    if (keyword.length === 0) {
        document.getElementById('treeContent').innerHTML = originalHTML;
        document.getElementById('threadCount').innerText = originalThreadCount;
        document.getElementById('frameCount').innerText = originalFrameCountText;
        return;
    }

    var results = collectMatches(treeData, keyword, []);

    var groups = {};
    var threadOnlyMatches = [];
    for (var i = 0; i < results.length; i++) {
        var r = results[i];
        if (r.frame) {
            var key = r.frame.fullName || r.frame.name;
            if (!groups[key]) groups[key] = { frame: r.frame, threads: [] };
            var dup = false;
            for (var j = 0; j < groups[key].threads.length; j++) {
                if (groups[key].threads[j].id === r.thread.id && groups[key].threads[j].name === r.thread.name) {
                    dup = true; break;
                }
            }
            if (!dup) groups[key].threads.push(r.thread);
        } else if (r.thread) {
            var dup = false;
            for (var j = 0; j < threadOnlyMatches.length; j++) {
                if (threadOnlyMatches[j].id === r.thread.id && threadOnlyMatches[j].name === r.thread.name) {
                    dup = true; break;
                }
            }
            if (!dup) threadOnlyMatches.push(r.thread);
        }
    }

    var html = '';
    var matchThreadCount = 0;
    var groupKeys = Object.keys(groups);

    for (var i = 0; i < groupKeys.length; i++) {
        var group = groups[groupKeys[i]];
        var frame = group.frame;
        var lineInfo = frame.lineNumber > 0 ? ':' + frame.lineNumber : '';

        html += '<div style="margin-bottom:10px;border-bottom:1px solid #eee;padding-bottom:5px;">';
        html += '<div class="tree-item" style="margin-left:0px;font-weight:bold;color:#d9534f;">';
        html += '<span class="glyphicon glyphicon-tag" style="margin-right:5px;"></span>';
        html += '<span>' + frame.name + '</span>';
        html += '<span style="margin-left:5px;color:#999;">' + (frame.className || '') + lineInfo + '</span>';
        html += '<span class="badge" style="margin-left:10px;">' + group.threads.length + ' threads</span>';
        html += '</div>';

        for (var j = 0; j < group.threads.length; j++) {
            var thread = group.threads[j];
            var stateClass = 'state-' + (thread.state || 'unknown').toLowerCase();
            html += '<div class="tree-item tree-thread-leaf ' + stateClass + '" style="margin-left:20px;">';
            html += '<input type="checkbox" class="thread-checkbox" data-thread-id="' + thread.id + '" data-thread-name="' + thread.name + '" data-thread-state="' + (thread.state || '') + '">';
            html += '<span class="tree-icon glyphicon glyphicon-user"></span>';
            html += '<span class="thread-name">' + thread.name + '</span>';
            html += '<span class="thread-id" style="margin-left:5px;">[' + thread.id + ']</span>';
            html += '<span class="thread-state" style="margin-left:5px;">' + (thread.state || '') + '</span>';
            html += '</div>';
        }

        html += '</div>';
        matchThreadCount += group.threads.length;
    }

    for (var i = 0; i < threadOnlyMatches.length; i++) {
        var thread = threadOnlyMatches[i];
        var stateClass = 'state-' + (thread.state || 'unknown').toLowerCase();
        html += '<div class="tree-item tree-thread-leaf ' + stateClass + '" style="margin-left:0px;">';
        html += '<input type="checkbox" class="thread-checkbox" data-thread-id="' + thread.id + '" data-thread-name="' + thread.name + '" data-thread-state="' + (thread.state || '') + '">';
        html += '<span class="tree-icon glyphicon glyphicon-user"></span>';
        html += '<span class="thread-name">' + thread.name + '</span>';
        html += '<span class="thread-id" style="margin-left:5px;">[' + thread.id + ']</span>';
        html += '<span class="thread-state" style="margin-left:5px;">' + (thread.state || '') + '</span>';
        html += '</div>';
        matchThreadCount++;
    }

    if (matchThreadCount === 0) {
        html = '<div class="text-muted" style="padding:20px;">No matches found</div>';
    }

    document.getElementById('treeContent').innerHTML = html;
    document.getElementById('threadCount').innerText = matchThreadCount;
    document.getElementById('frameCount').innerText = groupKeys.length + ' matched frames';
}

// ========== Thread Stack Detail Modal (merged tree) ==========

function getSelectedThreads() {
    var checkboxes = document.querySelectorAll('.thread-checkbox:checked');
    var threads = [];
    for (var i = 0; i < checkboxes.length; i++) {
        threads.push({
            id: checkboxes[i].getAttribute('data-thread-id'),
            name: checkboxes[i].getAttribute('data-thread-name'),
            state: checkboxes[i].getAttribute('data-thread-state')
        });
    }
    selectedThreadIds = threads;
    return threads;
}

function viewSelectedStacks() {
    var threads = getSelectedThreads();
    if (threads.length === 0) {
        alert('Please select at least one thread');
        return;
    }
    loadAndMergeThreadStacks(threads);
    $('#threadStackModal').modal('show');
}

function loadAndMergeThreadStacks(threads) {
    var body = document.getElementById('threadStackBody');
    body.innerHTML = '<span class="text-muted">Loading stack traces...</span>';

    var threadData = [];
    var completed = 0;
    var total = threads.length;
    var failedCount = 0;

    for (var i = 0; i < threads.length; i++) {
        fetchThreadStackData(threads[i].id, threads[i].name, threads[i].state, function(data) {
            if (data) {
                threadData.push(data);
            } else {
                failedCount++;
            }
            completed++;
            if (completed === total) {
                if (threadData.length === 0) {
                    body.innerHTML = '<span class="text-muted">No stack traces found</span>';
                } else {
                    var merged = mergeStacks(threadData);
                    var html = '';
                    for (var j = 0; j < merged.length; j++) {
                        html += buildFrameNode(merged[j], 0, false);
                    }
                    if (failedCount > 0) {
                        html += '<div class="text-danger" style="margin-top:10px;">' + failedCount + ' thread(s) could not be loaded</div>';
                    }
                    body.innerHTML = html;
                }
            }
        });
    }
}

function fetchThreadStackData(tid, tname, tstate, callback) {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/thread/detail.json?id=' + tid, true);
    xhr.onload = function() {
        if (xhr.status === 200) {
            try {
                var response = JSON.parse(xhr.responseText);
                if (response.status && response.data) {
                    callback({ id: tid, name: tname, state: tstate, stacks: response.data });
                } else {
                    callback(null);
                }
            } catch (e) {
                callback(null);
            }
        } else {
            callback(null);
        }
    };
    xhr.onerror = function() { callback(null); };
    xhr.send();
}

/**
 * Merge multiple thread stacks into a shared tree (same algorithm as backend threadJson).
 * Each stack is processed bottom-to-top, same-path frames are merged with count increment.
 * Thread leaves are attached at the deepest frame.
 */
function mergeStacks(threadDataList) {
    var root = { name: 'root', children: [], childrenMap: {} };

    for (var i = 0; i < threadDataList.length; i++) {
        var threadInfo = threadDataList[i];
        var stacks = threadInfo.stacks;
        var current = root;

        // Build from bottom to top, merging same paths
        for (var j = stacks.length - 1; j >= 0; j--) {
            var frame = stacks[j];
            var key = frame.className + '#' + frame.methodName + '#' + frame.lineNumber;

            var frameNode = current.childrenMap[key];
            if (!frameNode) {
                frameNode = {
                    name: frame.methodName,
                    fullName: frame.className + '.' + frame.methodName,
                    className: frame.className,
                    lineNumber: frame.lineNumber,
                    count: 0,
                    isFrame: true,
                    isThread: false,
                    children: [],
                    childrenMap: {}
                };
                current.childrenMap[key] = frameNode;
                current.children.push(frameNode);
            }
            frameNode.count++;
            current = frameNode;
        }

        // Add thread leaf at deepest frame
        if (current !== root) {
            var threadLeaf = {
                name: threadInfo.name,
                id: threadInfo.id,
                state: threadInfo.state,
                count: 1,
                isFrame: false,
                isThread: true,
                children: [],
                childrenMap: {}
            };
            current.children.push(threadLeaf);
        }
    }

    // Sort children by count descending, recursively
    sortMergedNodes(root.children);
    return root.children;
}

function sortMergedNodes(nodes) {
    nodes.sort(function(a, b) { return b.count - a.count; });
    for (var i = 0; i < nodes.length; i++) {
        if (nodes[i].children && nodes[i].children.length > 0) {
            sortMergedNodes(nodes[i].children);
        }
    }
}

function refreshStacks() {
    if (selectedThreadIds.length === 0) return;
    loadAndMergeThreadStacks(selectedThreadIds);
}

// ========== Event listeners ==========

document.getElementById('expandAll').addEventListener('click', expandAll);
document.getElementById('collapseAll').addEventListener('click', collapseAll);
document.getElementById('selectAllBtn').addEventListener('click', toggleSelectAll);
document.getElementById('viewStackBtn').addEventListener('click', viewSelectedStacks);
document.getElementById('refreshStackBtn').addEventListener('click', refreshStacks);
document.getElementById('expandModalAll').addEventListener('click', expandModalAll);
document.getElementById('collapseModalAll').addEventListener('click', collapseModalAll);

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
