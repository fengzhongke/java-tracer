
var data = null;
$('#index_refresh').click(function(){
    $.post("/index/get", function(str){
        var ret = JSON.parse(str);
        if(ret.status){
            data = ret.data;
            if(data.mode > 0){
                $('#form_set').hide();
            }else {
                if (data.metaVO.hasOwnProperty('cname') && data.metaVO.hasOwnProperty('mname')) {
                    $('label[name=cname]', $('#form_set')).html(data.metaVO.cname);
                    $('label[name=mname]', $('#form_set')).html(data.metaVO.mname);
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
                $('[name=cname_mname]', tr).html(item.metaVO.cname + "." + item.metaVO.mname);
                $('[name=rt]', tr).html(item.rt);
                demo.after(tr);
            }
        }
    });
});
$('#index_set').click(function(){
    if(data){
        $('input[name=model_cname]').val(data.metaVO.cname);
        $('input[name=model_mname]').val(data.metaVO.mname);
        $('select[name=model_type]').val(data.type);
        $('input[name=model_size]').val(data.size);
    }
});

$('#index_confirm').click(function(){
    var cname = $('input[name=model_cname]').val();
    var mname = $('input[name=model_mname]').val();
    var type = $('input[name=model_type]').val();
    var size = $('input[name=model_size]').val();
    if(confirm("set class:[" + cname + "]method:[" + mname + "] type:[" + type + "] size:[" + size + "] ? ")){
        $.post("/index/set", {class:cname, method:mname, type:type, size:size}, function(str){
            var ret = JSON.parse(str);
            if(ret.status){
                alert("set sucess!");
                location.reload();
            }else{
                alert("set failed :[" + ret.msg + "]");
            }
        });
    }
});



$('#class_refresh').click(function(){
    $.post("/class/get", function(str){
        var ret = JSON.parse(str);
        if(ret.status){
            data = ret.data;
            if(data.mode > 0){
                $('#form_set').hide();
            }else {
                if (data.hasOwnProperty('include') && data.hasOwnProperty('exclude')) {
                    $('label[name=include]', $('#form_set')).html(data.include);
                    $('label[name=exclude]', $('#form_set')).html(data.exclude);
                }
            }
        }
    });
});

$('#class_set').click(function(){
    if(data){
        $('input[name=model_include]').val(data.include);
        $('input[name=model_exclude]').val(data.exclude);
    }
});
$('#class_confirm').click(function(){
    var include = $('input[name=model_include]').val();
    var exclude = $('input[name=model_exclude]').val();
    if(confirm("set include:[" + include + "]exclude:[" + exclude + "] and refresh ? ")){
        $.post("/class/set", {include:include, exclude:exclude}, function(str){
            var ret = JSON.parse(str);
            if(ret.status){
                alert("set sucess!");
                location.reload();
            }else{
                alert("set failed :[" + ret.msg + "]");
            }
        });
    }
});

$('button[name=auto_refresh]').click();

