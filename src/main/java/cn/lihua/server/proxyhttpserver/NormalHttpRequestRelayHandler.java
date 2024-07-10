package cn.lihua.server.proxyhttpserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * 普通的http请求处理器,进行代理连接时,会传Proxy-Connection, 另外url是传的绝对路径,需要修改消息头,和把绝对路径去掉,因此特殊处理
 */
public class NormalHttpRequestRelayHandler extends ChannelInboundHandlerAdapter {
	
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private Channel remoteChannel;

    public NormalHttpRequestRelayHandler(Channel remoteChannel) {
        this.remoteChannel = remoteChannel;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    	super.channelActive(ctx);
    }
    
	// 替换域名将绝对路径替换成相对路径
	private String replaceDomain(String reqUrl) {
		int endIndex = reqUrl.indexOf("/", 8); // 第8个字符开始搜索斜杠
		String domain = endIndex != -1 ? reqUrl.substring(0, endIndex + 1) : reqUrl;
		reqUrl = reqUrl.replaceAll(domain, "/");
		return reqUrl;
	}

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

    	/**
    	 * 如果是FullHttpRequest,则进行数据包改写
    	 */
		if (msg instanceof FullHttpRequest) {

			FullHttpRequest httpRequest = (FullHttpRequest) msg;

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

			remoteChannel.writeAndFlush(httpRequest);
		} else {
			remoteChannel.writeAndFlush(msg);
		}

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error(cause.getMessage(), cause);
        flushAndClose(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    	
        /**
         * 连接断开时关闭另一端连接。
         * 如果代理到远端服务器连接断了也同时关闭代理到客户的连接。
         * 如果代理到客户端的连接断了也同时关闭代理到远端服务器的连接。
         */
        flushAndClose(remoteChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
