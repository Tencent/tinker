## Tinker
[![license](http://img.shields.io/badge/license-apache_2.0-red.svg?style=flat)](http://git.code.oa.com/tinker/tinker/blob/master/LICENSE)

Tinker is a hot-fix solution library for Android, it supports dex, library and resources update without reinstall apk.

![tinker.png](assets/tinker.png) 

## Getting started
Add tinker-gradle-plugin as a dependency in your main `build.gradle` in the root of your project:

```gradle
buildscript {
    dependencies {
        classpath ('com.tencent.tinker:tinker-patch-gradle-plugin:1.4.0')
    }
}
```

Then you need to "apply" the plugin and add dependencies by adding the following lines to your `app/build.gradle`.

```gradle
dependencies {
    //Optional, help to gen the final application 
    compile('com.tencent.tinker:tinker-android-anno:1.4.0')
    //tinker's main Android lib
    compile('com.tencent.tinker:tinker-android-lib:1.4.0') 
}
...
...
apply plugin: 'com.tencent.tinker.patch'
```

If your app has a class that subclasses android.app.Application, then you need to modify that class, and move all its implements to DefaultApplicationLike rather than Application:

```java
-public class YourApplication extends Application {
+public class SampleApplicationLifeCycle extends DefaultApplicationLike
```

Now you should change your `Application` class, which will be a subclass of [TinkerApplication](http://git.code.oa.com/tinker/tinker/blob/master/tinker-android/tinker-android-loader/src/main/java/com/tencent/tinker/loader/app/TinkerApplication.java). As you can see from its API, it is an abstract class that does not have a default constructor, so you must define a no-arg constructor as follows:

```java
public class SampleApplication extends TinkerApplication {
    public SampleApplication() {
      super(
        //tinkerFlags, which types is supported
        //dex only, library only, all support
        ShareConstants.TINKER_ENABLE_ALL,
        // This is passed as a string so the shell application does not
        // have a binary dependency on your ApplicationLifeCycle class. 
        "tinker.sample.android.SampleApplicationLike");
    }  
}
```

Use `tinker-android-anno` to generate your `Application` is more recommended, you can just add an annotation for your [SampleApplicationLike](http://git.code.oa.com/tinker/tinker/blob/master/tinker-sample-android/app/src/main/java/tinker/sample/android/SampleApplicationLike.java) class

```java
@DefaultLifeCycle(
application = ".SampleApplication",                       //application name to generate
flags = ShareConstants.TINKER_ENABLE_ALL)                 //tinkerFlags above
public class SampleApplicationLike extends DefaultApplicationLike 
```

How to install tinker? learn more at the sample [SampleApplicationLike](http://git.code.oa.com/tinker/tinker/blob/master/tinker-sample-android/app/src/main/java/tinker/sample/android/SampleApplicationLike.java).

For proguard, we have already change the proguard config automatic, and also generate the multiDex keep proguard file for you.

For more tinker configurations, learn more at the sample [app/build.gradle](http://git.code.oa.com/tinker/tinker/blob/master/tinker-sample-android/app/build.gradle).

## Support
Any problem?

1. Learn more from [tinker-sample-android](http://git.code.oa.com/tinker/tinker/tree/master/tinker-sample-android).
2. Read the [source code](http://git.code.oa.com/tinker/tinker/tree/master).
3. Read the [wiki](http://git.code.oa.com/tinker/tinker/wikis/home) or [FAQ](http://git.code.oa.com/tinker/tinker/wikis/faq) for help.
4. Contact us for help.

## Contributing
For more information about contributing issues or pull requsets, see our [Tinker Contributing Guide](http://git.code.oa.com/tinker/tinker/blob/master/CONTRIBUTING.md).

## License
    Copyright (C) 2016 Tencent Wechat, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.