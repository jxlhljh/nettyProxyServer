package cn.lihua.server.porttranstest;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.lihua.common.AcceptorIdleStateTrigger;
import cn.lihua.common.ProxyLogHandler;
import cn.lihua.config.ProxyRule;
import cn.lihua.server.IService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * 端口转发程序Netty版本
 * @author liujh
 *
 */
public class PortTransferServer extends IService{

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private ProxyRule proxyRule;
	
	private ChannelFuture future = null; //保存句丙，用于关闭服务
	
	public PortTransferServer(ProxyRule proxyRule) {
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
			ServerBootstrap serverBootstrap = new ServerBootstrap();
			serverBootstrap.group(bossGroup, workerGroup)// 设置主从线程组
					.channel(NioServerSocketChannel.class)// 设置通道
					.option(ChannelOption.SO_BACKLOG, 1024)
					.childOption(ChannelOption.SO_REUSEADDR, true)
					.childOption(ChannelOption.SO_KEEPALIVE, true)
					.childOption(ChannelOption.AUTO_READ, false)
					// .handler(new LoggingHandler(LogLevel.INFO))
					.childHandler(new ChannelInitializer<SocketChannel>() {
						protected void initChannel(SocketChannel ch) throws Exception {
							ch.pipeline().addLast(
									pxLogHandler,
									new IdleStateHandler(AcceptorIdleStateTrigger.max_loss_connect_time, 0, 0,TimeUnit.SECONDS),
									new AcceptorIdleStateTrigger(), 
									new ProxyFrontendHandler(proxyRule));
						};

					});// 子处理器，用于处理workerGroup中的操作

			// 启动server
			future = serverBootstrap.bind(proxyRule.getServerPort()).sync();

			logger.info("service started , listen on " + proxyRule.getServerPort() + ",Remote:" + proxyRule.getRemoteHost() + ":" + proxyRule.getRemotePort());
			
			//启动打印线程打印存活的链接数量
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

	@Override
	public ProxyRule getProxyRule() {
		return proxyRule;
	}
	
	public void setProxyRule(ProxyRule proxyRule) {
		this.proxyRule = proxyRule;
	}

}
