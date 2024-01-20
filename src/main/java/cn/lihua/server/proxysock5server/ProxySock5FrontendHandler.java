package cn.lihua.server.proxysock5server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.lihua.common.ProxyLogHandler;
import cn.lihua.config.ProxyRule;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
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

public class ProxySock5FrontendHandler extends ChannelInboundHandlerAdapter {
	
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	//缓存数据的buf，用于进行sock5认证前的数据
	private ByteBuf cacheBuf = PooledByteBufAllocator.DEFAULT.buffer(10);//Unpooled.buffer(10);
	private boolean cacheBufReleased = false;
	
	private boolean hasWriteVersionMethod ;//是否已写给客户端version_method数据
	private boolean hasWriteLoginResult;//写登陆结果
	private boolean hasWriteIpAndPort;//写登陆结果
	
	boolean sock5Prepared = false; //是否sock5完成
	private Channel outboundChannel;
	
	private ProxyRule proxyRule;
	
	public ProxySock5FrontendHandler(ProxyRule proxyRule) {
		this.proxyRule = proxyRule;
	}

	public void channelActive(ChannelHandlerContext ctx) {
		ctx.channel().read();
	}
	
	/**
	 * 打印字节
	 * @param buf
	 */
    public static void printByteBuf(ByteBuf buf) {
        // 确保 ByteBuf 的 readerIndex 不会改变
        int readerIndex = buf.readerIndex();
        int readableBytes = buf.readableBytes();

        // 创建一个临时数组来存储字节数据
        byte[] bytes = new byte[readableBytes];
        // 将 ByteBuf 中的数据复制到临时数组中
        buf.getBytes(readerIndex, bytes);

        // 打印字节数据
        for (byte b : bytes) {
            System.out.printf("%02x ", b);
        }
        System.out.println();
    }

	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		
		if(!sock5Prepared){
			
			cacheBuf.markReaderIndex();
			cacheBuf.writeBytes((ByteBuf)msg);
			//printByteBuf(cacheBuf);
			
			/**
			 * 一. 协商阶段
			 *	在这个阶段，客户端向socks5发起请求，内容如下:
			 *	+----+----------+----------+
			 *	|VER | NMETHODS | METHODS  |
			 *	+----+----------+----------+
			 *	| 1  |    1     | 1 to 255 |
			 * 	+----+----------+----------+
			 * #上方的数字表示字节数，下面的表格同理，不再赘述
			 *	VER: 协议版本，socks5为0x05
			 *	NMETHODS: 支持认证的方法数量
			 *	METHODS: 对应NMETHODS，NMETHODS的值为多少，METHODS就有多少个字节。RFC预定义了一些值的含义，内容如下:
			 *	X’00’ NO AUTHENTICATION REQUIRED 
			 *	X’01’ GSSAPI GSSAPI
			 *	X’02’ USERNAME/PASSWORD 用户名、密码认证
			 *	X’03’ to X’7F’ IANA ASSIGNED - 0x7F由IANA分配（保留）
			 *	X’80’ to X’FE’ RESERVED FOR PRIVATE METHODS - 0xFE为私人方法保留
			 *	X’FF’ NO ACCEPTABLE METHODS 无可接受的方法
			 */
	    	byte[] tmp = new byte[1];
	    	
	    	/**数据不够时，倒退重来*/
        	if(!hasMoreBytes(ctx,1)){
        		return;
        	}
	    	cacheBuf.readBytes(tmp);
	    	
	    	byte protocol = tmp[0];//1.获取协议头，如果是0x05表示是socks5
	    	if ((0x05 == protocol)) {// 如果开启代理5，并以socks5协议请求
	    		
	    		tmp = new byte[1]; //获取可供选择的方法，及选择的方法
	    		
	    		/**数据不够时，倒退重来*/
	        	if(!hasMoreBytes(ctx,1)){
	        		return;
	        	}
	    		cacheBuf.readBytes(tmp);
	    		
	    		/**数据不够时，倒退重来*/
	        	if(!hasMoreBytes(ctx,1)){
	        		return;
	        	}
	    		cacheBuf.readByte();
	    		
		        byte method = tmp[0];
		        if (0x02 == tmp[0]) {
		            method = 0x00;
		            
		            /**数据不够时，倒退重来*/
		        	if(!hasMoreBytes(ctx,1)){
		        		return;
		        	}
		            cacheBuf.readByte();
		            
		        }else {
		        	method = 0x00;
		        }
		        
		        if (proxyRule.isNeedLogin()) {
		            method = 0x02;
		        }
		        
		        boolean isLogin = false; //是否已经登录
		       
		        /**
				 *	二、socks5服务端收到上面的四个字节的数据后，需要选中一个METHOD返回给客户端，格式如下
				 *	+----+--------+
				 *	|VER | METHOD |
				 *	+----+--------+
				 *	| 1  |   1    |
				 *	+----+--------+
				 *	当客户端收到0x00时，会跳过认证阶段直接进入请求阶段; 当收到0xFF时，直接断开连接。其他的值进入到对应的认证阶段。
				 *  如果服务端不需要认证，服务端会发送0x05 0x00回来客户端
				 *  如果服务端需要认证，服务端会发送0x05 0x02回来客户端
				 */
		        //2.请求认证返回
		        tmp = new byte[] { 0x05, method };
		        
		        if(!hasWriteVersionMethod){
		        	ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(tmp));
		        	hasWriteVersionMethod = true;
		        }
		        
		        /**
				 *	三、如果返回的是要密码认证，则客户端继续发送密码相关的信息给服务器，并获取服务器的认证结果
				 *		+----+------+----------+------+----------+
				 *		|VER | ULEN |  UNAME   | PLEN |  PASSWD  |
				 *		+----+------+----------+------+----------+
				 *		| 1  |  1   | 1 to 255 |  1   | 1 to 255 |
				 *		+----+------+----------+------+----------+
				 *		VER: 版本，通常为0x01
				 *		ULEN: 用户名长度
				 *		UNAME: 对应用户名的字节数据
				 *		PLEN: 密码长度
				 *		PASSWD: 密码对应的数据
				 */
		        
		        //3.处理登录,这里直接返回成功
		        if (0x02 == method) {// 处理登录.
		        	
		        	/**数据不够时，倒退重来*/
		        	if(!hasMoreBytes(ctx,1)){
		        		return;
		        	}
		        	
		        	//获取用户名
		        	int b = cacheBuf.readByte();
		            String user = null;
		            String pwd = null;
		            if(0x01 == b) {
		            	
		            	/**数据不够时，倒退重来*/
			        	if(!hasMoreBytes(ctx,1)){
			        		return;
			        	}
		            	b = cacheBuf.readByte();//读取用户名长度
		            	
		                tmp = new byte[b];
		                
		                /**数据不够时，倒退重来*/
			        	if(!hasMoreBytes(ctx,b)){
			        		return;
			        	}
		                cacheBuf.readBytes(tmp);//读取用户名
		                
		                user = new String(tmp);
		                
		                /**数据不够时，倒退重来*/
			        	if(!hasMoreBytes(ctx,1)){
			        		return;
			        	}
		                b = cacheBuf.readByte();//读取密码长度
		                
		                tmp = new byte[b];
		                
		                /**数据不够时，倒退重来*/
			        	if(!hasMoreBytes(ctx,b)){
			        		return;
			        	}
		                cacheBuf.readBytes(tmp);//读取密码
		                
		                pwd = new String(tmp);
		                
		                /**
						 *	四、socks5服务端收到客户端的认证请求后，解析内容，验证信息是否合法，然后给客户端响应结果。响应格式如下
						 *	+----+--------+
						 *	|VER | STATUS |
						 *	+----+--------+
						 *	| 1  |   1    |
						 *	+----+--------+
						 *	STATUS字段如果为0x00表示认证成功，其他的值为认证失败。当客户端收到认证失败的响应后，它将会断开连接。
						 */
		                if (null != user && user.trim().equals(proxyRule.getUsername()) && null != pwd && pwd.trim().equals(proxyRule.getPassword())) {// 权限过滤
		                    isLogin = true;
		                    tmp = new byte[] { 0x05, 0x00 };// 登录成功
		                    if(!hasWriteLoginResult){
		        	        	ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(tmp));
		        	        	hasWriteLoginResult = true;
		        	        	logger.info("{} login success !", user);
		        	        }
		                    
		                } else {
		                    logger.error("{} login faild !", user);
		                }
		                
		            }else {
		            	
		            	logger.error("not socks proxy : openSock5[]");
			            closeOnFlush(ctx.channel());
		            	
		            }
		            
		        }
		        
		        /**
				 *	五、顺利通过协商阶段后，客户端向socks5服务器发起请求细节（如是TCP转发还是UDP，真实服务器IP和端口等），格式如:
				 *		+----+-----+-------+------+----------+----------+
				 *		|VER | CMD |  RSV  | ATYP | DST.ADDR | DST.PORT |
				 *		+----+-----+-------+------+----------+----------+
				 *		| 1  |  1  | X'00' |  1   | Variable |    2     |
				 *		+----+-----+-------+------+----------+----------+
				 *		VER 版本号，socks5的值为0x05
				 *		CMD
				 *		0x01表示CONNECT请求
				 *		0x02表示BIND请求
				 *		0x03表示UDP转发
				 *		RSV 保留字段，值为0x00
				 *		ATYP 目标地址类型，DST.ADDR的数据对应这个字段的类型。
				 *		0x01表示IPv4地址，DST.ADDR为4个字节
				 *		0x03表示域名，DST.ADDR是一个可变长度的域名
				 *		0x04表示IPv6地址，DST.ADDR为16个字节长度
				 *		DST.ADDR 一个可变长度的值
				 *		DST.PORT 目标端口，固定2个字节
				 *		上面的值中，DST.ADDR是一个变长的数据，它的数据长度根据ATYP的类型决定。
				 */
		        //4.客户端 -> 代理服务器，发送目标信息
		        //版本号(1字节)	命令(1字节)	保留(1字节)	请求类型(1字节)	地址(不定长)	端口(2字节)
		        if(!proxyRule.isNeedLogin() || isLogin) {
		        	
		        	/**数据不够时，倒退重来*/
		        	if(!hasMoreBytes(ctx,4)){
		        		return;
		        	}
		        	
		        	tmp = new byte[4];
		        	cacheBuf.readBytes(tmp);
		            logger.info("proxy header >>  {}", Arrays.toString(tmp));
		            
		            //包校验，如果不是0x05，则为非法请求，直接关闭流
		            if(tmp[0] != 0x05){
		            	logger.error("not socks proxy : openSock5[]");
			            closeOnFlush(ctx.channel());
		            }
		            
		            String host = getHost(tmp[3], cacheBuf);//获程远程主机ip
		            tmp = new byte[2];//获程远程主机端口
		            
		            /**数据不够时，倒退重来*/
		        	if(!hasMoreBytes(ctx,2)){
		        		return;
		        	}
		            cacheBuf.readBytes(tmp);
		            
		            int port = ByteBuffer.wrap(tmp).asShortBuffer().get() & 0xFFFF;
		            
		            /**
					 *	六、socks5服务器收到客户端的请求细节（如是TCP转发还是UDP，真实服务器IP和端口等）后，需要返回一个响应，格式如:
					 *		+----+-----+-------+------+----------+----------+
					 *		|VER | REP |  RSV  | ATYP | BND.ADDR | BND.PORT |
					 *		+----+-----+-------+------+----------+----------+
					 *		| 1  |  1  | X'00' |  1   | Variable |    2     |
					 *		+----+-----+-------+------+----------+----------+
					 *		VER socks版本，这里为0x05
					 *		REP Relay field,内容取值如下
					 *		X’00’ succeeded
					 *		X’01’ general SOCKS server failure
					 *		X’02’ connection not allowed by ruleset
					 *		X’03’ Network unreachable
					 *		X’04’ Host unreachable
					 *		X’05’ Connection refused
					 *		X’06’ TTL expired
					 *		X’07’ Command not supported
					 *		X’08’ Address type not supported
					 *		X’09’ to X’FF’ unassigned
					 *		RSV 保留字段
					 *		ATYPE 同请求的ATYPE
					 *		BND.ADDR 服务绑定的地址
					 *		BND.PORT 服务绑定的端口DST.PORT 
					 */
		            // 登录成功,不告客户端底层ip和port的写法
		        	tmp = new byte[] { 0x05, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
		        	if(!hasWriteIpAndPort){
	    	        	ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(tmp));
	    	        	hasWriteIpAndPort = true;
	    	        }
		            
		        	//5协议完成，进行真正的数据转发，交由Sock5FrontendHandler进行处理
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
		    				
		    				ch.pipeline().addLast(new ProxySock5BackendHandler(inboundChannel));
		    				
		    			}
		    		})
		    		//.handler(new ProxyBackendHandler(inboundChannel))
		    		.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)//超时时间
		            .option(ChannelOption.SO_REUSEADDR, true)
		            .option(ChannelOption.SO_KEEPALIVE, true)
		    		.option(ChannelOption.AUTO_READ, true);

		    		ChannelFuture f = b.connect(InetSocketAddress.createUnresolved(host, port));
		    		outboundChannel = f.channel();

		    		f.addListener(new ChannelFutureListener() {
		    			@Override
		    			public void operationComplete(ChannelFuture future) {
		    				if (future.isSuccess()) {
		    					sock5Prepared = true;
		    					cacheBuf.release();
		    					cacheBufReleased = true;
		    					//logger.info("cacheBuf released, ProxySock5FrontendHandler hashCode: {}" , ProxySock5FrontendHandler.this.hashCode());
		    					// connection complete start to read first data
		    					inboundChannel.read();
		    					
		    				} else {
		    					// Close the connection if the connection attempt has
		    					// failed.
		    					inboundChannel.close();
		    				}
		    			}
		    		});
		            
		            
		        }else {
		            tmp = new byte[] { 0x05, 0x01 };// 登录失败
		            logger.error("socks server need login,but no login info .");
		            ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(tmp));
		            closeOnFlush(ctx.channel());
		        }
		        
	    		
	    	}else{
	    		
	            logger.error("not socks proxy : openSock5[]");
	            closeOnFlush(ctx.channel());
	    		
	    	}
			
		}else {
			
			if (outboundChannel!= null && outboundChannel.isActive()) {
				
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

	}
	
	/**
	 * 检查是否有数据，没有就重置读索引
	 * @return
	 */
	private boolean hasMoreBytes(ChannelHandlerContext ctx, int length){
		if(cacheBuf.readableBytes() < length){
    		cacheBuf.resetReaderIndex();
    		ctx.channel().read();
    		return false;
    	}
		return true;
	}
	
	/**
     * 获取目标的服务器地址
     */
    private String getHost(byte type, ByteBuf in) throws IOException {
        String host = null;
        byte[] tmp = null;
        switch (type) {
        case 0x01:// IPV4协议
            tmp = new byte[4];
            in.readBytes(tmp);
            host = InetAddress.getByAddress(tmp).getHostAddress();
            break;
        case 0x03:// 使用域名
            int l = in.readByte();
            tmp = new byte[l];
            in.readBytes(tmp);
            host = new String(tmp);
            break;
        case 0x04:// 使用IPV6
            tmp = new byte[16];
            in.readBytes(tmp);
            host = InetAddress.getByAddress(tmp).getHostAddress();
            break;
        default:
            break;
        }
        return host;
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
		if(!cacheBufReleased) {
			cacheBuf.release();
			//logger.info("cacheBuf released, ProxySock5FrontendHandler hashCode: {}" , this.hashCode());
		}
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
