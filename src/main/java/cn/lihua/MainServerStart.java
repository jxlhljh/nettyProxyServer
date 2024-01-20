package cn.lihua;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import cn.lihua.config.Config;
import cn.lihua.config.ProxyRule;
import cn.lihua.server.IService;
import cn.lihua.server.porttranstest.PortTransferServer;
import cn.lihua.server.proxyhttpserver.ProxyHttpServer;
import cn.lihua.server.proxysock5server.ProxySock5Server;

/**
 * 程序入口
 */
public class MainServerStart {
	
	public static void main(String[] args) {
		
		//获取所有的规则配置
		ConcurrentHashMap<Integer,ProxyRule> proxyRules  = Config.proxyRules;
		
		//遍历所有的规则启动对应的服务
		Set<Integer> keySet = proxyRules.keySet();
		for (Integer key : keySet) {
			ProxyRule proxyRule = proxyRules.get(key);
			IService server = getServer(proxyRule);
			server.start();
		}
		
	}
	
	/**
	 * 根据规则获取对应的服务
	 * @param proxyRule
	 * @return
	 */
	public static IService getServer(ProxyRule proxyRule) {
		
		if ("forward".equals(proxyRule.getServerType())) {
			return new PortTransferServer(proxyRule);
		} else if ("http".equals(proxyRule.getServerType())) {
			return new ProxyHttpServer(proxyRule);
		} else if ("sock5".equals(proxyRule.getServerType())) {
			//一种实现（自己实现的）
			return new ProxySock5Server(proxyRule);
			//另一种实现（官网方式）
			//return new SocksServer(proxyRule);
		}
		return null;
	}

}
