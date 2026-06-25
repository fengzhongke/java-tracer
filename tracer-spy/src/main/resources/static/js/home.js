// ====== Index: trace config + trace list ======
var data = null;
var setCname = '';
var setMname = '';

// Load trace config and trace list on refresh
$('#index_refresh').click(function(){
    $.post("/index/get", function(str){
        var ret = JSON.parse(str);
        if(ret.status){
            data = ret.data;
            if(data.mode > 0){
                $('#form_set').hide();
            }else {
                if (data.metaVO.hasOwnProperty('cname') && data.metaVO.hasOwnProperty('mname')) {
                    $('label[name=method]', $('#form_set')).html(data.metaVO.cname + '#' + data.metaVO.mname);
                    $('label[name=type]', $('#form_set')).html(data.type);
                }
                $('label[name=size]', $('#form_set')).html(data.size);
            }
        }
    });
    $.post("/index/list", function(str){
        var ret = JSON.parse(str);
        if(ret.status){
            $('.list-show-item', $('#form_list')).remove();
            for(var i in ret.data){
                var item = ret.data[i];
                var demo = $('.list-demo', $('#form_list'));
                var tr = demo.clone();
                tr.removeClass('list-demo');
                tr.addClass('list-show-item');
                $('#form_href', tr).attr('href', '/trace?id=' + item.seed);
                $('[name=type]', tr).html(item.type);
                $('[name=time]', tr).html(item.time);
                $('[name=cname_mname]', tr).html(item.metaVO.cname + "#" + item.metaVO.mname);
                $('[name=rt]', tr).html(item.rt);
                demo.after(tr);
            }
        }
    });
});

// ====== Set modal: class search + method selection ======

$('#index_set').click(function(){
    setCname = '';
    setMname = '';
    $('#setClassSearchArea').show();
    $('#setMethodListArea').hide();
    $('#setClassSearchInput').val('');
    $('#setClassList').html('');
    if(data){
        $('#setModelSize').val(data.size || '100');
    }
    $('#setModal').modal('show');
});

$('#setClassSearchBtn').click(function(){
    var prefix = $('#setClassSearchInput').val().trim();
    if(!prefix){ alert('Type a class name prefix'); return; }
    $.post('/invoke/classes.json', {prefix: prefix}, function(str){
        var ret = JSON.parse(str);
        if(ret.status && ret.data && ret.data.length > 0){
            var html = '';
            for(var i = 0; i < ret.data.length; i++){
                var cn = ret.data[i].name;
                var lid = ret.data[i].loaderId || 0;
                html += '<div class="class-item" onclick="setSelectClass(\'' + cn.replace(/'/g, "\\'") + '\',' + lid + ')">';
                html += '<span style="font-weight:bold;">' + cn + '</span>';
                if(lid > 0) html += ' <span style="color:#d9534f;font-size:11px;">[loader#' + lid + ']</span>';
                html += '</div>';
            }
            $('#setClassList').html(html);
        }else{
            $('#setClassList').html('<div style="color:#999;padding:8px;">No classes found</div>');
        }
    });
});

$('#setClassSearchInput').on('keypress', function(e){
    if(e.which === 13){ e.preventDefault(); $('#setClassSearchBtn').click(); }
});

function setSelectClass(className, loaderId){
    setCname = className;
    $('#setSelectedClassName').text(className);
    $.post('/invoke/members.json', {className: className}, function(str){
        var ret = JSON.parse(str);
        if(ret.status && ret.data){
            var members = ret.data;
            var html = '';
            var methods = [];
            var fields = [];
            for(var i = 0; i < members.length; i++){
                if(members[i].isField) fields.push(members[i]);
                else methods.push(members[i]);
            }

            if(methods.length > 0){
                html += '<div class="member-section-header">Methods (' + methods.length + ')</div>';
                for(var i = 0; i < methods.length; i++){
                    var m = methods[i];
                    html += '<div class="method-item" onclick="setSelectMethod(\'' + m.name.replace(/'/g, "\\'") + '\')">';
                    html += '<span class="method-name">' + m.name;
                    if(m.paramTypes && m.paramTypes.length > 0){
                        html += '(' + m.paramTypes.join(', ') + ')';
                    }else{
                        html += '()';
                    }
                    html += '</span>';
                    if(m.isStatic) html += ' <span class="badge-static">static</span>';
                    if(m.declaringClass) html += ' <span class="badge-inherited">from ' + m.declaringClass + '</span>';
                    html += '<span class="method-sig"> → ' + m.returnType + '</span>';
                    html += '</div>';
                }
            }

            if(fields.length > 0){
                html += '<div class="member-section-header">Fields (' + fields.length + ')</div>';
                for(var i = 0; i < fields.length; i++){
                    var f = fields[i];
                    html += '<div class="method-item" onclick="setSelectMethod(\'' + f.name.replace(/'/g, "\\'") + '\')">';
                    html += '<span class="method-name">' + f.name + '</span>';
                    if(f.isStatic) html += ' <span class="badge-static">static</span>';
                    if(f.declaringClass) html += ' <span class="badge-inherited">from ' + f.declaringClass + '</span>';
                    html += '<span class="method-sig"> → ' + f.returnType + '</span>';
                    html += '</div>';
                }
            }

            $('#setMethodList').html(html);
            $('#setClassSearchArea').hide();
            $('#setMethodListArea').show();
        }else{
            $('#setMethodList').html('<div style="color:#999;padding:8px;">No members found</div>');
            $('#setClassSearchArea').hide();
            $('#setMethodListArea').show();
        }
    });
}

function setSelectMethod(methodName){
    setMname = methodName;
    $('#setModal').modal('hide');
    var type = $('#setModelType').val();
    var size = $('#setModelSize').val();
    if(confirm("set method:[" + setCname + "#" + setMname + "] type:[" + type + "] size:[" + size + "] ? ")){
        $.post("/index/set", {class:setCname, method:setMname, type:type, size:size}, function(str){
            var ret = JSON.parse(str);
            if(ret.status){
                alert("set success!");
                location.reload();
            }else{
                alert("set failed :[" + ret.msg + "]");
            }
        });
    }
}

$('#setChangeClassBtn').click(function(){
    setCname = '';
    setMname = '';
    $('#setClassSearchArea').show();
    $('#setMethodListArea').hide();
    $('#setClassSearchInput').val('');
    $('#setClassList').html('');
});

// ====== Class Tree ======
var treeData = null;
var totalClasses = 0;
var includePrefixes = [];
var excludePrefixes = [];
var currentConfigPath = '';
var progressTimer = null;
var treeExpanded = false;

function loadConfig() {
    $.get('/class/tree/config', function(str) {
        var ret = JSON.parse(str);
        if (ret.status) {
            var d = ret.data;
            document.getElementById('includeLabel').innerText = d.include || '';
            document.getElementById('excludeLabel').innerText = d.exclude || '';
            includePrefixes = d.includePrefixes || [];
            excludePrefixes = d.excludePrefixes || [];
        }
    });
}

function loadTree() {
    $.get('/class/tree.json', function(str) {
        var ret = JSON.parse(str);
        if (ret.status) {
            buildTree(ret.data);
        } else {
            document.getElementById('treeContent').innerHTML = '<span class="text-danger">Error: ' + ret.msg + '</span>';
        }
    });
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

        if (loader.children && loader.children.length > 0) {
            html += buildPackageTree(loader.children, 0);
        }

        html += '</div>';
        html += '</div>';
    }

    document.getElementById('treeContent').innerHTML = html;
    document.getElementById('loaderCount').innerText = data.length;
    document.getElementById('classCount').innerText = totalClasses;
    // Reset toggle state since tree is rebuilt collapsed
    treeExpanded = false;
    updateToggleBtn();
}

function buildPackageTree(children, level) {
    var html = '';
    for (var i = 0; i < children.length; i++) {
        var node = children[i];
        var indent = level * 10;

        if (node.isClass) {
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
            var wovenCount = node.wovenCount || 0;
            var totalCount = node.totalCount || 0;

            html += '<div class="tree-node package-node">';
            html += '<div class="tree-item tree-package" onclick="toggleNode(this)" style="margin-left:' + indent + 'px;">';
            html += '<span class="tree-icon glyphicon glyphicon-folder-close"></span>';
            html += '<span class="node-name">' + node.name + '</span>';
            html += '<span class="badge" style="margin-left:10px;">' + wovenCount + '/' + totalCount + '</span>';
            html += '<button class="btn btn-xs btn-default config-btn" data-path="' + node.fullPath + '" data-is-class="false" onclick="openConfigModal(this)">';
            html += '<span class="glyphicon glyphicon-cog"></span>';
            html += '</button>';
            html += '</div>';
            html += '<div class="tree-children">';

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
    if (matchedExclude) {
        statusEl.innerHTML = '<span class="config-status-excluded">Currently excluded by prefix: ' + matchedExclude + '</span>';
    } else if (matchedInclude) {
        statusEl.innerHTML = '<span class="config-status-included">Currently included by prefix: ' + matchedInclude + '</span>';
    } else {
        statusEl.innerHTML = '<span class="config-status-default">Not matched by any prefix (default excluded)</span>';
    }

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
                    var bar = document.getElementById('progressBar');
                    bar.classList.remove('active');
                    bar.classList.remove('progress-bar-striped');
                    bar.classList.add('progress-bar-success');
                    loadConfig();
                    loadTree();
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

function updateToggleBtn() {
    var btn = document.getElementById('toggleExpandBtn');
    if (treeExpanded) {
        btn.innerHTML = '<span class="glyphicon glyphicon-folder-close"></span> Collapse All';
        btn.className = 'btn btn-default btn-sm';
    } else {
        btn.innerHTML = '<span class="glyphicon glyphicon-folder-open"></span> Expand All';
        btn.className = 'btn btn-primary btn-sm';
    }
}

document.getElementById('toggleExpandBtn').addEventListener('click', function() {
    if (treeExpanded) {
        collapseAll();
        treeExpanded = false;
    } else {
        expandAll();
        treeExpanded = true;
    }
    updateToggleBtn();
});

function filterTree(keyword) {
    keyword = keyword.toLowerCase();
    var allNodes = document.querySelectorAll('.tree-node');

    for (var i = 0; i < allNodes.length; i++) {
        var node = allNodes[i];
        var nodeMatch = false;
        var hasClassMatch = false;

        var nodeNameEl = node.querySelector('.node-name');
        if (nodeNameEl) {
            var nodeName = nodeNameEl.innerText.toLowerCase();
            nodeMatch = nodeName.indexOf(keyword) >= 0;
        }

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

        if (nodeMatch || hasClassMatch) {
            node.style.display = '';
            if (hasClassMatch && keyword.length > 0) {
                expandToNode(node);
            }
        } else {
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

document.getElementById('searchInput').addEventListener('input', function() {
    filterTree(this.value);
});

// Initial load: refresh trace data, then load class config + tree
$('button[name=auto_refresh]').click();
loadConfig();
loadTree();