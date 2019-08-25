package com.ali.trace.spy.jetty.io;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.loader.ResourceLoader;

import java.io.InputStream;

/**
 * @author nkhanlang@163.com
 */
public class AgentResVmLoader extends ResourceLoader {

    private static AgentResVmLoader instance;
    private ClassLoader loader;

    public AgentResVmLoader() {
        loader = getClass().getClassLoader();
        instance = this;
    }

    public static AgentResVmLoader getInstance(){
        if(instance == null){
            instance = new AgentResVmLoader();
        }
        return instance;
    }
    @Override
    public void init(ExtendedProperties extendedProperties) {
    }

    @Override
    public InputStream getResourceStream(String s) throws ResourceNotFoundException {
        return loader.getResourceAsStream(s);
    }

    @Override
    public boolean isSourceModified(Resource resource) {
        return false;
    }

    @Override
    public long getLastModified(Resource resource) {
        return 0;
    }
}
