
var data = null;
$('#refresh').click(function(){
    $.post("/trace/getSet", function(str){
        var ret = JSON.parse(str);
        if(ret.status){
            data = ret.data;
            $('label[name=size]').html(data.size);
            if(data.metaVO.hasOwnProperty('cname') && data.metaVO.hasOwnProperty('mname')){
                $('label[name=cname_mname]', $('#form_set')).html(data.metaVO.cname + "." + data.metaVO.mname);
            }
        }
    });
    $.post("/trace/list", function(str){
        var ret = JSON.parse(str);
        if(ret.status){
            $('.list-group-item', $('#form_list')).remove();
            for(var i in ret.data){
                var item = ret.data[i];
                var demo = $('.list-demo', $('#form_list'));
                var li = demo.clone();
                li.removeClass('list-demo');
                li.addClass('list-group-item');
                $('#form_href', li).attr('href', '/trace?id=' + item.seed);
                $('label[name=cname_mname]', li).html(item.metaVO.cname + "." + item.metaVO.mname);
                demo.after(li);
            }
        }
    });
});
$('#refresh').click();
$('#set').click(function(){
    if(data){
        $('input[name=model_cname]').val(data.metaVO.cname);
        $('input[name=model_mname]').val(data.metaVO.mname);
        $('input[name=model_size]').val(data.size);
    }
});

$('#confirm').click(function(){
    var cname = $('input[name=model_cname]').val();
    var mname = $('input[name=model_mname]').val();
    var size = $('input[name=model_size]').val();
    if(confirm("set class:[" + cname + "]method:[" + mname + "]size:[" + size + "] ? ")){
        $.post("/trace/set", {class:cname, method:mname, size:size}, function(str){
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

