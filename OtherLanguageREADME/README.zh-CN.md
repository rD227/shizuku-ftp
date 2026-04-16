[English](/README.md) | [Original_README](Original_README.md)

# Shizuku-FTP

现在 Shizuku 功能能用了

**如果你喜欢这个项目，你可以给它加星！**

~~这种服务器端没美化其实也没什么~~

# 还需要做什么：

1.
emmm，如果你一开始没有生成密钥，你应该按两次按钮

（一次密码，一次指纹）

2.
release里面关掉debuggable就会失去app内调试的能力，似乎也不会写入那个csv的日志文件里面

debug版本的日志似乎也很少，比如我搓半天就只有一条
2026-04-16 11:14:15.5";"DEBUG";"org.primftpd.prefs.FtpPrefsFragment";"onCreatePreferences()";"";

好吧，重启app才能生效

3.

~~我受不了了，怎么又能用了，我又复现不了这个bug了~~

看来重新启动服务器，上传到服务器的文件将真正通过sftp（NP flies manager）上传 (否则就是 0B 的文件，但是ftp就不会被影响，我猜我可以手动做兼容)

Ok, this bug is caused by the NP manager. 如果有其他情况，请向我发起issue

~~**新发现的** 可能是BUG的东西：~~

~~sftp模式下传输，我的测试sftp的工具无法向服务器写入文件~~
~~同设备访问，或者它本来就是这样？~~

传输不会主动覆盖相同文件或者目录，需要修复么（或者修改名字传输的逻辑而不是抛出一大串报错？

~~我之前似乎没有注意版本号的问题，以后会注意的~~

~~需要修复 sftp Shizuku~~

~~修复记录器问题~~

~~忽略省电优化（请求~~
~~(初始的“空闲多久停止服务器”的值似乎是30分钟，问题应该是这个)~~

~~我不太熟悉而且看起来确实很难~~

~~我有点不知道从哪里开始~~

~~精简软件包体积：我在测试时发现了ClassNotFoundException，我以为是优化问题，所以关了很多优化，~~
~~到时候再开吧（）~~


# 使用

（现在不需要怎么看这个也能使用了，开启的办法更加友善了）

不要直接点击启动按钮来生成密钥

没修这个会崩溃的 bug

应该点击左上角，点 Authentication

设置密码

然后启动需要选择一种认证方式

点击右上角，点击 Network status 选择 Shizuku

然后点击启动

# 问题(优先写的英文版)

~~1. 不能用，稍后我会修复它~~

~~2. 当没生成密钥，直接点击启动，会导致崩溃，应该 参考 #使用 来解决这个问题
稍后我会修复它~~

~~3.
尝试改了https://github.com/rD227/shizuku-ftp/blob/aef60727d67f351b5033ae616781aad6cb5e84d8/primitiveFTPd/assets/logback.xml
但是似乎还是把生成的日志放在旧的android/data/app下，改回来了~~

~~4.
似乎本地日志功能不生效，只能选择Android然后logcat~~


# 原始 README.md

你可以去那里查看 README 的原始版本

你也可以去那里帮他们翻译（翻译成其他语言

等等等等