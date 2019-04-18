package com.ali.trace.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Stack;

import com.ali.trace.intercepter.CompressIntercepter;
import com.ali.trace.support.IFileNameGenerator;

/**
 * used to format XML generated by tracer
 * 
 * @author hanlang.hl
 *
 */
public class XmlUtils {

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        String type = "common";
        if (args.length > 0) {
            type = args[0];
        }

        while (true) {
            System.out.print("please input xml fileName or directory and press q to exit: ");
            String input = scanner.nextLine();
            if ("q".equalsIgnoreCase(input)) {
                break;
            }
            File file = new File(input);
            if (file.exists()) {
                List<String> fileNames = new ArrayList<String>();
                if (file.isDirectory()) {
                    for (File subFile : file.listFiles()) {
                        fileNames.add(subFile.getAbsolutePath());
                    }
                } else {
                    fileNames.add(file.getAbsolutePath());
                }
                formatXml(fileNames, type);
            } else {
                System.err.println("input [" + input + "] not exists !!!");
            }
        }
    }

    public static void formatXml(List<String> fileNames, String type) throws Exception {
        if (type.equalsIgnoreCase("compress")) {
            for (String fileName : fileNames) {
                String newFile = fileName + ".xml";
                String tmpFile = fileName + ".tmp";
                System.out.println("generate file : " + newFile);
                fillXml(fileName, tmpFile);
                compressXml(tmpFile, newFile);
                restoreXml(newFile, tmpFile);
                reverseFile(tmpFile, newFile);
            }
        } else if (type.equalsIgnoreCase("stastic")) {
            stasticXml(fileNames, "/tmp/statstic.xml");
        } else {
            for (String fileName : fileNames) {
                String newFile = fileName + ".xml";
                String tmpFile = fileName + ".tmp";
                System.out.println("generate file : " + newFile);
                fillXml(fileName, newFile);
                restoreXml(newFile, tmpFile);
                reverseFile(tmpFile, newFile);
            }
        }

    }
    
    public static void stasticXml(List<String> fileNames, String newFile) throws Exception{

        TreeNode root = new TreeNode(TreeNode.getId("test", "root"));
        Stack<TreeNode> stack = new Stack<TreeNode>();
        stack.push(root);
        for(String fileName : fileNames){
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(fileName));
                Stack<String> cs = new Stack<String>();
                String line = null;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().length() > 0) {
                        String[] items = line.split("<|'| ");
                        String m = items[1];
                        if (m.charAt(0) == '/') {
                            TreeNode son = stack.pop();
                            Long rt = Long.valueOf(items[3]);
                            son.addRt(rt);
                        } else {
                            String c = items[items.length - 2];
                            cs.push(c);
                            stack.push(stack.peek().addSon(TreeNode.getId(c, m), 1L));
                        }
                    }
                }
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(newFile));
            root.writeFile(writer);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    public static void compressXml(String fileName, final String newFile) throws Exception {
        CompressIntercepter intecepter = new CompressIntercepter(newFile);
        intecepter.setNameGenerator(new IFileNameGenerator() {
            @Override
            public String getName() {
                return newFile;
            }
        });
        Stack<String> cs = new Stack<String>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(fileName));
            String line = null;
            intecepter.start("c", "m");
            while ((line = br.readLine()) != null) {
                if (line.trim().length() > 0) {
                    String[] items = line.split("<|'| ");
                    String m = items[1];
                    if (m.charAt(0) == '/') {
                        String c = cs.pop();
                        intecepter.end(c, m);
                    } else {
                        String c = items[items.length - 2];
                        cs.push(c);
                        intecepter.start(c, m);
                    }
                }
            }
            intecepter.end("c", "m");
        } finally {
            if (br != null) {
                br.close();
            }
        }
    }

    public static void fillXml(String fileName, String fillFile) throws IOException {
        BufferedReader reader = null;
        BufferedWriter writer = null;
        try {
            reader = new BufferedReader(new FileReader(fileName));
            writer = new BufferedWriter(new FileWriter(fillFile));
            String line = null;
            Stack<String> params = new Stack<String>();
            while ((line = reader.readLine()) != null) {
                char c = line.charAt(1);
                if (c != '/') {
                    int idx = line.indexOf(' ');
                    if (idx > 0) {
                        String param = line.substring(1, idx);
                        params.push(param);
                    }
                } else if (!params.isEmpty()) {
                    params.pop();
                }
                writer.write(line);
                writer.write("\r\n");
            }
            if (!params.isEmpty()) {
                while (!params.isEmpty()) {
                    String param = params.pop();
                    writer.write("</");
                    writer.write(param);
                    writer.write(" _f='false'>\r\n");
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
        }
    }

    public static void restoreXml(String fileName, String reverseFile) throws IOException {
        CrunchifyReverseLineReader reader = null;
        BufferedWriter writer = null;
        try {
            reader = new CrunchifyReverseLineReader(fileName);
            writer = new BufferedWriter(new FileWriter(reverseFile));
            String line = null;
            Stack<String> params = new Stack<String>();
            while ((line = reader.readLine()) != null) {
                if (line.length() > 1) {
                    char c = line.charAt(1);
                    int idx = line.indexOf(' ');
                    if (idx > 0) {
                        String prefix = line.substring(0, idx);
                        String param = line.substring(idx, line.length() - 1);
                        writer.write(prefix);
                        if (c == '/') {
                            params.push(param);
                        } else {
                            writer.write(params.pop());
                            writer.write(param);
                        }
                        writer.write(">");
                        writer.write("\r\n");
                    }
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
        }
    }

    public static void reverseFile(String fileName, String reverseFile) throws IOException {
        CrunchifyReverseLineReader reader = null;
        BufferedWriter writer = null;
        try {
            reader = new CrunchifyReverseLineReader(fileName);
            writer = new BufferedWriter(new FileWriter(reverseFile));
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line != null && !"".equals(line.trim())) {
                    writer.write(line);
                    writer.write("\r\n");
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
        }
    }
}
