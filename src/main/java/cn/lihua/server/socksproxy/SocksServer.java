/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package cn.lihua.server.socksproxy;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.lihua.common.ProxyLogHandler;
import cn.lihua.config.ProxyRule;
import cn.lihua.server.IService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * Netty官方提供的Sock5例子，增加账号密码认证功能
 * @author liujh
 *
 */
public final class SocksServer extends IService {

    private static final Logger logger = LoggerFactory.getLogger(SocksServer.class);
    
	private ChannelFuture future = null; //保存句丙，用于关闭服务
	
	private ProxyRule proxyRule;
	
	public SocksServer(ProxyRule proxyRule) {
		this.proxyRule = proxyRule;
	}
	
	@Override
	public void run() {
		startService();
	}
	
	public void startService() {

		// 创建主线程组，接收请求
		EventLoopGroup bossGroup = new NioEventLoopGroup(1);
		// 创建从线程组，处理主线程组分配下来的io操作
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		// 创建netty服务器
		ProxyLogHandler pxLogHandler = new ProxyLogHandler();
		try {
			
			ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             //.handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new SocksServerInitializer(proxyRule,pxLogHandler));
            ChannelFuture future = b.bind(proxyRule.getServerPort()).sync();
            
            logger.info("service started , listen on "+ proxyRule.getServerPort());
            
            //启动打印线程打印存活的链接数量,只启动一次
            future.channel().eventLoop().scheduleAtFixedRate(
            		() -> logger.info("serverPort: {}, current actived connect number is: {}",proxyRule.getServerPort(),pxLogHandler.channelGroup.size()), 
            		10, 10, TimeUnit.SECONDS);
            
            // 监听关闭channel
            future.channel().closeFuture().sync();

		} catch (Exception e) {
			logger.error("Netty server failed. serverPort: {}", proxyRule.getServerPort());
		} finally {
			bossGroup.shutdownGracefully();// 关闭主线程
			workerGroup.shutdownGracefully();// 关闭从线程
		}

	}

	public void stopService() {
		
		if(future != null) {
			future.channel().close();
			future = null;
		}
		
	}
	
	public ProxyRule getProxyRule() {
		return proxyRule;
	}
	
	public void setProxyRule(ProxyRule proxyRule) {
		this.proxyRule = proxyRule;
	}
	
}
