@[TOC](Java使用Netty实现端口转发&Http代理&Sock5代理服务器.md)
<hr style=" border:solid; width:100px; height:1px;" color=#000000 size=1">

# 一、Java使用Netty实现端口转发&Http代理&Sock5代理服务器

这里总结整理了之前使用Java写的端口转发、Http代理、Sock5代理程序，放在同一个工程中，方便使用。

开发语言：Java
开发框架：Netty

## 1.功能

> 端口转发：
> HTTP代理服务器,支持账号密码认证
> Sock5代理服务器,支持账号密码认证
> 支持连接后端时直接连接或采用代理连接，也后端代理连接认证

## 2.参数配置

`修改config.yml`

```c
configs:
    #端口转发demo
  - enable: true
    serverType: forward
    serverPort: 13306
    remoteHost: 127.0.0.1
    remotePort: 3306
    
    #http/https代理demo
  - enable: true
    serverType: http
    serverPort: 3128
    
    #sock5代理demo,需要认证
  - enable: true
    serverType: sock5
    serverPort: 1080
    needLogin: true
    username: "test"
    password: "123456"
```

比如上面的配置，就是开启了一个端口转发，一个Http代理和一个Sock5代理


`全量配置参考,config_full.yml：`

```c
#此文件包含所有能配置的属性，只用来查看使用，程序使用的是config.yml中的配置
configs:
    #规则是否生效,true或者false，默认为true
  - enable: true
    #类型，forward或http或sock5,表示端口转发或http代理或sock5代理，默认为forward
    serverType: forward
    #本地监听的端口号
    serverPort: 13306
    #转发的目标IP，serverType为forward时此参数才有意义
    remoteHost: 127.0.0.1
    #转发的目标端口，serverType为forward时此参数才有意义
    remotePort: 3306
    #是否需要认证，serverType为http和sock5时此参数才有意义
    needLogin: true
    #认证账号，serverType为http和sock5时此参数才有意义
    username: "user"
    #认证密码，serverType为http和sock5时此参数才有意义
    password: "pwd"
    #是否需要通过后端代理连接远程服务器，会覆盖全局的配置
    proxyNeed: false
    #如果需要后端口代理，代理连接类型，http或socks5，会覆盖全局的配置
    proxyType: http
    #如果需要后端口代理，代理连接IP，会覆盖全局的配置
    proxyIp: 127.0.0.1
    #如果需要后端口代理，代理连接Port，会覆盖全局的配置
    proxyPort: 1080
    #如果需要后端口代理，代理连接用户名,为空说明不需要认证，会覆盖全局的配置
    proxyUsername: "proxyUser"
    #如果需要后端口代理，代理连接密码,为空说明不需要认证，会覆盖全局的配置
    proxyPassword: "proxyPwd"

#===后端代理全局配置，会对所有的configs有效，以下配置都有默认值，如果没配置，则采用默认===#
global:
  #是否需要通过后端代理连接远程服务器
  proxyNeed: false
  #代理连接类型，http或socks5
  proxyType: http
  #代理连接IP
  proxyIp: 127.0.0.1
  #代理连接Port
  proxyPort: 1080
  #代理连接用户名,为空说明不需要认证
  proxyUsername: ""
  #代理连接密码,为空说明不需要认证
  proxyPassword: ""
```

## 程序下载

程度可直接下载已编绎好的文件（要求JDK1.8环境下使用）


>

也可以采用源源编绎

```
git clone 
mvn clean package
```

## 程序启动

```c
#window
./start.bat

#Linux
./start.sh
```

