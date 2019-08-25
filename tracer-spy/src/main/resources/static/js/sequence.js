function DynSeq(node, map){
    this.node = node;
    this.map = map;
    this.set = [];
    this.getName = function(str){
        return str.substr(str.lastIndexOf('.')+1);
    };
    this.setActor = function(cname, name){
        if(this.set.indexOf(name) == -1){
            this.set.push(name);
            return "Participant " + name + " [cname='" + cname + "',fillcolor='#fed']";
        }
    };
    this.getNode = function(path, nn){
        var idx = 0;
        while((idx = path.indexOf('/')) == 0){
            path = path.substr(idx+1);
        }
        var n = nn ? nn : node;
        var p = path;
        if(idx != -1){
            p = path.substr(0, idx);
        }
        for(var i in n.s){
            if(n.s[i].i == p){
                return idx != -1 ? this.getNode(path.substr(idx+1), n.s[i]) : n.s[i];
            }
        }
        if(idx != -1 && n.i == p){
            return this.getNode(path.substr(idx+1), n);
        }
        return n;
    };
    this.showLines = function(path, depth){
        return this.getLines1(this.getNode(path), path, depth, 0);
    };
    this.getLines1 = function(n, path, depth, count, invoke){
        if(depth > 0 && n.hasOwnProperty('s')){
            var lines = [];
            var src = map[n.i];
            if(count == 0){
                this.set = [];
                lines.push("Title:" + src[0] + "." + src[1] + "\\ncnt:" + n.c + ":rt:" + n.t + " [fillcolor='#fef']");
            }
            var srcName = this.getName(src[0]);
            var srcActor = this.setActor(src[0], srcName);
            if(srcActor){
                lines.push(srcActor);
            }
            for(var i in n.s){
                var son = n.s[i];
                var dst = map[son.i];
                var dstName = this.getName(dst[0]);
                var dstActor = this.setActor(dst[0], dstName);
                if(dstActor){
                    lines.push(dstActor);
                }
                var nextInvoke = invoke ? (invoke + "." + (parseInt(i)+1)) : ("" + (parseInt(i)+1));
                var subLines = this.getLines1(son, path + "/" + son.i, depth-1, count+1, nextInvoke);

                var id = "id='" + son.i + "'";
                var pa = "path='" + path + "/" + son.i + "'";

                var cnt = son.c > 1 ? ":cnt:" + son.c : "";
                var rt = son.t > 1 ? ":rt:" + son.t : "";

                if(subLines){
                    lines.push(srcName + "->" + dstName + ":" + nextInvoke + "." + dst[1] + cnt + " [" + id + "," + pa + ", fontcolor='#0af']");
                    lines = lines.concat(subLines);
                    lines.push(dstName + "-->" + srcName + ":" + nextInvoke + "."  + dst[1]  + rt + " [fontcolor='#0af']");
                }else if(son.hasOwnProperty('s')){
                    lines.push(srcName + "->" + dstName + ":" + nextInvoke + "." + dst[1] + cnt + rt + " [" + id + "," + pa + ",fontcolor='#a0f']");
                }else{
                    lines.push(srcName + "->" + dstName + ":" + nextInvoke + "." + dst[1] + cnt + rt + " [" + id + "," + pa + "]");
                }
            }
            return lines;
        }
    }
}

var getDepth = function(){
    var val = parseInt($('.sequence-op[name=depth-value]').val());
    return isNaN(val) ? 2 : val;
}
var showChart = function(path){
    if(seq){
        var lines  = seq.showLines(path, getDepth());
        if(lines){
            console.log(lines.join('\r\n'));
            $('#sequence').html('');
            Diagram.parse(lines.join('\r\n')).drawSVG("sequence", {theme: 'simple'});
        }
    }
}

var seq = null;
var stack = [];
var init = function(id){
    $.post("/trace/get.json", {id:id}, function(str){
        var ret = JSON.parse(str);
        if(ret.status){
            seq = new DynSeq(ret.data.node, ret.data.metas);
            stack.push('/1');
            showChart('/1', 2);
        }
    });
}
$("[data-toggle='tooltip']").tooltip();
$('.sequence-op').click(function(){
    var name = $(this).attr("name");
    if(name == "depth-add"){
        $('.sequence-op[name=depth-value]').val(getDepth()+1);
    }else if(name == "depth-sub"){
        $('.sequence-op[name=depth-value]').val(getDepth()-1);
    }else if(name == "fast-back"){
        stack.splice(1, stack.length-1);
    }else if(name == "step-back"){
        if(stack.length > 1){
            stack.pop();
        }
    }else if(name == "depth-value"){
        return;
    }
    showChart(stack[stack.length-1]);
});
$('body').on('click', 'svg text', function(){
    var dataStr = $(this).prop('data');
    if(dataStr){
        var data = JSON.parse(dataStr);
        if(data.hasOwnProperty('path')){
            if(data.path != stack[stack.length-1]){
                stack.push(data.path);
                showChart(data.path);
            }
        }
    }
});