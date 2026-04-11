[English](/README.md)

# Shizuku-FTP

现在 Shizuku 功能能用了

~~这种服务器端没美化其实也没什么~~

# 使用

不要直接点击启动按钮来生成密钥

没修这个会崩溃的 bug

应该点击左上角，点 Authentication

设置密码

然后启动需要选择一种认证方式

点击右上角，点击 Network status 选择 Shizuku

然后点击启动

# 还需要做什么：

我之前似乎没有注意版本号的问题，以后会注意的

需要修复 sftp Shizuku

修复记录器问题

忽略省电优化（请求
(初始的“空闲多久停止服务器”的值似乎是30分钟，问题应该是这个)

~~我不太熟悉而且看起来确实很难~~

~~我有点不知道从哪里开始~~

精简软件包体积：我在测试时发现了ClassNotFoundException，我以为是优化问题，所以关了很多优化，
到时候再开吧（）

# 问题(优先写的英文版)

1. sftp-Shizuku 不能用，稍后我会修复它

2. 当没生成密钥，直接点击启动，会导致崩溃，应该 参考 #使用 来解决这个问题
稍后我会修复它

3.
尝试改了https://github.com/rD227/shizuku-ftp/blob/aef60727d67f351b5033ae616781aad6cb5e84d8/primitiveFTPd/assets/logback.xml
但是似乎还是把生成的日志放在旧的android/data/app下，改回来了

4.
似乎本地日志功能不生效，只能选择Android然后logcat


# 原始 README.md

你可以去那里查看 README 的原始版本

你也可以去那里帮他们翻译（翻译成其他语言

等等等等