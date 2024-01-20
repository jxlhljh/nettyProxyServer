package cn.lihua.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

@ChannelHandler.Sharable
public class ProxyLogHandler extends ChannelInboundHandlerAdapter{
	
	public ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		if(logger.isInfoEnabled()){
			logger.info("{} connect.",ctx.channel());
		}
		if(!channelGroup.contains(ctx.channel())){
			channelGroup.add(ctx.channel());
		}
		super.channelActive(ctx);
	}
	
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		
		if(logger.isInfoEnabled()){
			logger.info("{} close.",ctx.channel());
		}
		
		if(channelGroup.contains(ctx.channel())){
			channelGroup.remove(ctx.channel());
		}
		
		super.channelInactive(ctx);
	}
	
}
