package com.ali.trace.spy.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Stack;

/**
 * @author nkhanlang@163.com
 */
public class SelectiveFilter {

	public static void main(String[] args) throws Exception {

		Scanner scanner = new Scanner(System.in);
		if (args.length > 0) {
			String[] filters = args[0].split(",");
			Map<String, List<String>> map = new HashMap<String, List<String>>();
			for (String filter : filters) {
				int idx = filter.lastIndexOf(".");
				String mName = filter.substring(idx + 1);
				String cName = filter.substring(0, idx);
				List<String> list = map.get(mName);
				if (list == null) {
					map.put(mName, list = new ArrayList<String>());
				}
				list.add(cName);
			}

			while (true) {
				System.out.print("please input xml fileName or directory and press q to exit: ");
				String input = scanner.nextLine();
				if ("q".equalsIgnoreCase(input)) {
					break;
				}
				File file = new File(input);
				if (file.exists()) {
					if (file.isDirectory()) {
						for (File subFile : file.listFiles()) {
							String xml = subFile.getAbsolutePath();
							String newXml = xml + "_filter.xml";
							System.out.println("generate file : " + newXml);
							filter(xml, newXml, map);
						}
					} else {
						String xml = file.getAbsolutePath();
						String newXml = xml + "_filter.xml";
						System.out.println("generate file : " + newXml);
						filter(xml, newXml, map);
					}
				} else {
					System.err.println("input [" + input + "] not exists !!!");
				}
			}
		}
	}

	private static void filter(String fileName, String newFileName, Map<String, List<String>> filters)
			throws Exception {
		BufferedReader reader = null;
		BufferedWriter writer = null;
		try {
			reader = new BufferedReader(new FileReader(fileName));
			writer = new BufferedWriter(new FileWriter(newFileName));
			String line = null;
			Stack<LineBean> printBeans = new Stack<LineBean>();
			while ((line = reader.readLine()) != null) {
				char c = line.charAt(1);
				if (c != '/') {
					int blankIdx = line.indexOf(' ');
					int quateIdx = line.indexOf("c='");
					if (blankIdx > 0 && quateIdx > 0) {
						String mName = line.substring(1, blankIdx);
						String cName = line.substring(quateIdx + 3, line.length() - 2);
						LineBean bean = new LineBean(line, cName + "." + mName);
						List<String> cNameList = filters.get(mName);
						if (cNameList != null) {
							boolean match = false;
							for(String cNameItem : cNameList){
								if(cNameItem.equals("*") || cNameItem.equalsIgnoreCase(cName)){
									match = true;
									break;
								}else{
									int splitIdx = cNameItem.indexOf("#");
									if(!printBeans.isEmpty() && splitIdx > 0){
										String superRegex = cNameItem.substring(0, splitIdx);
										if(superRegex.equalsIgnoreCase(printBeans.peek().name)){
											match = true;
											break;
										}
									}
								}
							}
							printBeans.push(bean);
							if (match) {
								for (LineBean preBean : printBeans) {
									if (!preBean.print) {
										writer.write(preBean.start);
										writer.write("\r\n");
										preBean.print = true;
									}
								}
							}
						}else{
							printBeans.push(bean);
						}
					}

				} else {
					if (printBeans.pop().print) {
						writer.write(line);
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

	private static class LineBean {
		String start;
		String name;
		boolean print;

		LineBean(String start, String name) {
			this.start = start;
			this.name = name;
		}
	}
}
