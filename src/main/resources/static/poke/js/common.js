
$(function(){

    //折叠窗口
    function collaseWin() {
        $('.collapse_win dl dt').click(function () {
            var op = $(this).parent('dl');
            if (op.hasClass('active')) {
                $(this).siblings('dd').slideDown(100);
                op.removeClass('active');

            } else {
                $(this).siblings('dd').slideUp(100);
                op.addClass('active');
            }
        })
    }

    collaseWin();

     //聊天窗口
 function val(){

    $('.input_btn').click(sub)

    $('.input_text').keydown(function (evt) {
        var keycode = evt.which;//获取键值
        if (keycode == 13)//回车
        {
            sub();
        }

    })

    function sub() {
        if ($('.input_text').val() != '') {
            $.ajax({
                type: "post",
                url: "xxxx",
                data: {'chatText': $('.input_text').val()},
                dataType: "json",
                success: function (data) {
                   $('<li>'+data.xxx+'</li>').appendTo($('.chat_con ol'))
                }
            })
        } else {

            layer.tips('请输入聊天内容！', '.input_area');

        }
    }

 }

 val();

//发送请求获取多少张牌及其属性，返回HTML

    $.ajax({
        type: "post",
        url: "xxxx",
        dataType: "html",
        success: function (data) {
           /* XXX.appendTo($('.mypoke'))*/
           /* setAttr();*/  //获取后设置属性
        }
    })


setAttr(); //模拟获取后设置属性


 //设置牌间距

    function pokeMargin(obj){

        var len=$(obj).length
        for(var i=0;i<len;i++){
            $(obj).eq(i).attr('index',i);
            $(obj).eq(i).css({'margin-left':i*20,'z-index':i+1});

        }

    }


function setAttr(){

    pokeMargin('.mypoke a') //设置间距

//获取数据设置扑克的属性
    $('.mypoke a').each(function(){

var dataAttr=$.parseJSON("{"+$(this).attr('data-options')+"}")
        //设置扑克形状
        switch (dataAttr.poke_shape) {
            case 'Spade':
                $(this).find('.cm').html('&spades;')
                break;
            case 'Heart':
                $(this).find('.cm').html('&hearts;')
                break;
            case 'Club':
                $(this).find('.cm').html('&clubs;')
                break;
            case 'Diamond':
                $(this).find('.cm').html('&hearts;')
                break;
        }

        //设置扑克花色
        switch (dataAttr.poke_color) {
            case 'black':
                $(this).css({'color': 'black'});
                break;
            case 'red':
                $(this).css({'color': 'red'});
                break;
        }

        //设置扑克值
        switch (dataAttr.poke_value) {
            case 'A':
                $(this).find('.pv').text('A')
                break;
            case 'K':
                $(this).find('.pv').text('K')
                break;
            case 'Q':
                $(this).find('.pv').text('Q')
                break;
            case 'J':
                $(this).find('.pv').text('J')
                break;
            case '10':
                $(this).find('.pv').text('10')
                break;
            case '9':
                $(this).find('.pv').text('9')
                break;
            case '8':
                $(this).find('.pv').text('8')
                break;
            case '7':
                $(this).find('.pv').text('7')
                break;
            case '6':
                $(this).find('.pv').text('6')
                break;
            case '5':
                $(this).find('.pv').text('5')
                break;
            case '4':
                $(this).find('.pv').text('4')
                break;
            case '3':
                $(this).find('.pv').text('3')
                break;
            case '2':
                $(this).find('.pv').text('2')
                break;

        }

        //设置点击选取状态

        $(this).click(function () {
            if ($(this).hasClass('selected')) {
                $(this).css('marginTop', '0px');
                $(this).removeClass('selected');
            } else {
                $(this).css('marginTop', '-10px');
                $(this).addClass('selected');
            }
           var numIndex=selTips();
           if(numIndex[0]==0){
                layer.closeAll('page')
            }
        })
    })
}




    function selTips(){
        //获取选择牌的数量

        var selNum=$('.mypoke a.selected').length;
        $('.selected_poke span em').text(selNum);

        //打开提示框
         var layerIndex=layer.open({
         type: 1,
         shade: false,
         title: false, //不显示标题
         closeBtn: false,
         shift: 2,
         content: $('.selTips'), //捕获的元素
/*         cancel: function (index) {
         layer.close(index);
         this.content.hide()
         }*/
         });
         return [selNum];
    }


   //出牌
    $('.turn').click(function(){
        var selNum=$('.mypoke a.selected').length;
        if(selNum){
            var turnArr=[]
            $('.mypoke a.selected').each(function(){
                turnArr.push($(this).find('.lt').text());

            })
            if($('.sel_value').val()=='0'){
                layer.tips('请选择点数！', '.sel_value');
            }else{
                $.post('xxx',{"turnArrValue":turnArr,"sel_value":$('.sel_value').val()},function(){
                //post成功后执行下面的方法
                })

                /*模拟发送成功后*/
                function sucTurn(){
                layer.closeAll('page'); //关闭弹层
                $('.mypoke a.selected').remove();//删除打出的牌
                $('.sel_value').get(0).selectedIndex=0; //重置select
                pokeMargin('.mypoke a');//重置牌间距

                var cur='';
                for(var j=0;j<selNum;j++){
                    cur+='<a></a>';
                }
                    $('.current_poke').html(cur);
                    pokeMargin('.current_poke a')//重置打出的牌间距

                }
                sucTurn();

            }
        }else{
            layer.open({
                type: 4,
                tips: [1, '#f90'],
                time: 2000,
                closeBtn: false,
                content: ['请选择要打出的牌！', '.mypoke']
            });

        }

    });

    //过牌
    $('.pass').click(function(){

    //xxx

    });

    //判牌
    $('.sentence').click(function(){

    //xxx


    });





});
