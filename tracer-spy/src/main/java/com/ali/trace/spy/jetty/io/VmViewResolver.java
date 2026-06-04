package com.ali.trace.spy.jetty.io;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Properties;

/**
 * @author nkhanlang@163.com
 */
public class VmViewResolver {

  private VelocityEngine ve;
  private ClassLoader loader;

  public VmViewResolver() {
    this.loader = getClass().getClassLoader();
    initEngine();
  }

  private void initEngine() {
    try {
      ve = new VelocityEngine();
      Properties p = new Properties();
      p.setProperty("resource.loader", "agent");
      p.setProperty("agent.resource.loader.class", AgentResVmLoader.class.getName());
      p.setProperty("input.encoding", "UTF-8");
      // Disable caching for hot reload support
      p.setProperty("agent.resource.loader.cache", "false");
      p.setProperty("velocimacro.library.autoreload", "true");
      p.setProperty("file.resource.loader.cache", "false");
      ve.init(p);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void resolve(String template, Map<String, Object> params, Writer writer) {
    try {
      Template t = ve.getTemplate(template);
      t.merge(new VelocityContext(params), writer);
    } catch (Exception e) {
      e.printStackTrace(new PrintWriter(writer));
    }
  }
}