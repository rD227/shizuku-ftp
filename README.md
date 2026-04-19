~~I have never made app before...Maybe it will cost me a few month~~
~~Upgrade~~ 
~~Change it to material3~~

[简体中文](OtherLanguageREADME/README.zh-CN.md) | [Original_README](OtherLanguageREADME/Original_README.md)

# Shizuku-FTP

Now the Shizuku function works
~~This kind of server-side lack of beautification is actually nothing~~

**If you like this project, you can star it!**

~~I will work hard~~

# I need to do other things in recent,don't worry, I won't forget it,

# Fixed ERROR

FTP Shizuku is actually can be used

SFTP Shizuku is actually can be used (The initial path is probably can't be used, you can manually specify)

~~If you click on start and click on another fingerprint, you will be prompted to choose an authentication method.~~

~~This way it will be intercepted instead of crashing. I have not understood this yet.~~

# What still needs to be done:

I think it's better to request full storage access from the beginning, 

otherwise someone will not click that little false, even if the pop-up is annoying to the user

1.
em mm, if you didn't generate the key in the beginning, you should press the button twice 

(One for password, one for fingerprint)

Not bug (Maybe)

2.
If you turn off debuggable in release,

you will lose the ability to debug within the app, and it seems that it will not be written to the csv log file.

Well, restart the app to take effect （ then you can see logs 

**Not bug**

3.
Ok, this bug is caused by the NP manager.

It seems that close the server, the files uploaded to the server will be uploaded really via sftp (NP flies manager) 

You can solve this problem by turning off the root copy files to temp directory option: 

the transfer file will actually be generated in the android/data/...shizuku… directory

~~(If not, a few more reboots seem to work too, this seems to be entirely an NP manager issue that I can't fix)~~

**Not bug**

~~I can't stand it anymore. Why can it be used again? I can't reproduce this bug anymore.~~

~~**Newly discovered** things that could be BUGs:~~

~~Transferring in sftp mode, **my test sftp tool fails to write files to the server**, but can rename~~
~~(Same device access (My testing environment : using the same device to access the server), or it is being designed like this? I haven't looked those files)~~ 

~~Because the [original project](https://github.com/wolpi/prim-ftpd) is also can't be used ~~Maybe it's my files browser's problem~~~~

But you can use [tailscale](https://github.com/tailscale/tailscale) to connect to the server

The transfer won't actively overwrite the same file or directory, and needs to be fixed.

~~I didn’t seem to pay attention to the version number issue before~~

~~The write function of Shizuku-ftp is can't use~~

~~fix the logger problem（Previews logger (of mine) need to be changed~~

Streamlined software package size: I found ClassNotFoundException during testing. I thought it was an optimization problem, so I turned off a lot of optimizations.
Let’s open it again later ()

~~Need to fix sftp Shizuku~~

~~Ignore power saving optimization
(The initial value of "How long to idle before stopping the server" seems to be 30 minutes. The problem should be this)~~

~~I'm not too familiar with it and they do look difficult~~

~~I don't know where to start I'm a bit at a loss where to start~~


# use

(Now you don’t very need to look at this to use it, and the method of opening it is more friendly)

Don't click the launch button directly to generate the key

If this bug is not fixed, it will crash.

You should click in the upper left corner and click Authentication

Set password

Then you need to choose an authentication method when starting

Click the upper right corner, click Network status and select Shizuku

Then click Start

# The Problem:

1.
~~the Shizuku sometimes isn't available (I don't know why)~~ 

~~**Because gpt5.4 try to use root to finish it**~~

~~Finally, I know I need to learn it by myself~~

It was able to use it now.......

~~sftp-Shizuku can't be used~~



~~2.~~
~~When didn't generate the key and try to start the (s)ftp~~

~~It will jump out the window~~ 

~~then it will crash after generate the key~~

~~(Didn't click the Round button in the first~~

~~try to generate key first will finish the problem~~


~~3.
Try changing logback.xml~~

~~But it seems that the generated logs are disappear~~

~~4.~~
~~It seems that the local logging function does not take effect. You can only choose Android and then logcat.~~

5.
Some logger isn't original logger (Shouldn't ignore it when generating key)

# The Original README.md 

You can go there to see original version of the README

And you can go there to help them translate (to other language)

And so on