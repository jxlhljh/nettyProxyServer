package cn.lihua.server.porttranstest;

import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.lihua.common.ProxyLogHandler;
import cn.lihua.config.ProxyRule;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

public class ProxyFrontendHandler extends ChannelInboundHandlerAdapter {
	
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private ProxyRule proxyRule;
	private Channel outboundChannel;

	public ProxyFrontendHandler(ProxyRule proxyRule) {
		this.proxyRule = proxyRule;
	}

	public void channelActive(ChannelHandlerContext ctx) {
		
		final Channel inboundChannel = ctx.channel();

		// Start the connection attempt.
		Bootstrap b = new Bootstrap();
		b.group(inboundChannel.eventLoop()).channel(ctx.channel().getClass())
		.handler(new ChannelInitializer<SocketChannel>(){
			protected void initChannel(SocketChannel ch) throws Exception {
				ch.pipeline().addLast(new ProxyLogHandler());
				
				//是否需要代理连接远程 
				if(proxyRule.isProxyNeed()) {
					
					/**
                	 * HTTP/HTTPS代理设置写法
                	 */
					if("http".equals(proxyRule.getProxyType())) {
						
                    	// 添加代理服务器处理器
                    	String proxyHost = proxyRule.getProxyIp();
                    	int proxyPort = proxyRule.getProxyPort();
                    	// 设置代理服务器的账号和密码
                        InetSocketAddress proxyAddr = new InetSocketAddress(proxyHost, proxyPort);
                        
                        //需要密码认证写法
                        if(!proxyRule.getProxyUsername().equals("") && !proxyRule.getProxyPassword().equals("")) {
                        	ProxyHandler proxyHandler = new HttpProxyHandler(proxyAddr, proxyRule.getProxyUsername(), proxyRule.getProxyPassword());
                        	ch.pipeline().addLast(proxyHandler);
                        }else {
                        	//无需密码认证写法
                            ProxyHandler proxyHandler = new HttpProxyHandler(proxyAddr); 
                            ch.pipeline().addLast(proxyHandler);
                        }
                    	
					}else {
						
						/**
                    	 * SOCK5代理设置写法
                    	 */
                    	// 设置 SOCKS5 代理服务器的地址和端口
						String proxyHost = proxyRule.getProxyIp();
                    	int proxyPort = proxyRule.getProxyPort();
                        InetSocketAddress proxyAddress = new InetSocketAddress(proxyHost, proxyPort);
                        
                        //需要密码认证写法
                        if(!proxyRule.getProxyUsername().equals("") && !proxyRule.getProxyPassword().equals("")) {
                        	
                        	 // 设置 SOCKS5 代理服务器的账号和密码
                            Socks5ProxyHandler proxyHandler = new Socks5ProxyHandler(proxyAddress, proxyRule.getProxyUsername(), proxyRule.getProxyPassword());
                            ch.pipeline().addLast(proxyHandler);
                        	
                        }else {
                        	//无需密码认证写法
                            Socks5ProxyHandler proxyHandler = new Socks5ProxyHandler(proxyAddress);
                            ch.pipeline().addLast(proxyHandler);
                            
                        }
						
					}
				}
				
				ch.pipeline().addLast(new ProxyBackendHandler(inboundChannel));
			}
		})
		//.handler(new ProxyBackendHandler(inboundChannel))
		.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)//超时时间
        .option(ChannelOption.SO_REUSEADDR, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
		.option(ChannelOption.AUTO_READ, true);

		ChannelFuture f = b.connect(proxyRule.getRemoteHost(), proxyRule.getRemotePort());
		outboundChannel = f.channel();

		f.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				if (future.isSuccess()) {
					// connection complete start to read first data
					inboundChannel.read();
					
				} else {
					// Close the connection if the connection attempt has
					// failed.
					inboundChannel.close();
				}
			}
		});

	}

	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		
		if (outboundChannel.isActive()) {
			
			outboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    if (future.isSuccess()) {
                        // was able to flush out data, start to read the next chunk
                        ctx.channel().read();
                    } else {
                        future.channel().close();
                    }
                }
            });
			
		}

	}

	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		//System.out.println("channelReadComplete");
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error("error",cause);
		closeOnFlush(ctx.channel());
	}
	
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
	}
	
    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

}
