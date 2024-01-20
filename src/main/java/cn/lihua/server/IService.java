package cn.lihua.server;

import cn.lihua.config.ProxyRule;

public abstract class IService extends Thread{
	
	/**
	 * 获取ProxyRule
	 * @return
	 */
	public abstract ProxyRule getProxyRule();
	
	public abstract void setProxyRule(ProxyRule proxyRule);
	
	/**
	 * 停止服务
	 */
	public abstract void stopService();

}
