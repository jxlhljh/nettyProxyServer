package cn.lihua.server.proxyhttpserver;

import java.net.InetSocketAddress;
import java.util.Base64;

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
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

//https://www.jianshu.com/p/aaa211c11a27
public class ProxyHttpHandler extends ChannelInboundHandlerAdapter {
	
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
    private String host;
    private int port;
    private boolean isConnectMethod = false;

    // 代理到远端服务器的 channel
    private Channel remoteChannel;
    
	private ProxyRule proxyRule;
	
	public ProxyHttpHandler(ProxyRule proxyRule) {
		this.proxyRule = proxyRule;
	}

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
		// clientChannel = ctx.channel();
    }

	// 代理服务器需要认证,发送回浏览器弹出输入账号密码框（仅支持http代理）
	private void sendAuthRequiredResponse(ChannelHandlerContext ctx) {
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED);
		response.headers().set(HttpHeaderNames.PROXY_AUTHENTICATE, "Basic realm=\"Proxy\"");
		// 以下四行代码注解掉好像也没有什么问题，这里就直接开放出来
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");
		String content = "<html><body><h1>407 Proxy Authentication Required</h1></body></html>";
		response.content().writeBytes(content.getBytes());
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
		ctx.writeAndFlush(response);
	}

    @Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    	
		// 客户端到代理的 channel
		Channel clientChannel = ctx.channel();

        if (msg instanceof FullHttpRequest) {
            FullHttpRequest httpRequest = (FullHttpRequest) msg;
            
			logger.info("\r\n{}", httpRequest.toString());

            //如果要认证账号和密码，校验一下是否正确
            if(proxyRule.isNeedLogin()){
            	String authorizationStr = "Basic "+ Base64.getEncoder().encodeToString(new String(proxyRule.getUsername() + ":" + proxyRule.getPassword()).getBytes());
            	String proxyAuthorizationStr = httpRequest.headers().get("Proxy-Authorization");
            	if(!authorizationStr.equals(proxyAuthorizationStr)){
            		logger.error("httpproxy server need login,but login failed .");
					// throw new RuntimeException("auth failed.");
					// 认证失败，返回407状态码，浏览器会弹出输入账号密码框
					sendAuthRequiredResponse(ctx);
            	}
            }
            
            isConnectMethod = HttpMethod.CONNECT.equals(httpRequest.method());

            // 解析目标主机host和端口号
            parseHostAndPort(httpRequest);
            logger.info("remote server is " + host + ":" + port);

            // disable AutoRead until remote connection is ready
            clientChannel.config().setAutoRead(false);

            /**
             * 建立代理服务器到目标主机的连接
             */
            Bootstrap b = new Bootstrap();
            b.group(clientChannel.eventLoop())  // 和 clientChannel 使用同一个 EventLoop
                    .channel(clientChannel.getClass())
                  //第一种写法
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
                        	
							// 加HttpRequestEncoder的目的是进行第一个httpRequest包的转发
							ch.pipeline().addLast(new HttpRequestEncoder());
        					
        				}
        			});
            
            ChannelFuture f = b.connect(InetSocketAddress.createUnresolved(host, port));
            remoteChannel = f.channel();
            f.addListener((ChannelFutureListener) future -> {
            	
                if (future.isSuccess()) {
                	
                    // connection is ready, enable AutoRead
                    clientChannel.config().setAutoRead(true);

                    if (isConnectMethod) {
                        // CONNECT 请求回复连接建立成功
                        HttpResponse connectedResponse = new DefaultHttpResponse(httpRequest.protocolVersion(), new HttpResponseStatus(200, "Connection Established"));
                        clientChannel.writeAndFlush(connectedResponse);
                    } else {

						// 普通的http请求要特殊处理一下消息头
						// 将Proxy-Connection替换为Connection
						// 先获取Proxy-Connection的值
						String proxyConnection = httpRequest.headers().get("Proxy-Connection");
						if (proxyConnection != null) {
							httpRequest.headers().remove("Proxy-Connection");
							httpRequest.headers().set("Connection", proxyConnection);
						}

						// 如果有认证的密码消息头,也进行去掉
						httpRequest.headers().remove("Proxy-Authorization");

						// 替换域名,将全路径的url替换为相对路径
						String reqLine = httpRequest.uri();
						reqLine = replaceDomain(reqLine);
						httpRequest.setUri(reqLine);

                        // 普通http请求解析了第一个完整请求，第一个请求也要原样发送到远端服务器
                        remoteChannel.writeAndFlush(httpRequest);

                    }

                    /**
                     * 第一个完整Http请求处理完毕后，不需要解析任何 Http 数据了，直接盲目转发 TCP 流就行了
                     * 所以无论是连接客户端的 clientChannel 还是连接远端主机的 remoteChannel 都只需要一个 RelayHandler 就行了。
                     * 代理服务器在中间做转发。
                     *
                     * 客户端   --->  clientChannel --->  代理 ---> remoteChannel ---> 远端主机
                     * 远端主机 --->  remoteChannel  --->  代理 ---> clientChannel ---> 客户端
                     */
					if (isConnectMethod) {

						ChannelPipeline pipeline = clientChannel.pipeline();

						if (pipeline.get(HttpRequestDecoder.class) != null) {
							clientChannel.pipeline().remove(HttpRequestDecoder.class);
						}

						if (pipeline.get(HttpResponseEncoder.class) != null) {
							clientChannel.pipeline().remove(HttpResponseEncoder.class);
						}

						if (pipeline.get(HttpObjectAggregator.class) != null) {
							clientChannel.pipeline().remove(HttpObjectAggregator.class);
						}

						if (pipeline.get(ProxyHttpHandler.class) != null) {
							clientChannel.pipeline().remove(ProxyHttpHandler.this);
						}

						clientChannel.pipeline().addLast(new RelayHandler(remoteChannel));

					} else {

						// 非CONNECT方法,说明是普通的http请求,只需要移除HttpResponseEncoder和ProxyHttpHandler
						// 留下HttpRequestDecoder和HttpObjectAggregator,进行请求解包,提供到NormalHttpRequestRelayHandler处理数据包改写
						clientChannel.pipeline().remove(HttpResponseEncoder.class);
						clientChannel.pipeline().remove(ProxyHttpHandler.this);
						clientChannel.pipeline().addLast(new NormalHttpRequestRelayHandler(remoteChannel));

					}

					// 非https请求要留着HttpRequestEncoder
					if (isConnectMethod) {

						if (remoteChannel.pipeline().get(HttpRequestEncoder.class) != null) {
							remoteChannel.pipeline().remove(HttpRequestEncoder.class);
						}

					}
                    remoteChannel.pipeline().addLast(new RelayHandler(clientChannel));
                    
                } else {
                    clientChannel.close();
                }
                
            });
        }
    }

	// 替换域名
	private String replaceDomain(String reqUrl) {
		int endIndex = reqUrl.indexOf("/", 8); // 第8个字符开始搜索斜杠
		String domain = endIndex != -1 ? reqUrl.substring(0, endIndex + 1) : reqUrl;
		reqUrl = reqUrl.replaceAll(domain, "/");
		return reqUrl;
	}

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        flushAndClose(remoteChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(),cause);
        flushAndClose(ctx.channel());
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * 解析header信息，建立连接
     * HTTP 请求头如下
     * GET http://www.baidu.com/ HTTP/1.1
     * Host: www.baidu.com
     * User-Agent: curl/7.69.1
     * Proxy-Connection:Keep-Alive
     * ---------------------------
     * HTTPS请求头如下
     * CONNECT www.baidu.com:443 HTTP/1.1
     * Host: www.baidu.com:443
     * User-Agent: curl/7.69.1
     * Proxy-Connection: Keep-Alive
     */
    private void parseHostAndPort(HttpRequest httpRequest) {
        String hostAndPortStr;
        if (isConnectMethod) {
            // CONNECT 请求以请求行为准
            hostAndPortStr = httpRequest.uri();
        } else {
            hostAndPortStr = httpRequest.headers().get("Host");
        }
        String[] hostPortArray = hostAndPortStr.split(":");
        host = hostPortArray[0];
        if (hostPortArray.length == 2) {
            port = Integer.parseInt(hostPortArray[1]);
        } else if (isConnectMethod) {
            // 没有端口号，CONNECT 请求默认443端口
            port = 443;
        } else {
            // 没有端口号，普通HTTP请求默认80端口
            port = 80;
        }
    }

}

