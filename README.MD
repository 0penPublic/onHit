# EzXHelper-template
A template for [EzXHelper](https://github.com/KyuubiRan/EzXHelper)

这是一份关于[EzXHelper](https://github.com/KyuubiRan/EzXHelper)的项目模板

## Use this template to create your project / 使用模板创建项目
Before use:
- Change the `applicationId` and `namespace` in `build.gradle.kts`
- Change the packageName and change the HookEntry class path in `xposed_init`
- Change the `rootProject.name` in `settings.gradle.kts`
- Run gradle sync
- Change the `TAG` and `TARGET_APP` in `MainHook.kt`
- Change the `xposedscope` in `arrays.xml`

开始之前：
- 修改`build.gradle.kts`中的`applicationId` 和 `namespace`
- 修改包名并同时修改`xposed_init`中的 Hook 入口类
- 修改`settings.gradle.kts`中的`rootProject.name`
- 执行 gradle sync
- 修改`MainHook.kt`中的`TAG`和`TARGET_APP`
- 修改`arrays.xml`中的`xposedscope`
