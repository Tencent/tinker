package com.tencent.tinker.build.immutable;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;


public class ClassSimDef {

    int methodCount;
    int fieldCount;
    byte[] bytes;
    HashSet<String> refFieldSet;
    HashSet<String> refMtdSet;

    public ClassSimDef(byte[] bytes, HashSet<String> refFieldSet, HashSet<String> refMtdSet) {
        this.bytes = bytes;
        this.refFieldSet = refFieldSet;
        this.refMtdSet = refMtdSet;
        init();
    }

    public void init() {
        methodCount = 0;
        fieldCount = 0;

        ClassReader cr = new ClassReader(bytes);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM4) {
            String className;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                className = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String mtdName, String mtdDesc, String mtdSig, String[] exceptions) {

                String defMtd = className + ":" + mtdName + ":" + mtdDesc;
                if (!refMtdSet.contains(defMtd)) {
                    refMtdSet.add(defMtd);
                    methodCount++;
                }

                MethodVisitor mv = super.visitMethod(access, mtdName, mtdDesc, mtdSig, exceptions);
                mv = new MethodVisitor(Opcodes.ASM4, mv) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fName, String fDesc) {
                        String invokeField = owner + ":" + fName + ":" + fDesc;
                        if (!refFieldSet.contains(invokeField)) {
                            refFieldSet.add(invokeField);
                            fieldCount++;
                        }
                        super.visitFieldInsn(opcode, owner, fName, fDesc);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName, String mDesc) {
                        String invokeMtd = owner + ":" + mName + ":" + mDesc;
                        if (!refMtdSet.contains(invokeMtd)) {
                            refMtdSet.add(invokeMtd);
                            methodCount++;
                        }
                        super.visitMethodInsn(opcode, owner, mName, mDesc);
                    }
                };
                return mv;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                String fieldDesc = className + ":" + name + ":" + desc;
                if (!refFieldSet.contains(fieldDesc)) {
                    refFieldSet.add(fieldDesc);
                    fieldCount++;
                }
                return super.visitField(access, name, desc, signature, value);
            }
        };
        cr.accept(cv, 0);
    }
}