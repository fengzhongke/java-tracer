var data = null;
var setCname = '';
var setMname = '';

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
    // Show class search area, hide method list
    $('#setClassSearchArea').show();
    $('#setMethodListArea').hide();
    $('#setClassSearchInput').val('');
    $('#setClassList').html('');
    // Pre-fill size/type if data exists
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
    // Load declared methods for this class
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
    // Auto-submit
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

$('button[name=auto_refresh]').click();