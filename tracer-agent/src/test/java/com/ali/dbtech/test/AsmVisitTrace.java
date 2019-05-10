package com.ali.dbtech.test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public class AsmVisitTrace {

    public static void main(String[] args) throws FileNotFoundException, IOException {

        String name = Test.class.getName();
        new Test();
        Scanner scanner = new Scanner(System.in);
        String line = null;
        //test(name);
        while((line = scanner.nextLine()) != null && !"q".equalsIgnoreCase(line)){
            System.out.println("print line : " + line);
            test(line);
        }
    }
    
    public static void test(String name) throws IOException{
        new ClassReader(name).accept(
            new ClassVisitor(Opcodes.ASM7, new ClassWriter( ClassWriter.COMPUTE_MAXS) {
                public String getCommonSuperClass(String type1, String type2) {
                    System.out.println("names : " + type1 + "," + type2);
                    return super.getCommonSuperClass(type1, type2);
                }
            }) {}, ClassReader.EXPAND_FRAMES);
    }

}
