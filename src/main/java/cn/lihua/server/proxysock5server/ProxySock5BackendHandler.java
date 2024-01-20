package cn.lihua.server.proxysock5server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class ProxySock5BackendHandler extends ChannelInboundHandlerAdapter {
	
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private Channel inboundChannel;
	
	public ProxySock5BackendHandler(Channel inboundChannel) {
		this.inboundChannel = inboundChannel;
	}
	
    public void channelActive(ChannelHandlerContext ctx) {
    	//ctx.read();
    }
    
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        inboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    //ctx.channel().read();
                } else {
                    future.channel().close();
                }
            }
        });
    }
    
    public void channelInactive(ChannelHandlerContext ctx) {
    	ProxySock5FrontendHandler.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    	logger.error("error",cause);
        ProxySock5FrontendHandler.closeOnFlush(ctx.channel());
    }

}
