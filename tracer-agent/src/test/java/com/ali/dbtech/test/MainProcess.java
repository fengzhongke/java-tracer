package com.ali.dbtech.test;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainProcess {
	
	private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

	public static void main(String[] args) throws InterruptedException {
		Object t = new Object();
		System.out.println(t instanceof Object);
		
		System.out.println("hello world");
		while (true) {
			Thread.sleep(2 * 1000);
			System.out.println("sleep over now time is " + new SimpleDateFormat(DATE_FORMAT).format(new Date()));
		}
	}
	
	public static void print(){
		System.out.println("externer call time is " + new SimpleDateFormat(DATE_FORMAT).format(new Date()));
        throw new RuntimeException("s");
	}
}
