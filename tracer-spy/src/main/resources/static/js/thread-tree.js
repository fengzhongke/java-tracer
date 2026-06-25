var treeData = null;
var totalThreads = 0;
var originalHTML = '';
var originalThreadCount = 0;
var originalFrameCountText = '';
var selectedThreadIds = [];
var samplerPollInterval = null;
var samplerThreshold = 0;
var maxDepth = 0;
var cachedSamplerData = null;
var currentSamplerId = null;
var pageExpanded = false;
var modalExpanded = false;
var threadsVisible = true;
var modalThreadsVisible = true;

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

/**
 * Build tree HTML node.
 * @param node - tree data node
 * @param level - indentation level
 * @param showCheckbox - show thread checkboxes (main page only)
 * @param rootCount - root frame's count, used as denominator for percentage (modal only)
 * @param threshold - minimum percentage to display, 0 = show all (modal only)
 */
function buildFrameNode(node, level, showCheckbox, rootCount, threshold) {
    var indent = level * 10;
    var displayName = node.fullName || node.name;
    var lineInfo = node.lineNumber > 0 ? ':' + node.lineNumber : '';
    var pct = rootCount > 0 ? (node.count / rootCount * 100) : 0;

    // Threshold filter: skip node if pct < threshold AND no visible descendant
    if (threshold > 0 && pct < threshold && !hasVisibleDescendant(node, rootCount, threshold)) {
        return '';
    }

    var html = '';

    if (node.isThread) {
        var stateClass = 'state-' + (node.state || 'unknown').toLowerCase();
        html += '<div class="tree-item tree-thread-leaf ' + stateClass + '" data-level="' + level + '" style="margin-left:' + indent + 'px;">';
        if (showCheckbox) {
            html += '<input type="checkbox" class="thread-checkbox" data-thread-id="' + node.id + '" data-thread-name="' + node.name + '" data-thread-state="' + (node.state || '') + '">';
        }
        html += '<span class="tree-icon glyphicon glyphicon-user"></span>';
        html += '<span class="thread-name">' + node.name + '</span>';
        html += '<span class="thread-id" style="margin-left:5px;">[' + node.id + ']</span>';
        html += '<span class="thread-state" style="margin-left:5px;">' + (node.state || '') + '</span>';
        if (node.cpuPct !== undefined && node.cpuPct !== null) {
            var cpuColor = node.cpuPct >= 50 ? '#d9534f' : (node.cpuPct >= 10 ? '#f0ad4e' : '#5bc0de');
            html += '<span class="badge" style="margin-left:5px;background:' + cpuColor + ';">cpu ' + node.cpuPct.toFixed(1) + '%</span>';
        }
        html += '</div>';
    } else if (node.children && node.children.length > 0) {
        // Separate thread children from frame children
        var threadChildren = [];
        var frameChildren = [];
        for (var c = 0; c < node.children.length; c++) {
            if (node.children[c].isThread) {
                threadChildren.push(node.children[c]);
            } else {
                frameChildren.push(node.children[c]);
            }
        }

        html += '<div class="tree-node">';
        html += '<div class="tree-item tree-frame" onclick="toggleNode(this)" data-frame-name="' + displayName + '" data-level="' + level + '" style="margin-left:' + indent + 'px;">';
        html += '<span class="tree-icon glyphicon glyphicon-folder-close"></span>';
        html += '<span class="frame-name">' + node.name + '</span>';
        html += '<span class="frame-class" style="margin-left:5px;color:#666;">' + (node.className || '') + lineInfo + '</span>';
        var badgeText = '' + node.count;
        if (rootCount > 0) {
            badgeText += ' (' + pct.toFixed(1) + '%)';
        }
        html += '<span class="badge" style="margin-left:10px;">' + badgeText + '</span>';
        html += '</div>';

        // Thread leaves in separate container (always visible, controlled by Hide Threads)
        if (threadChildren.length > 0) {
            html += '<div class="tree-threads">';
            for (var t = 0; t < threadChildren.length; t++) {
                html += buildFrameNode(threadChildren[t], level + 1, showCheckbox, rootCount, threshold);
            }
            html += '</div>';
        }

        // Frame children in collapsible container
        if (frameChildren.length > 0) {
            html += '<div class="tree-children">';
            for (var f = 0; f < frameChildren.length; f++) {
                html += buildFrameNode(frameChildren[f], level + 1, showCheckbox, rootCount, threshold);
            }
            html += '</div>';
        }

        html += '</div>';
    } else {
        html += '<div class="tree-item tree-frame" data-frame-name="' + displayName + '" data-level="' + level + '" style="margin-left:' + indent + 'px;">';
        html += '<span class="tree-icon glyphicon glyphicon-record"></span>';
        html += '<span class="frame-name">' + node.name + '</span>';
        html += '<span class="frame-class" style="margin-left:5px;color:#666;">' + (node.className || '') + lineInfo + '</span>';
        if (rootCount > 0 && node.count) {
            html += '<span class="badge" style="margin-left:10px;">' + node.count + ' (' + pct.toFixed(1) + '%)</span>';
        }
        html += '</div>';
    }

    return html;
}

/**
 * Check if a node has any descendant with pct >= threshold
 */
function hasVisibleDescendant(node, rootCount, threshold) {
    if (!node.children || node.children.length === 0) return false;
    for (var i = 0; i < node.children.length; i++) {
        var child = node.children[i];
        var childPct = rootCount > 0 ? (child.count / rootCount * 100) : 0;
        if (childPct >= threshold) return true;
        if (hasVisibleDescendant(child, rootCount, threshold)) return true;
    }
    return false;
}

function toggleNode(item) {
    var node = item.parentElement;
    var children = node.querySelector(':scope > .tree-children');
    var icon = item.querySelector('.tree-icon');

    if (!children) return;

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

// ========== Expand/Collapse Toggle ==========

function applyThreadVisibility(root, visible) {
    var containers = root.querySelectorAll('.tree-threads');
    for (var i = 0; i < containers.length; i++) {
        if (visible) {
            containers[i].classList.remove('hidden-threads');
        } else {
            containers[i].classList.add('hidden-threads');
        }
    }
}

function togglePageExpand() {
    var root = document.getElementById('treeContainer');
    if (pageExpanded) {
        scopeExpandCollapse(root, 'collapse');
        pageExpanded = false;
        document.getElementById('toggleExpandBtn').innerText = 'Expand';
        document.getElementById('toggleExpandBtn').className = 'btn btn-primary btn-sm';
    } else {
        scopeExpandCollapse(root, 'expand');
        pageExpanded = true;
        document.getElementById('toggleExpandBtn').innerText = 'Collapse';
        document.getElementById('toggleExpandBtn').className = 'btn btn-default btn-sm';
    }
}

function toggleModalExpand() {
    var root = document.getElementById('threadStackBody');
    if (modalExpanded) {
        scopeExpandCollapse(root, 'collapse');
        modalExpanded = false;
        document.getElementById('toggleModalExpandBtn').innerText = 'Expand';
        document.getElementById('toggleModalExpandBtn').className = 'btn btn-primary btn-sm';
    } else {
        expandUpToDepth();
        modalExpanded = true;
        document.getElementById('toggleModalExpandBtn').innerText = 'Collapse';
        document.getElementById('toggleModalExpandBtn').className = 'btn btn-default btn-sm';
    }
}

// ========== Threads Visibility Toggle ==========

function togglePageThreads() {
    threadsVisible = !threadsVisible;
    applyThreadVisibility(document.getElementById('treeContainer'), threadsVisible);
    document.getElementById('toggleThreadsBtn').innerText = threadsVisible ? 'Hide Threads' : 'Show Threads';
    document.getElementById('toggleThreadsBtn').className = threadsVisible ? 'btn btn-default btn-sm' : 'btn btn-success btn-sm';
}

function toggleModalThreads() {
    modalThreadsVisible = !modalThreadsVisible;
    applyThreadVisibility(document.getElementById('threadStackBody'), modalThreadsVisible);
    document.getElementById('toggleModalThreadsBtn').innerText = modalThreadsVisible ? 'Hide Threads' : 'Show Threads';
    document.getElementById('toggleModalThreadsBtn').className = modalThreadsVisible ? 'btn btn-default btn-sm' : 'btn btn-success btn-sm';
}

function expandUpToDepth() {
    var root = document.getElementById('threadStackBody');
    // If maxDepth=0, expand everything (no depth limit)
    if (maxDepth <= 0) {
        scopeExpandCollapse(root, 'expand');
        return;
    }
    // Expand nodes whose level < maxDepth
    var allItems = root.querySelectorAll('.tree-item[data-level]');
    for (var i = 0; i < allItems.length; i++) {
        var level = parseInt(allItems[i].getAttribute('data-level')) || 0;
        var children = allItems[i].nextElementSibling;
        var icon = allItems[i].querySelector('.tree-icon');
        if (level < maxDepth && children && children.classList.contains('tree-children')) {
            children.classList.add('expanded');
            if (icon) {
                icon.classList.remove('glyphicon-folder-close');
                icon.classList.add('glyphicon-folder-open');
            }
        }
    }
    // Expand immediate parent of thread leaves that are within one level beyond maxDepth
    // Deeper threads stay inside collapsed frames — user can click to expand one level at a time
    var threadLeaves = root.querySelectorAll('.tree-thread-leaf');
    for (var i = 0; i < threadLeaves.length; i++) {
        var threadLevel = parseInt(threadLeaves[i].getAttribute('data-level')) || 0;
        // Only auto-reveal threads at maxDepth+1 (one level beyond the expanded boundary)
        if (threadLevel === maxDepth + 1) {
            var parent = threadLeaves[i].parentElement;
            if (parent && parent.classList.contains('tree-children') && !parent.classList.contains('expanded')) {
                parent.classList.add('expanded');
                var prevItem = parent.previousElementSibling;
                var icon = prevItem ? prevItem.querySelector('.tree-icon') : null;
                if (icon) {
                    icon.classList.remove('glyphicon-folder-close');
                    icon.classList.add('glyphicon-folder-open');
                }
            }
        }
    }
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

            if (threadMatch) {
                // Thread name matches — show full path from root to this thread
                results.push({ ancestors: currentAncestors, thread: node, matchedFrame: null });
            } else {
                // Check ancestor frames for method name match
                var matchingFrame = null;
                var matchingIdx = -1;
                for (var j = currentAncestors.length - 1; j >= 0; j--) {
                    var f = currentAncestors[j];
                    var fname = (f.fullName || f.name || '').toLowerCase();
                    if (fname.indexOf(keyword) >= 0) {
                        matchingFrame = f;
                        matchingIdx = j;
                        break;
                    }
                }
                if (matchingFrame) {
                    results.push({ ancestors: currentAncestors, thread: node, matchedFrame: matchingFrame, matchedIdx: matchingIdx });
                }
            }
        } else if (node.children && node.children.length > 0) {
            currentAncestors.push(node);
            results = results.concat(collectMatches(node.children, keyword, currentAncestors));
        }
    }
    return results;
}

/**
 * Group search results by their call chain path (root → matched frame).
 * Each group contains: the ancestor frames, which frame matched, and the list of threads.
 */
function buildPathGroups(results) {
    var groups = {};
    var groupOrder = [];

    for (var i = 0; i < results.length; i++) {
        var r = results[i];
        var ancestors = r.ancestors;
        var endIdx = r.matchedIdx >= 0 ? r.matchedIdx : ancestors.length - 1;

        // Build path key from ancestor chain (root to matched frame or full path)
        var pathKey = '';
        for (var j = 0; j <= endIdx && j < ancestors.length; j++) {
            pathKey += (ancestors[j].fullName || ancestors[j].name) + '#' + ancestors[j].lineNumber + '|';
        }

        if (!groups[pathKey]) {
            groups[pathKey] = {
                ancestors: ancestors,
                endIdx: endIdx,
                isFrameMatch: r.matchedIdx >= 0,
                matchedIdx: r.matchedIdx >= 0 ? r.matchedIdx : -1,
                threads: []
            };
            groupOrder.push(pathKey);
        }

        if (r.thread) {
            groups[pathKey].threads.push(r.thread);
        }
    }

    // Convert to array in insertion order, then sort by thread count descending
    var result = [];
    for (var i = 0; i < groupOrder.length; i++) {
        result.push(groups[groupOrder[i]]);
    }
    result.sort(function(a, b) { return b.threads.length - a.threads.length; });

    return result;
}

/**
 * Render a path group as a stack-trace style display:
 *   Header: matched frame name + thread count (clickable to toggle)
 *   Call path: each frame on its own line, matched frame highlighted
 *   Thread list: thread items with checkboxes
 */
function buildPathGroupHtml(group, showCheckbox) {
    var ancestors = group.ancestors;
    var endIdx = group.endIdx;
    var isFrameMatch = group.isFrameMatch;
    var matchedIdx = group.matchedIdx;
    var threads = group.threads;

    var html = '<div class="search-path-group">';

    // Header: matched frame summary + thread count (clickable to expand/collapse)
    html += '<div class="search-call-chain" onclick="togglePathGroup(this)">';
    html += '<span class="search-toggle-icon glyphicon glyphicon-chevron-down"></span>';

    if (isFrameMatch && matchedIdx >= 0 && matchedIdx < ancestors.length) {
        var matchedFrame = ancestors[matchedIdx];
        var matchedName = matchedFrame.fullName || matchedFrame.name;
        var matchedLine = matchedFrame.lineNumber > 0 ? ':' + matchedFrame.lineNumber : '';
        html += '<span class="search-matched">' + matchedName + matchedLine + '</span>';
    } else if (endIdx >= 0 && endIdx < ancestors.length) {
        var lastFrame = ancestors[endIdx];
        var lastName = lastFrame.fullName || lastFrame.name;
        var lastLine = lastFrame.lineNumber > 0 ? ':' + lastFrame.lineNumber : '';
        html += '<span class="search-matched">' + lastName + lastLine + '</span>';
    }

    html += ' <span class="badge search-thread-badge">' + threads.length + ' threads</span>';
    html += '</div>';

    // Call path: each frame as a separate line (stack trace style)
    html += '<div class="search-frame-list">';
    for (var j = 0; j <= endIdx && j < ancestors.length; j++) {
        var frame = ancestors[j];
        var displayName = frame.fullName || frame.name;
        var lineInfo = frame.lineNumber > 0 ? ':' + frame.lineNumber : '';

        if (isFrameMatch && j === matchedIdx) {
            html += '<div class="search-frame-line search-frame-matched">';
            html += '<span class="search-frame-marker glyphicon glyphicon-star" style="color:#d9534f;"></span> ';
            html += '<span class="search-matched">' + displayName + lineInfo + '</span>';
        } else {
            html += '<div class="search-frame-line">';
            html += '<span class="search-frame-marker glyphicon glyphicon-arrow-right" style="color:#999;"></span> ';
            html += '<span class="search-frame">' + displayName + lineInfo + '</span>';
        }
        html += '</div>';
    }
    html += '</div>';

    // Thread list (under the call path)
    html += '<div class="search-thread-list">';
    for (var t = 0; t < threads.length; t++) {
        var thread = threads[t];
        var stateClass = 'state-' + (thread.state || 'unknown').toLowerCase();
        html += '<div class="search-thread-item ' + stateClass + '">';
        if (showCheckbox) {
            html += '<input type="checkbox" class="thread-checkbox" data-thread-id="' + thread.id + '" data-thread-name="' + thread.name + '" data-thread-state="' + (thread.state || '') + '">';
        }
        html += '<span class="glyphicon glyphicon-user" style="margin-right:5px;"></span>';
        html += '<span class="thread-name">' + thread.name + '</span>';
        html += '<span style="margin-left:5px;">[' + thread.id + ']</span>';
        html += '<span style="margin-left:5px;">' + (thread.state || '') + '</span>';
        if (thread.cpuPct !== undefined && thread.cpuPct !== null) {
            var cpuColor = thread.cpuPct >= 50 ? '#d9534f' : (thread.cpuPct >= 10 ? '#f0ad4e' : '#5bc0de');
            html += '<span class="badge" style="margin-left:5px;background:' + cpuColor + ';">cpu ' + thread.cpuPct.toFixed(1) + '%</span>';
        }
        if (!isFrameMatch) {
            html += '<span class="glyphicon glyphicon-star" style="margin-left:5px;color:#d9534f;"></span>';
        }
        html += '</div>';
    }
    html += '</div>';

    html += '</div>';

    return html;
}

function togglePathGroup(header) {
    var frameList = header.nextElementSibling;
    var threadList = frameList ? frameList.nextElementSibling : null;
    var icon = header.querySelector('.search-toggle-icon');

    if (frameList && frameList.style.display === 'none') {
        frameList.style.display = 'block';
        if (threadList) threadList.style.display = 'block';
        icon.classList.remove('glyphicon-chevron-right');
        icon.classList.add('glyphicon-chevron-down');
    } else {
        if (frameList) frameList.style.display = 'none';
        if (threadList) threadList.style.display = 'none';
        icon.classList.remove('glyphicon-chevron-down');
        icon.classList.add('glyphicon-chevron-right');
    }
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

    var html = '';
    if (results.length === 0) {
        html = '<div class="text-muted" style="padding:20px;">No matches found</div>';
    } else {
        var pathGroups = buildPathGroups(results);
        for (var i = 0; i < pathGroups.length; i++) {
            html += buildPathGroupHtml(pathGroups[i], true);
        }
    }

    document.getElementById('treeContent').innerHTML = html;
    document.getElementById('threadCount').innerText = results.length;
    document.getElementById('frameCount').innerText = (pathGroups ? pathGroups.length : 0) + ' matched paths';
}

// ========== Thread Stack Sampler (live sampling + polling) ==========

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

    if (samplerPollInterval) {
        clearInterval(samplerPollInterval);
        samplerPollInterval = null;
    }

    var ids = '';
    for (var i = 0; i < threads.length; i++) {
        if (i > 0) ids += ',';
        ids += threads[i].id;
    }

    var startXhr = new XMLHttpRequest();
    startXhr.open('GET', '/thread/sampler/start.json?ids=' + ids, true);
    startXhr.onload = function() {
        if (startXhr.status === 200) {
            try {
                var startResp = JSON.parse(startXhr.responseText);
                if (startResp.status) {
                    currentSamplerId = startResp.msg; // sampler id returned in msg field
                    document.getElementById('threadStackBody').innerHTML = '<span class="text-muted">Starting sampler...</span>';
                    document.getElementById('samplerStats').innerText = '';
                    samplerThreshold = parseFloat(document.getElementById('thresholdInput').value) || 0;
                    maxDepth = parseInt(document.getElementById('depthInput').value) || 0;
                    cachedSamplerData = null;
                    $('#threadStackModal').modal('show');

                    refreshStacks();
                    samplerPollInterval = setInterval(pollSamplerStats, 5000);
                } else {
                    alert('Failed to start sampler: ' + startResp.msg);
                }
            } catch (e) {
                alert('Failed to start sampler');
            }
        } else {
            alert('Failed to start sampler: HTTP ' + startXhr.status);
        }
    };
    startXhr.onerror = function() { alert('Failed to start sampler: request error'); };
    startXhr.send();
}

function getTtl() {
    return parseInt(document.getElementById('ttlInput').value) || 10;
}

function pollSamplerStats() {
    if (!currentSamplerId) return;
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/thread/sampler/result.json?sid=' + currentSamplerId + '&ttl=' + getTtl(), true);
    xhr.onload = function() {
        if (xhr.status === 200) {
            try {
                var response = JSON.parse(xhr.responseText);
                if (response.status && response.data) {
                    var data = response.data;
                    cachedSamplerData = data;
                    var statsEl = document.getElementById('samplerStats');
                    if (statsEl) {
                        statsEl.innerText = data.sampleCount + ' samples' +
                            (data.running ? ' (sampling)' : ' (stopped)');
                    }
                } else if (!response.status) {
                    // sampler expired or not found — stop polling
                    clearInterval(samplerPollInterval);
                    samplerPollInterval = null;
                    currentSamplerId = null;
                }
            } catch (e) {}
        }
    };
    xhr.onerror = function() {};
    xhr.send();
}

function refreshStacks() {
    if (!currentSamplerId) return;
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/thread/sampler/result.json?sid=' + currentSamplerId + '&ttl=' + getTtl(), true);
    xhr.onload = function() {
        if (xhr.status === 200) {
            try {
                var response = JSON.parse(xhr.responseText);
                if (response.status && response.data) {
                    cachedSamplerData = response.data;
                    renderSamplerTree();
                } else {
                    document.getElementById('threadStackBody').innerHTML = '<span class="text-muted">Sampler expired. Please close and start a new one.</span>';
                    currentSamplerId = null;
                }
            } catch (e) {}
        }
    };
    xhr.onerror = function() {};
    xhr.send();
}

function renderSamplerTree() {
    if (!cachedSamplerData) return;
    var data = cachedSamplerData;
    var tree = data.tree;
    samplerThreshold = parseFloat(document.getElementById('thresholdInput').value) || 0;
    maxDepth = parseInt(document.getElementById('depthInput').value) || 0;

    var html = '';
    if (tree && tree.length > 0) {
        for (var i = 0; i < tree.length; i++) {
            html += buildFrameNode(tree[i], 0, false, tree[i].count, samplerThreshold);
        }
    } else {
        html = '<span class="text-muted">Waiting for samples...</span>';
    }

    document.getElementById('threadStackBody').innerHTML = html;

    // Auto-expand after rendering: expand up to maxDepth, or all if maxDepth=0
    expandUpToDepth();
    // After expand, set modal button state to "Collapse All"
    modalExpanded = true;
    document.getElementById('toggleModalExpandBtn').innerText = 'Collapse';
    document.getElementById('toggleModalExpandBtn').className = 'btn btn-default btn-sm';

    var statsEl = document.getElementById('samplerStats');
    if (statsEl) {
        statsEl.innerText = data.sampleCount + ' samples' +
            (data.running ? ' (sampling)' : ' (stopped)');
    }
}

function stopSampler() {
    if (!currentSamplerId) return;
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/thread/sampler/stop.json?sid=' + currentSamplerId, true);
    xhr.send();
    currentSamplerId = null;
}

// ========== Event listeners ==========

document.getElementById('toggleExpandBtn').addEventListener('click', togglePageExpand);
document.getElementById('toggleThreadsBtn').addEventListener('click', togglePageThreads);
document.getElementById('selectAllBtn').addEventListener('click', toggleSelectAll);
document.getElementById('viewStackBtn').addEventListener('click', viewSelectedStacks);
document.getElementById('refreshStackBtn').addEventListener('click', refreshStacks);
document.getElementById('toggleModalExpandBtn').addEventListener('click', toggleModalExpand);
document.getElementById('toggleModalThreadsBtn').addEventListener('click', toggleModalThreads);

document.getElementById('refreshTreeBtn').addEventListener('click', loadTreeData);

document.getElementById('searchInput').addEventListener('input', function() {
    filterTree(this.value);
});

// Threshold input: re-render tree from cached data when threshold changes
document.getElementById('thresholdInput').addEventListener('input', function() {
    renderSamplerTree();
});

// Depth input: re-render tree from cached data when depth changes
document.getElementById('depthInput').addEventListener('input', function() {
    renderSamplerTree();
});

$('#threadStackModal').on('hidden.bs.modal', function() {
    if (samplerPollInterval) {
        clearInterval(samplerPollInterval);
        samplerPollInterval = null;
    }
    stopSampler();
    cachedSamplerData = null;
    currentSamplerId = null;
});

// Load tree data
function loadTreeData() {
    document.getElementById('treeContent').innerHTML = '<span class="text-muted">Loading...</span>';
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/thread/tree.json', true);
    xhr.onload = function() {
        if (xhr.status === 200) {
            var response = JSON.parse(xhr.responseText);
            if (response.status) {
                buildTree(response.data);
                // Reset expand state after reload (tree starts collapsed)
                pageExpanded = false;
                document.getElementById('toggleExpandBtn').innerText = 'Expand';
                document.getElementById('toggleExpandBtn').className = 'btn btn-primary btn-sm';
                // Re-apply search filter if there's text in the search input
                var searchValue = document.getElementById('searchInput').value;
                if (searchValue && searchValue.length > 0) {
                    filterTree(searchValue);
                }
            } else {
                document.getElementById('treeContent').innerHTML = '<span class="text-danger">Error: ' + response.msg + '</span>';
            }
        }
    };
    xhr.onerror = function() {
        document.getElementById('treeContent').innerHTML = '<span class="text-danger">Failed to load tree data</span>';
    };
    xhr.send();
}

loadTreeData();
