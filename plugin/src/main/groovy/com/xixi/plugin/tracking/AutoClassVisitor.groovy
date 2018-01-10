package com.xixi.plugin.tracking

import com.xixi.plugin.Controller

import com.xixi.plugin.bean.TextUtil
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes


public class AutoClassVisitor extends ClassVisitor {
    /**
     * 是否查看修改后的方法
     */
    public boolean seeModifyMethod = false
    /**
     * 是否满足条件，满足条件的类才会修改中指定的方法
     */
    private boolean isMeetClassCondition = false
    /**
     * 是否需要通过注解执行对应的方法
     */
    private boolean isNeedAnnotation = Controller.isUseAnotation()

    AutoClassVisitor(final ClassVisitor cv) {
        super(Opcodes.ASM4, cv)
    }

    @Override
    void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        String appInterfaceName = Controller.getInterfaceName()
        String appClassName = Controller.getClassName()
        String appSuperName = Controller.getSuperName()
        // 是否满足实现的接口
        if (!TextUtil.isEmpty(appInterfaceName)) {
            interfaces.each {
                String inteface ->
                    if (inteface.contains(appInterfaceName)) {
                        isMeetClassCondition = true
                    }
            }
        }
        // 是否满足指定类
        if (!TextUtil.isEmpty(appClassName) && name.contains(appClassName)) {
            isMeetClassCondition = true
        }
        // 是否满足指定的父类
        if (!TextUtil.isEmpty(appSuperName) && superName.contains(appSuperName)) {
            isMeetClassCondition = true
        }
        // 打印调试信息
        if (isMeetClassCondition){
            Logger.info('||------------------------------开始遍历类 Start--------------------------------------')
            if (!seeModifyMethod) {
                Logger.logForEach('||* visitStart *', Logger.accCode2String(access), name, signature, superName, interfaces)
            }
        }

        super.visit(version, access, name, signature, superName, interfaces)
    }

    @Override
    void visitInnerClass(String name, String outerName, String innerName, int access) {
        String appInnerClassName = Controller.getInnerClassName()
        // 内部类
        if (!isMeetClassCondition && !TextUtil.isEmpty(appInnerClassName) && name.contains(appInnerClassName)) {
            isMeetClassCondition = true
            Logger.info('||------------------------------开始遍历类 Start--------------------------------------')
        }
        if(isMeetClassCondition) {
            if (!seeModifyMethod) {
                Logger.logForEach('||* visitInnerClass *', name, outerName, innerName, Logger.accCode2String(access))
            }
        }
        super.visitInnerClass(name, outerName, innerName, access)
    }

    @Override
    MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {

        MethodVisitor methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions)
        MethodVisitor adapter = null
        String appMethodName = Controller.getMethodName()

        if (seeModifyMethod && !isNeedAnnotation && name.contains(appMethodName)) { //查看插入字节码之后信息，注解查找就不运行了，每个方法都会遍历到，日志太多
            Logger.info("||---------------------查看修改后方法${name}-----------------------------")
            Logger.logForEach('||* visitMethod *', Logger.accCode2String(access), name, desc, signature, exceptions)
            adapter = new AutoMethodVisitor(methodVisitor, access, name, desc)
        } else if (TextUtil.isEmpty(appMethodName)) { //不指定方法名(方法名为空)，根据用户自己传过来的注解修改方法
            Closure vivi = Controller.getAppMethodVistor()
            if (vivi != null) {
                try {
                    adapter = vivi(methodVisitor, access, name, desc)
                } catch (Exception e) {
                    e.printStackTrace()
                    adapter = null
                }
            }
        } else if ((isMeetClassCondition && name.contains(appMethodName))) { //指定方法名，根据满足的类条件和方法名
            Logger.info("||-----------------开始修改方法${name}--------------------------")
            Logger.logForEach('||* visitMethod *', Logger.accCode2String(access), name, desc, signature, exceptions)
            Closure vivi = Controller.getAppMethodVistor()
            if (vivi != null) {
                try {
                    adapter = vivi(methodVisitor, access, name, desc)
                } catch (Exception e) {
                    e.printStackTrace()
                    adapter = null
                }
            }
        }
        if (adapter != null) {
            return adapter
        }
        return methodVisitor
    }

    @Override
    void visitEnd() {
        if (isMeetClassCondition) {
            if (!seeModifyMethod){
                Logger.logForEach('||* visitEnd *')
            }
            Logger.info('||------------------------------结束遍历类 end--------------------------------------')
        }
        super.visitEnd()
    }
}