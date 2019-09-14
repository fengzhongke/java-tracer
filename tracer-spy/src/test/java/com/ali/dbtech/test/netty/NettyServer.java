/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.ali.dbtech.test.netty;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class NettyServer {
    private static final int port = 6789; //设置服务端端口
//    private static  EventLoopGroup pGroup = new NioEventLoopGroup(1);   // 通过nio方式来接收连接和处理连接
    //private static  EventLoopGroup sGroup = new NioEventLoopGroup(1);   // 通过nio方式来接收连接和处理连接
    //private static  ServerBootstrap b = new ServerBootstrap();

    /**
     * Netty创建全部都是实现自AbstractBootstrap。
     * 客户端的是Bootstrap，服务端的则是    ServerBootstrap。
     **/
    public static void main(String[] args) throws InterruptedException {
        EventLoopGroup pGroup = new NioEventLoopGroup(1);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(pGroup, pGroup);
            b.channel(NioServerSocketChannel.class);
            b.childHandler(new NettyServerFilter()); //设置过滤器
            // 服务器绑定端口监听
            ChannelFuture f = b.bind(port).sync();
            System.out.println("服务端启动成功...");
            // 监听服务器关闭监听
            f.channel().closeFuture().sync();
        } finally {
            pGroup.shutdownGracefully(); ////关闭EventLoopGroup，释放掉所有资源包括创建的线程
            //sGroup.shutdownGracefully(); ////关闭EventLoopGroup，释放掉所有资源包括创建的线程
        }
    }
}