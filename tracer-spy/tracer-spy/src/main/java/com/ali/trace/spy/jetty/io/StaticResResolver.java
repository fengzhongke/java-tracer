package com.ali.trace.spy.jetty.io;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.Map;
import java.util.Properties;

/**
 * @author nkhanlang@163.com
 */
public class StaticResResolver {

    public static void resolve(String file, Writer writer) {
        try {
            InputStream in = AgentResVmLoader.getInstance().getResourceStream(file);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String temp = null;
            while ((temp = br.readLine()) != null) {
                writer.write(temp);
                writer.write("\r\n");
            }
        } catch (Exception e) {
            //e.printStackTrace();
        }
    }
}
