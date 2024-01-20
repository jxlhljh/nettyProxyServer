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

import java.net.InetSocketAddress;

import cn.lihua.common.ProxyLogHandler;
import cn.lihua.config.ProxyRule;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

@ChannelHandler.Sharable
public final class SocksServerConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {
	
	private ProxyRule proxyRule;
	
	public SocksServerConnectHandler(ProxyRule proxyRule) {
		this.proxyRule = proxyRule;
	}
	
    private final Bootstrap b = new Bootstrap();
    
    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final SocksMessage message) throws Exception {
        if (message instanceof Socks4CommandRequest) {
            final Socks4CommandRequest request = (Socks4CommandRequest) message;
            Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener(
                    new FutureListener<Channel>() {
                        @Override
                        public void operationComplete(final Future<Channel> future) throws Exception {
                            final Channel outboundChannel = future.getNow();
                            if (future.isSuccess()) {
                                ChannelFuture responseFuture = ctx.channel().writeAndFlush(
                                        new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS));

                                responseFuture.addListener(new ChannelFutureListener() {
                                    @Override
                                    public void operationComplete(ChannelFuture channelFuture) {
                                        ctx.pipeline().remove(SocksServerConnectHandler.this);
                                        outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                                        ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                                    }
                                });
                            } else {
                                ctx.channel().writeAndFlush(
                                        new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED));
                                SocksServerUtils.closeOnFlush(ctx.channel());
                            }
                        }
                    });

            final Channel inboundChannel = ctx.channel();
            b.group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new DirectClientHandler(promise));

            b.connect(request.dstAddr(), request.dstPort()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // Connection established use handler provided results
                    } else {
                        // Close the connection if the connection attempt has failed.
                        ctx.channel().writeAndFlush(
                                new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED)
                        );
                        SocksServerUtils.closeOnFlush(ctx.channel());
                    }
                }
            });
        } else if (message instanceof Socks5CommandRequest) {
            final Socks5CommandRequest request = (Socks5CommandRequest) message;
            Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener(
                    new FutureListener<Channel>() {
                        @Override
                        public void operationComplete(final Future<Channel> future) throws Exception {
                            final Channel outboundChannel = future.getNow();
                            if (future.isSuccess()) {
                                ChannelFuture responseFuture =
                                        ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
                                                Socks5CommandStatus.SUCCESS,
                                                request.dstAddrType(),
                                                request.dstAddr(),
                                                request.dstPort()));

                                responseFuture.addListener(new ChannelFutureListener() {
                                    @Override
                                    public void operationComplete(ChannelFuture channelFuture) {
                                        ctx.pipeline().remove(SocksServerConnectHandler.this);
                                        outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                                        ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                                    }
                                });
                            } else {
                                ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
                                        Socks5CommandStatus.FAILURE, request.dstAddrType()));
                                SocksServerUtils.closeOnFlush(ctx.channel());
                            }
                        }
                    });

            final Channel inboundChannel = ctx.channel();
            b.group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    //.handler(new DirectClientHandler(promise))
                    .handler(new ChannelInitializer<SocketChannel>() {
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
							
							ch.pipeline().addLast(new DirectClientHandler(promise));
						}
                    });

            b.connect(request.dstAddr(), request.dstPort()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // Connection established use handler provided results
                    } else {
                        // Close the connection if the connection attempt has failed.
                        ctx.channel().writeAndFlush(
                                new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType()));
                        SocksServerUtils.closeOnFlush(ctx.channel());
                    }
                }
            });
        } else {
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        SocksServerUtils.closeOnFlush(ctx.channel());
    }
}
