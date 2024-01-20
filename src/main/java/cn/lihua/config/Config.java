package cn.lihua.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

import cn.lihua.utils.YmlUtils;

public class Config {
	
	private static final String fileName = "config.yml";
	
	//代理规则列表
	public static ConcurrentHashMap<Integer,ProxyRule> proxyRules = null;
	
	//初始化
	static {
		proxyRules = reload();
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static ConcurrentHashMap<Integer,ProxyRule> reload(){
		
		ConcurrentHashMap<Integer,ProxyRule> proxyRules = new ConcurrentHashMap<Integer,ProxyRule>();
		
		YmlUtils.reloadYml(fileName);
		
		//1.先加载全局后端代理配置
		LinkedHashMap global = (LinkedHashMap)YmlUtils.getValue(fileName,"global");
		
		//2.加载代理规则
		ArrayList<LinkedHashMap> configs = (ArrayList)YmlUtils.getValue(fileName,"configs");
		
		//3.合并全局配置和代理规则
		if(configs != null) {
			for (LinkedHashMap config : configs) {
				
				ProxyRule proxyRule = new ProxyRule();
				
				// 全局的
				if(global != null) {
					if(global.get("proxyNeed") != null) {
						proxyRule.setProxyNeed((Boolean) global.get("proxyNeed"));
					}
					
					if(global.get("proxyType") != null) {
						proxyRule.setProxyType((String) global.get("proxyType"));
					}
					
					if(global.get("proxyIp") != null) {
						proxyRule.setProxyIp((String) global.get("proxyIp"));
					}
					
					if(global.get("proxyPort") != null) {
						proxyRule.setProxyPort((Integer) global.get("proxyPort"));
					}
					
					if(global.get("proxyUsername") != null) {
						proxyRule.setProxyUsername((String) global.get("proxyUsername"));
					}
					
					if(global.get("proxyPassword") != null) {
						proxyRule.setProxyPassword((String) global.get("proxyPassword"));
					}
				}
				
				//接着加载具体的规则
				if(config.get("enable") != null) {
					proxyRule.setEnable((Boolean) config.get("enable"));
				}
				
				if(config.get("serverType") != null) {
					proxyRule.setServerType((String) config.get("serverType"));
				}
				
				if(config.get("serverPort") != null) {
					proxyRule.setServerPort((Integer) config.get("serverPort"));
				}
				
				if(config.get("remoteHost") != null) {
					proxyRule.setRemoteHost((String) config.get("remoteHost"));
				}
				
				if(config.get("remotePort") != null) {
					proxyRule.setRemotePort((Integer) config.get("remotePort"));
				}
				
				if(config.get("needLogin") != null) {
					proxyRule.setNeedLogin((Boolean) config.get("needLogin"));
				}
				
				if(config.get("username") != null) {
					proxyRule.setUsername((String) config.get("username"));
				}
				
				if(config.get("password") != null) {
					proxyRule.setPassword((String) config.get("password"));
				}
				
				if(config.get("proxyNeed") != null) {
					proxyRule.setProxyNeed((Boolean) config.get("proxyNeed"));
				}
				
				if(config.get("proxyType") != null) {
					proxyRule.setProxyType((String) config.get("proxyType"));
				}
				
				if(config.get("proxyIp") != null) {
					proxyRule.setProxyIp((String) config.get("proxyIp"));
				}
				
				if(config.get("proxyPort") != null) {
					proxyRule.setProxyPort((Integer) config.get("proxyPort"));
				}
				
				if(config.get("proxyUsername") != null) {
					proxyRule.setProxyUsername((String) config.get("proxyUsername"));
				}
				
				if(config.get("proxyPassword") != null) {
					proxyRule.setProxyPassword((String) config.get("proxyPassword"));
				}
				
				if (proxyRules.get(proxyRule.getServerPort()) != null) {
					throw new RuntimeException("存在端口冲突：" + proxyRule.getServerPort() + ", 本次修改不生效.");
				}
				
				//启动生效的
				if(proxyRule.isEnable()) {
					proxyRules.put(proxyRule.getServerPort(), proxyRule);
				}
				
				
			}
			
		}
		
		return proxyRules;
		
	}
	
}
