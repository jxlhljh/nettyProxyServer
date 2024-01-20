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

import cn.lihua.common.AcceptorIdleStateTrigger;
import cn.lihua.common.ProxyLogHandler;
import cn.lihua.config.ProxyRule;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.timeout.IdleStateHandler;

public final class SocksServerInitializer extends ChannelInitializer<SocketChannel> {
	
	private ProxyRule proxyRule;
	private ProxyLogHandler pxLogHandler;
	
	public SocksServerInitializer(ProxyRule proxyRule,ProxyLogHandler pxLogHandler) {
		this.proxyRule = proxyRule;
		this.pxLogHandler = pxLogHandler;
	}
	
    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(
                //new LoggingHandler(LogLevel.DEBUG),
        		pxLogHandler,
        		new IdleStateHandler(AcceptorIdleStateTrigger.max_loss_connect_time, 0, 0,TimeUnit.SECONDS),
				new AcceptorIdleStateTrigger(), 
                new SocksPortUnificationServerHandler(),
                new SocksServerHandler(proxyRule));
    }
}
