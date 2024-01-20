package cn.lihua.config;

import java.util.Objects;

/**
 * 规则类,每一个Bean代表一个代理规则
 */
public class ProxyRule {
	
	/**
	 * 规则是否生效
	 */
	private boolean enable = true;
	
	/**
	 * 类型，forward或http或sock5,表示端口转发或http代理或sock5代理
	 */
	private String serverType = "forward";
	
	/**
	 * 端口号
	 */
	private int serverPort = 13306;
	
	/**
	 * 转发的目标IP，serverType为forward时此参数才有意义
	 */
	private String remoteHost = "127.0.0.1";
	
	/**
	 * 转发的目标端口，serverType为forward时此参数才有意义
	 */	
	private int remotePort = 3306;
	
	/**
	 * 是否需要认证，serverType为http和sock5时此参数才有意义
	 */
	private boolean needLogin = false;
	
	/**
	 * 认证账号，serverType为http和sock5时此参数才有意义
	 */
	private String username = "";
	
	/**
	 * 认证密码，serverType为http和sock5时此参数才有意义
	 */
	private String password  = "";
	
	
	/**
	 * 是否需要通过后端代理连接远程服务器
	 */
	private boolean proxyNeed = false;
	
	/**
	 * 代理连接类型，http或socks5
	 */
	private String proxyType = "http";
	
	/**
	 * 代理连接IP
	 */
	private String proxyIp = "127.0.0.1";
	
	/**
	 * 代理连接Port
	 */
	private int proxyPort = 1080;
	
	/**
	 * 代理连接用户名,为空说明不需要认证
	 */
	private String proxyUsername = "";
	
	/**
	 * 代理连接密码,为空说明不需要认证
	 */
	private String proxyPassword = "";
	
	public String getServerType() {
		return serverType;
	}

	public void setServerType(String serverType) {
		this.serverType = serverType;
	}

	public int getServerPort() {
		return serverPort;
	}

	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}

	public String getRemoteHost() {
		return remoteHost;
	}

	public void setRemoteHost(String remoteHost) {
		this.remoteHost = remoteHost;
	}

	public int getRemotePort() {
		return remotePort;
	}

	public void setRemotePort(int remotePort) {
		this.remotePort = remotePort;
	}

	public boolean isNeedLogin() {
		return needLogin;
	}

	public void setNeedLogin(boolean needLogin) {
		this.needLogin = needLogin;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public boolean isProxyNeed() {
		return proxyNeed;
	}

	public void setProxyNeed(boolean proxyNeed) {
		this.proxyNeed = proxyNeed;
	}

	public String getProxyType() {
		return proxyType;
	}

	public void setProxyType(String proxyType) {
		this.proxyType = proxyType;
	}

	public String getProxyIp() {
		return proxyIp;
	}

	public void setProxyIp(String proxyIp) {
		this.proxyIp = proxyIp;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}

	public String getProxyUsername() {
		return proxyUsername;
	}

	public void setProxyUsername(String proxyUsername) {
		this.proxyUsername = proxyUsername;
	}

	public String getProxyPassword() {
		return proxyPassword;
	}

	public void setProxyPassword(String proxyPassword) {
		this.proxyPassword = proxyPassword;
	}

	public boolean isEnable() {
		return enable;
	}

	public void setEnable(boolean enable) {
		this.enable = enable;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ProxyRule) {
			ProxyRule proxyRule = (ProxyRule) obj;
			return Objects.equals(serverType, proxyRule.serverType) && Objects.equals(serverPort, proxyRule.serverPort)
					&& Objects.equals(remoteHost, proxyRule.remoteHost)
					&& Objects.equals(remotePort, proxyRule.remotePort)
					&& Objects.equals(needLogin, proxyRule.needLogin) && Objects.equals(username, proxyRule.username)
					&& Objects.equals(password, proxyRule.password) && Objects.equals(proxyNeed, proxyRule.proxyNeed)
					&& Objects.equals(proxyType, proxyRule.proxyType) && Objects.equals(proxyIp, proxyRule.proxyIp)
					&& Objects.equals(proxyPort, proxyRule.proxyPort)
					&& Objects.equals(proxyUsername, proxyRule.proxyUsername)
					&& Objects.equals(proxyPassword, proxyRule.proxyPassword);
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(serverType,serverPort,remoteHost,remotePort,needLogin,username,password,proxyNeed,proxyType,proxyIp,proxyPort,proxyUsername,proxyPassword);
	}

}
