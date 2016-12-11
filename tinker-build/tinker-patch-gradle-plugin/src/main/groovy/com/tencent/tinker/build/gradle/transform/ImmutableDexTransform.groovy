package com.tencent.tinker.build.gradle.transform

import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.DexTransform
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import com.tencent.tinker.android.dex.ClassDef
import com.tencent.tinker.android.dex.Dex
import com.tencent.tinker.build.gradle.TinkerPatchPlugin
import com.tencent.tinker.build.immutable.ClassSimDef
import com.tencent.tinker.build.immutable.DexRefData
import com.tencent.tinker.build.util.FileOperation
import com.tencent.tinker.build.util.Utils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.logging.Logging


import java.lang.reflect.Field;
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Created by wangzhi on 16/11/24.
 */
public class ImmutableDexTransform extends DexTransform {

    public static final String TASK_WORK_DIR = "keep_dex"

    private static final Joiner PATH_JOINER = Joiner.on(File.separatorChar);

    Project project

    String oldApkPath

    File classPreDir

    File baseDexDir

    File dxOutDir

    File mainDexListFile

    String varName

    String varDirName

    def variant


    ImmutableDexTransform(Project project, def variant, DexTransform dexTransform) {
        super(dexTransform.dexOptions, dexTransform.debugMode,
                dexTransform.multiDex, dexTransform.mainDexListFile,
                dexTransform.intermediateFolder, dexTransform.androidBuilder,
                Logging.getLogger(this.getClass()), dexTransform.instantRunBuildContext)
        this.project = project
        this.variant = variant
        this.varName = variant.name.capitalize()
        this.varDirName = variant.getDirName()
        this.oldApkPath = project.tinkerPatch.oldApk
        this.mainDexListFile = dexTransform.mainDexListFile
    }

    public void initFileEnv(TransformOutputProvider outputProvider) {
        classPreDir = getDirInWorkDir("class_pre")
        baseDexDir = getDirInWorkDir("base_dex")
        dxOutDir = outputProvider.getContentLocation("main",
                getOutputTypes(), getScopes(),
                Format.DIRECTORY);

        classPreDir.mkdirs()
        baseDexDir.mkdirs()
        dxOutDir.mkdirs()

        FileOperation.cleanDir(classPreDir)
        FileOperation.cleanDir(baseDexDir)
        FileOperation.cleanDir(dxOutDir)
    }

    private File getDirInWorkDir(String name) {
        return new File(PATH_JOINER.join(project.projectDir,
                TinkerPatchPlugin.TINKER_INTERMEDIATES,
                TASK_WORK_DIR,
                name,
                varDirName)
        )
    }


    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, IOException, InterruptedException {

        // because multi dex is enable,we only process jar file.
        List<JarInput> jarInputs = Lists.newArrayList();
        for (TransformInput input : transformInvocation.getInputs()) {
            jarInputs.addAll(input.getJarInputs());
        }
        //因为开启了multi dex 所以理论上jarInputSize==1
        if (jarInputs.size() != 1) {
            project.logger.error("jar input size is ${jarInputs.size()}, expected is 1. we will skip immutable dex!")
            super.transform(transformInvocation)
            return
        }

        //init
        initFileEnv(transformInvocation.getOutputProvider());
        //先解压 得到所有的old dex
        ArrayList<File> oldDexList = new ArrayList<>()
        traversal(new ZipFile(oldApkPath), { ZipEntry zipEntry, byte[] bytes ->
            if (zipEntry.name.startsWith("classes") && zipEntry.name.endsWith(".dex")) {
                project.logger.info("find dex: ${zipEntry.name} in old apk. ")
                File classDxFile = new File(baseDexDir, zipEntry.name)
                classDxFile.withDataOutputStream { output ->
                    output.write(bytes, 0, bytes.length)
                    output.close()
                }
                oldDexList.add(classDxFile)
            }
        })

        //classPath <==> dexName
        HashMap<String, String> pathDexMap = new HashMap<>()
        project.logger.info("old dex list is : ${oldDexList}.")

        //得到一张classPath<=>dexName 的hashmap
        oldDexList.each { dexFile ->
            Dex dex = new Dex(dexFile)
            dex.classDefs().each { ClassDef classDef ->
                String classPath = dex.typeNames().get(classDef.typeIndex)
                if (pathDexMap.get(classPath)) {
                    throw new GradleException("double class: ${classPath} in dex: ${dexFile.name} ")
                }
                pathDexMap.put(classPath, dexFile.name - ".dex")
            }
        }
        //孤儿类存放的起始dex index
        int newDexIndex = oldDexList.size()
        //得到mainDexSet
        HashSet<String> mainDexSets = initMainDexSet(mainDexListFile)
        project.logger.info("mainDexSets is ${mainDexSets}.")
        //压缩包文件名 <==> 对应的zip流
        HashMap<String, ZipOutputStream> osMap = new HashMap<>()
        //压缩包文件名 <==> 对应的zip中方法数和字段数
        HashMap<String, DexRefData> methodAndFieldsNum = new HashMap<>()
        //孤儿类的 entry <==> 对应bytes
        HashMap<ZipEntry, ByteArrayOutputStream> orphanMap = new HashMap()
        //for groovy
        def that = this
        //all class  in allClass.jar
        HashSet<String> allClassSet = new HashSet<>()
        //遍历all class jar
        processJar(jarInputs.get(0).file, allClassSet, pathDexMap, mainDexSets, methodAndFieldsNum, osMap, orphanMap)

        Iterator<Map.Entry<ZipEntry, ByteArrayOutputStream>> iterator = orphanMap.entrySet().iterator()
        Map.Entry<ZipEntry, ByteArrayOutputStream> leaveEntry = null
        while (iterator.hasNext()) {
            boolean writeResult = true
            while (writeResult && iterator.hasNext()) {
                if (leaveEntry != null) {
                    String newDexName = dexIndexToName(newDexIndex, "")
                    project.logger.info("write level orphan class: ${leaveEntry.key.name} to zip: ${newDexName}")
                    writeResult = writeClassToZip(methodAndFieldsNum, osMap, newDexName, leaveEntry.value.toByteArray(), leaveEntry.key)
                    if (!writeResult) {
                        throw new GradleException("add one class to a new zip failed!\n" +
                                "\t class:" + leaveEntry.key.name + "  zip: " + newDexName)
                    }
                }
                Map.Entry<ZipEntry, ByteArrayOutputStream> entry = iterator.next()
                leaveEntry = entry
                String newDexName = dexIndexToName(newDexIndex, "")
                project.logger.info("write orphan class: ${entry.key.name} to zip: ${newDexName}")
                writeResult = writeClassToZip(methodAndFieldsNum, osMap, newDexName, entry.value.toByteArray(), entry.key)
                if (writeResult) {
                    leaveEntry = null
                }
            }
            newDexIndex++
        }

        //关闭所有的zip流
        osMap.each { key, value ->
            value.close()
        }

        //存放了所有dex的路径,用于之后的校验
        ArrayList<String> dexPathList = new ArrayList<>()
        classPreDir.eachFile { classZip ->
            String classIndexName = classZip.name - ".jar"
            String dexPath = "${dxOutDir.absolutePath}/${classIndexName}.dex"
            dexPathList.add(dexPath)
            doDex(classIndexName, classZip, project.android.getDexOptions())
        }

        checkClassConsistence(dexPathList, allClassSet)

    }

    private void processJar(File jarFile,
                            HashSet<String> allClassSet, HashMap<String, String> pathDexMap, HashSet<String> mainDexSets, HashMap<String, DexRefData> methodAndFieldsNum, HashMap<String, ZipOutputStream> osMap, HashMap<ZipEntry, ByteArrayOutputStream> orphanMap) {


        ZipFile zipFile = new ZipFile(jarFile)
        //先处理maindexlist中的类
        traversal(zipFile, { ZipEntry zipEntry, byte[] bytes ->
            if (zipEntry.name.endsWith(".class")) {
                if (mainDexSets.contains(zipEntry.name)) {
                    String classPath = rePathToClassPath(zipEntry.name)
                    allClassSet.add(classPath)
                    project.logger.info("process main dex list's class " + classPath)
                    if (!writeClassToZip(methodAndFieldsNum, osMap, "classes", bytes, zipEntry)) {
                        throw new GradleException("main dex is exceed the limit! reduce the class number on your main dex keep please.")
                    }
                }
            }
        })

        traversal(zipFile, {
            ZipEntry zipEntry,
            byte[] bytes ->
                if (zipEntry.name.endsWith(".class")) {
                    String classPath = rePathToClassPath(zipEntry.name)
                    if (!Utils.isBlank(classPath) && !allClassSet.contains(classPath)) {
                        allClassSet.add(classPath)
                        //找到该类属于哪个old dex,返回对应dex文件名
                        String belongDex = belongTo(pathDexMap, classPath)
                        //如果是新增的类 或method|fields超出限制
                        if (Utils.isBlank(belongDex) ||
                                !writeClassToZip(methodAndFieldsNum, osMap, belongDex, bytes, zipEntry)) {
                            if (Utils.isBlank(belongDex)) {
                                project.logger.warn("find new class: " + classPath)
                            }
                            saveOrphan(orphanMap, zipEntry, bytes)
                        }
                    } else {
                        if (Utils.isBlank(classPath)) {
                            project.logger.error("illegal zip entry: " + zipEntry.name)
                        }
                    }

                }
        })
    }

    public HashSet<String> initMainDexSet(File mainDexList) {
        HashSet<String> mainDexSets = new HashSet<>()
        BufferedReader reader = mainDexList.newReader()
        List<String> lines = reader.readLines()
        lines.each {
            mainDexSets.add(it)
        }
        return mainDexSets
    }

    private String rePathToClassPath(String rePath) {
        int eIndex = rePath.lastIndexOf(".class")
        if (eIndex >= 0) {
            return "L${rePath.substring(0, eIndex)};"
        } else {
            return ""
        }
    }


    private void doDex(String classIndexName, File classZip, def dexOptions) {
        ArrayList<String> execArgs = new ArrayList()
        def dex = "${project.android.getSdkDirectory()}/build-tools/${project.android.buildToolsVersion}/dx"
        execArgs.add(dex.toString())
        execArgs.add("--dex")
        if (dexOptions.getJumboMode()) {
            execArgs.add("--force-jumbo");
        }
        if (dexOptions.getIncremental()) {
            execArgs.add("--incremental");
            execArgs.add("--no-strict");
        }
        execArgs.add("--output=${dxOutDir.absolutePath}/${classIndexName}.dex".toString())
        execArgs.add(classZip.absolutePath)
        project.logger.info(execArgs.toString())
        Utils.exec(execArgs, null)
    }

    public static void inject(Project project, def variant) {
        project.logger.info("prepare inject dex transform ")
        if (!variant.apkVariantData.variantConfiguration.isMultiDexEnabled()) {
            project.logger.warn("multidex is diable. we will not replace the dex transform.")
            return
        }
        if (!FileOperation.isLegalFile(project.tinkerPatch.oldApk)) {
            project.logger.warn("oldApk is illegal. we will not replace the dex transform.")
            return
        }

        project.getGradle().getTaskGraph().addTaskExecutionGraphListener(new TaskExecutionGraphListener() {
            @Override
            public void graphPopulated(TaskExecutionGraph taskGraph) {
                for (Task task : taskGraph.getAllTasks()) {
                    if (task instanceof TransformTask && task.name.toLowerCase().contains(variant.name.toLowerCase())) {

                        if (((TransformTask) task).getTransform() instanceof DexTransform && !(((TransformTask) task).getTransform() instanceof ImmutableDexTransform)) {
                            project.logger.warn("find dex transform. transform class: " + task.transform.getClass() + " . task name: " + task.name)

                            DexTransform dexTransform = task.transform
                            ImmutableDexTransform hookDexTransform = new ImmutableDexTransform(project,
                                    variant, dexTransform)
                            project.logger.info("variant name: " + variant.name)

                            Field field = TransformTask.class.getDeclaredField("transform")
                            field.setAccessible(true)
                            field.set(task, hookDexTransform)
                            project.logger.warn("transform class after hook: " + task.transform.getClass())
                            break;
                        }
                    }
                }
            }
        });

    }


    void checkClassConsistence(ArrayList<String> dexPathList, HashSet<String> allClassSet) {
        project.logger.info("start check class's consistence ..")
        if (dexPathList == null || dexPathList.size() == 0) {
            throw new GradleException("immutable dex list is null! ")
        }
        project.logger.info("check dex list: " + dexPathList)
        HashSet<String> dexClassSet = new HashSet<>()
        int classSize = 0
        dexPathList.each { path ->
            File dexFile = new File(path)
            if (dexFile.isFile()) {
                Dex dex = new Dex(dexFile)
                classSize += dex.classDefs().size()
                for (ClassDef item : dex.classDefs()) {
                    int index = item.typeIndex
                    dexClassSet.add(dex.typeNames().get(index))
                }
            } else {
                throw new GradleException("dex: ${dexFile} is illegal!")
            }
        }

        HashSet<String> hashSet1 = new HashSet<>(dexClassSet)
        HashSet<String> hashSet2 = new HashSet<>(allClassSet)

        hashSet1.removeAll(allClassSet)
        hashSet2.removeAll(dexClassSet)

        if (hashSet1.size() != 0 || hashSet2.size() != 0) {
            throw new GradleException("class is inconsistent! " + "\n\t"
                    + "allClassSet size is " + allClassSet.size()
                    + ",dexClassSet size is " + dexClassSet.size() + "\n"
                    + "allClassSet has extra class: " + hashSet2 + ",\n"
                    + "dexClassSet has extra class: " + hashSet1 + ".\n"
            )
        } else {
            project.logger.info("check class consistence successful! ")
        }

    }

    boolean writeClassToZip(HashMap<String, DexRefData> methodAndFieldsNum,
                            HashMap<String, ZipOutputStream> osMap,
                            String belongDex,
                            byte[] bytes,
                            ZipEntry zipEntry) {
        File jarFile = new File(classPreDir, belongDex + ".jar")
        DexRefData mfData = methodAndFieldsNum.get(jarFile.name)
        if (mfData == null) {
            mfData = new DexRefData()
            methodAndFieldsNum.put(jarFile.name, mfData)
        }
        ClassSimDef cf = new ClassSimDef(bytes, mfData.refFields, mfData.refMtds)
        ZipOutputStream zos = osMap.get(belongDex)
        if (zos == null) {
            project.logger.info("jarFile is  ${jarFile}.")
            zos = new ZipOutputStream(new FileOutputStream(jarFile))
            osMap.put(belongDex, zos)
        }
        if (!writeClassToZipNoCheck(mfData, cf, zos, zipEntry, bytes)) {
            project.logger.error("except limit! \n \tfind class ${zipEntry.name} method num: ${mfData.methodNum},field num: ${mfData.fieldNum},belong dex: ${belongDex} ")
            return false
        } else {
            return true
        }
    }

    boolean writeClassToZipNoCheck(DexRefData mfData, ClassSimDef cf, ZipOutputStream zos, ZipEntry zipEntry, byte[] bytes) {
        /**
         * 在ClassSimDef中只扫描了类定义的字段和方法中引用到的字段，而实际上在Annotation中也可能引用到某些字段，
         * 所以ClassSimDef中的统计是不全的。因为要统计annotation中引用到的字段有些麻烦，所以在这里直接粗暴的将阈值降低。
         * 留下1000个位置给annotation
         */
        if (mfData.methodNum + cf.methodCount >= 65536 || mfData.fieldNum + cf.fieldCount >= 64536) {
            return false
        } else {
            mfData.methodNum += cf.methodCount
            mfData.fieldNum += cf.fieldCount
            zos.putNextEntry(zipEntry)
            zos.write(bytes)
            zos.closeEntry()
            return true
        }
    }

    void saveOrphan(HashMap<ZipEntry, ByteArrayOutputStream> orphanMap, ZipEntry zipEntry, byte[] bytes) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length)
        bos.write(bytes, 0, bytes.length)
        bos.flush()
        orphanMap.put(zipEntry, bos)
    }

    public static String getNextClassName(int index) {
        return "classes${index + 1}.dex"
    }

    public String dexIndexToName(int index, String suffix) {
        return "classes" + (index == 1 ? "" : index) + suffix
    }

    public String belongTo(HashMap<String, String> pathDexMap, String classPath) {
        return pathDexMap.get(classPath)
    }

    public static void traversal(ZipFile zipFile, Closure callback) {
        try {
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                callback.call(entry, zipFile.getInputStream(entry).bytes)
            }
        } catch (IOException e) {
            e.printStackTrace();
            Utils.closeQuietly(zipFile);
        }

    }


}



