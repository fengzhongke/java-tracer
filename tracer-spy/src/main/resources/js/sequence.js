function TreeNode(name, arr, meta){
    if(arr.length > 0){
        meta.arr[name] = arr;
    }
    this.name = name;
    this.arr = arr;
    this.getSon = function(n){
        n = n ? n : this;
        n.son = [];
        var m = {};
        var idx = n.name.length+1;
        for(var i in n.arr){
            var s = n.arr[i];
            var idxx = s.indexOf('.', idx);
            var f = s.charAt(idx);
            if(f>='a' && f<='z' && idxx > 0){
                var pname = s.substr(0, idxx);
                if(!m.hasOwnProperty(pname)){
                    m[pname] = [];
                }
                m[pname].push(s);
            }else {
                m[s] = [s];
            }
        }
        for(var i in m){
            var s = m[i];
            if(s.length == 1){
                n.son.push(new TreeNode(s[0], [], meta));
            }else{
                var ns = new TreeNode(i, s, meta);
                if(ns.hasOwnProperty('son') && $.isArray(ns.son) && ns.son.length == 1){
                    n.son.push(ns.son[0]);
                }else{
                    n.son.push(ns);
                }
            }
        }
        return n.son;
    }
    this.son = this.getSon();
    this.getTree = function(n){
        n = n ? n : this;
        var ul = null;
        for(var i in n.son){
            var s = n.son[i];
            if(s.hasOwnProperty('son') && s.son.length > 0){
                if(!ul){
                    ul = $("<ul>");
                    ul.addClass("tree-list-group");
                }
                var li = $("<li>");
                li.append("<input type='checkbox' value='" + s.name + "'>" + s.name);
                li.append(this.getTree(s));
                li.addClass("tree-list-item");
                ul.append(li);
            }
        }
        return ul;
    }
}

function Meta(node, metas){
    this.node = node;
    this.metas = metas;
    this.arr = {};
    this.getCmeta = function(){
        var cmeta = {};
        for(var id in this.metas){
            var meta = this.metas[id];
            if(!cmeta.hasOwnProperty(meta[0])){
                cmeta[meta[0]] = [];
            }
            cmeta[meta[0]].push(parseInt(id));
        }
        return cmeta;
    }
    this.cmeta = this.getCmeta();
    this.getWithout = function(wnames){
        var m = {};
        for(var i in wnames){
            if(this.arr.hasOwnProperty(wnames[i])){
                var cnames = this.arr[wnames[i]];
                for(var j in cnames){
                    var ids = this.cmeta[cnames[j]];
                    for(var i in ids){
                        m[ids[i]]=ids[i];
                    }
                }
            }else if(this.cmeta.hasOwnProperty(wnames[i])){
                var ids = this.cmeta[wnames[i]];
                for(var i in ids){
                    m[ids[i]]=ids[i];
                }
            }
        }
        return m;
    }
}

function DynSeq(parent, node, meta){
    this.parent = parent;
    this.node = node;
    this.meta = meta;
    this.set = {};
    this.classes = {};
    this.cnames = {};
    this.getName = function(str){
        var len = str.length;
        var idx = 0;
        while(idx++<len){
            var c = str.charAt(idx);
            if(c>='A' && c<='Z'){
                break;
            }
        }
        return str.substr(idx);
        //return str.substr(str.lastIndexOf('.')+1);
    };
    this.setActor = function(cname, name){
        if(!this.set.hasOwnProperty(name)){
            this.set[name] = cname;
            this.classes[name] = cname;
            return "Participant " + name + " [cname='" + cname + "',fillcolor='#fed', type='actor']";
        }
    };
    this.addCname = function(cname){
        this.cnames[cname] = cname;
    }
    this.delCname = function(cname){
        delete this.cnames[cname]
    }
    this.getWithout = function(){
        return this.meta.getWithout(Object.keys(this.cnames));
    }
    this.filter = function(without, depth, n){
        if(depth > 0 && n.hasOwnProperty('s')){
            var m = {};
            var ss = [];
            for(var i in n.s){
                var s = n.s[i];
                if(without.hasOwnProperty(s.i)){
                    s = this.filter(without, depth, s);
                    if(s.hasOwnProperty('s')){
                        for(var j in s.s){
                            var g = s.s[j];
                            if(m.hasOwnProperty(g.i)){
                                m[g.i].t += g.t;
                                m[g.i].c += g.c;
                            }else{
                                m[g.i] = g;
                                ss.push(g);
                            }
                        }
                    }
                }else{
                    if(m.hasOwnProperty(s.i)){
                        m[s.i].t += s.t;
                        m[s.i].c += s.c;
                    }else{
                        m[s.i] = s;
                        ss.push(s);
                    }
                }
            }
            for(var i in ss){
                this.filter(without, depth-1, ss[i]);
            }
            n.s = ss;
        }
        return n;
    }
    this.getNode = function(path, n){
        n = n ? n : node;
        var idx = 0;
        while((idx = path.indexOf('/')) == 0){
            path = path.substr(idx+1);
        }
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
    this.getSon = function(path){
        var son = new DynSeq(this, this.getNode(path, this.filter(this.getWithout(), path.split('/').length-1, this.node)), this.meta);
        son.cnames = $.extend(true, {}, this.cnames);
        return son;
    }
    this.showLines = function(depth){
        return this.getLines(this.filter(this.getWithout(), depth, $.extend(true, {}, this.node)), "", depth, 0);
    };
    this.getLines = function(n, path, depth, count, invoke){
        if(depth > 0 && n.hasOwnProperty('s')){
            var lines = [];
            var src = this.meta.metas[n.i];
            if(count == 0){
                this.set = {};
                lines.push("Title:" + src[0] + "." + src[1] + "\\ncnt:" + n.c + ":rt:" + n.t + " [fillcolor='#fef']");
            }
            var srcName = this.getName(src[0]);
            var srcActor = this.setActor(src[0], srcName);
            if(srcActor){
                lines.push(srcActor);
            }
            for(var i in n.s){
                var son = n.s[i];
                var dst = this.meta.metas[son.i];
                var dstName = this.getName(dst[0]);
                var dstActor = this.setActor(dst[0], dstName);
                if(dstActor){
                    lines.push(dstActor);
                }
                var nextInvoke = invoke ? (invoke + "." + (parseInt(i)+1)) : ("" + (parseInt(i)+1));
                var subLines = this.getLines(son, path + "/" + son.i, depth-1, count+1, nextInvoke);

                var id = "id='" + son.i + "'";
                var pa = "path='" + path + "/" + son.i + "'";

                var cnt = son.c > 1 ? ":cnt:" + son.c : "";
                var rt = son.t > 1 ? ":rt:" + son.t : "";

                if(subLines){
                    lines.push(srcName + "->" + dstName + ":" + nextInvoke + "." + dst[1] + cnt + " [" + id + "," + pa + ", fontcolor='#0af', type='in']");
                    lines = lines.concat(subLines);
                    lines.push(dstName + "-->" + srcName + ":" + nextInvoke + "."  + dst[1]  + rt + " [fontcolor='#0af', type='out']");
                }else if(son.hasOwnProperty('s')){
                    lines.push(srcName + "->" + dstName + ":" + nextInvoke + "." + dst[1] + cnt + rt + " [" + id + "," + pa + ",fontcolor='#a0f', type='in']");
                }else{
                    lines.push(srcName + "->" + dstName + ":" + nextInvoke + "." + dst[1] + cnt + rt + " [" + id + "," + pa + ", type='all']");
                }
            }
            return lines;
        }
    }
    this.getList = function(){
        ul = $("<ul>");
        ul.addClass("tree-list-group");
        for(var i in this.classes){
            var s = this.classes[i];
            var li = $("<li>");
            if(this.cnames.hasOwnProperty(s)){
                li.append("<input type='checkbox' value='" + s + "' checked>" + i);
            }else if(this.set.hasOwnProperty(i)){
                li.append("<input type='checkbox' value='" + s + "'>" + i);
            }
            li.addClass("tree-list-item");
            ul.append(li);
        }
        return ul;
    }
}

function Chart(node, metas){
    this.meta = new Meta(node, metas);
    this.seq = new DynSeq(null, node, this.meta);
    this.getSetDepth = function(cnt){
        var val = parseInt($('.sequence-op[name=depth-value]').val());
        if(isNaN(val)){
            val = 2;
        }
        if(cnt){
            val += cnt;
            val = val<1 ? 1 : val;
            $('.sequence-op[name=depth-value]').val(val);
            this.showChart();
        }
        return val;
    }
    this.showChart = function(){
        var lines  = this.seq.showLines(this.getSetDepth());
        if(lines){
            $('#sequence').empty();
            Diagram.parse(lines.join('\r\n')).drawSVG("sequence", {theme: 'simple'});
            this.setClassModel();
        }
    }
    this.back = function(cnt){
        cnt = cnt ? cnt : 10000;
        var parent = null;
        while(cnt-->0 && (parent=this.seq.parent)){
            this.seq = parent;
        }
        this.showChart();
    }
    this.changeCname = function(cname, type){
        if(type == 1){
            this.seq.addCname(cname);
        }else{
            this.seq.delCname(cname);
        }
        this.showChart();
    }
    this.enterSon = function(path){
        this.seq = this.seq.getSon(path);
        this.showChart();
    }
    this.setPackageModel = function(){
        $('#packages').append(new TreeNode("", Object.keys(this.meta.cmeta), this.meta).getTree());
    }
    this.setClassModel = function(){
        $('#classes').empty();
        $('#classes').append(this.seq.getList());
    }
    this.setPackageModel();
    this.showChart();
}

var chart = null;
var init = function(id){
    $.post("/trace/get.json", {id:id}, function(str){
        var ret = JSON.parse(str);
        if(ret.status){
            chart = new Chart(ret.data.node, ret.data.metas)
        }
    });
}

$("[data-toggle='tooltip']").tooltip();
$('.sequence-op').click(function(){
    var name = $(this).attr("name");
    if(name == "depth-add"){
        chart.getSetDepth(1);
    }else if(name == "depth-sub"){
        chart.getSetDepth(-1);
    }else if(name == "fast-back"){
        chart.back();
    }else if(name == "step-back"){
        chart.back(1);
    }
});

$('body').on('click', 'svg text', function(){
    var dataStr = $(this).prop('data');
    if(dataStr){
        var data = JSON.parse(dataStr);
        if(data.hasOwnProperty('type')){
            if(data.type == 'in'){
                chart.enterSon(data.path);
            }else if(data.type == 'actor'){
                if(confirm("delete[" + data.cname + "]?")){
                    chart.changeCname(data.cname, 1);
                }
            }
        }
    }
});
$('body').on('change', '[type=checkbox]', function(){
    var cname = $(this).val();
    if($(this).is(":checked")){
        $(this).next().hide();
        chart.changeCname(cname, 1);
    }else{
        $(this).next().show();
        chart.changeCname(cname, -1);
    }
});
