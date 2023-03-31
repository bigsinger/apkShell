# ApkShell
使用`ChatGPT4`实现的安卓应用加固壳`DEMO`（基于整体加固内存加载），外壳的代码全部是`ChatGPT`生成的。



`ShellApp`是外壳的工程，`ShellTester`是待加固的测试用的工程。



为了简化流程，外壳代码基本上是固定的，不需要在加固阶段反编译后修改再回编译，实际上也并没有对`dex`进行加密和压缩，如果实际使用可以自行拓展代码。外壳通过以下方式读取宿主的原始`Application`的类名：

1. `AndroidManifest.xml`的`meta-data`配置，例如：

   ```xml
   <meta-data
              android:name="originApp"
              android:value="com.bigsing.shelltest.MyApp" />
   ```

2. `assets`目录下的`shell_config`配置文件

   ```ini
   originApp=com.bigsing.shelltest.MyApp
   ```

3. 反射获取宿主应用里`com.bigsing.shell.config`类的`originApp`字符串值。也即想要使用本加固方案的应用，可以在工程里创建一个该类，以告知外壳需要加载的`Application`类名。



外壳通过运行时获取当前应用的包名来判断是外壳自己的工程应用还是在宿主应用里，这样设计是为了方便测试。在开发阶段可以通过以下方式来调试代码：

1. 手动修改代码让`isRunningInHostApp`返回`true`
2. 在工程的`assets`目录下放好一个加固后的`encrypted_dex.dex`文件
3. 在外壳自己的`AndroidManifest.xml`里也添加上一个`meta-data`配置，这个`Application`的类名一定要是`encrypted_dex.dex`文件里的`Application`类名
4. 运行`AndroidStudio`就能够跟踪调试文件的解密和加载了

这样可以极大地提高外壳的代码编写速度。



当外壳代码编写完成后打出一个包出来，例如：`Shell.apk`，`ShellTester`也打出一个包，例如：`ShellTester.apk`。然后按照以下的流程加壳：

1. 从`ShellTester.apk`里提取出`classes.dex`，加固处理为`encrypted_dex.dex`。
2. 从`Shell.apk`里把`classes.dex`提取出来，塞到`ShellTester.apk`里。
3. 使用`axml`修改工具（例如：[xml2axml: encode xml to axml AND decode axml to xml--Hack Android Manifest easily](https://github.com/hzw1199/xml2axml)）把`ShellTester.apk`的`AndroidManifest.xml`的`Application`修改为外壳的`Application`（这里是：`com.bigsing.shellapp.ShellApp`）,修改后记得塞回`apk`文件里。
4. 对`ShellTester.apk`签名，安装运行。



由于以上流程比较简单，我就没有使用`Python`脚本处理了，纯手动就做了验证。所以`ChatGPT`生成的`Python`文件我没有经过验证，如果不满足需要自己自行修改，最后一并上传到`GitHub`上，见`doc\packer.py`。与`ChatGPT`的对话部分记录在`doc\ChatGPT_Talk.md`，`apk`文件也存放在`doc`目录下了，其中`ShellTester_packed.apk`就是加固处理后的包。



