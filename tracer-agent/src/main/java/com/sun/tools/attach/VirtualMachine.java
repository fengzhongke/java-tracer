/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.attach;

import com.sun.tools.attach.spi.AttachProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.io.IOException;


/**
 * A Java virtual machine.
 *
 * <p> A <code>VirtualMachine</code> represents a Java virtual machine to which this
 * Java virtual machine has attached. The Java virtual machine to which it is
 * attached is sometimes called the <i>target virtual machine</i>, or <i>target VM</i>.
 * An application (typically a tool such as a managemet console or profiler) uses a
 * VirtualMachine to load an agent into the target VM. For example, a profiler tool
 * written in the Java Language might attach to a running application and load its
 * profiler agent to profile the running application. </p>
 *
 * <p> A VirtualMachine is obtained by invoking the {@link #attach(String) attach} method
 * with an identifier that identifies the target virtual machine. The identifier is
 * implementation-dependent but is typically the process identifier (or pid) in
 * environments where each Java virtual machine runs in its own operating system process.
 * Alternatively, a <code>VirtualMachine</code> instance is obtained by invoking the
 * {@link #attach(VirtualMachineDescriptor) attach} method with a {@link
 * com.sun.tools.attach.VirtualMachineDescriptor VirtualMachineDescriptor} obtained
 * from the list of virtual machine descriptors returned by the {@link #list list} method.
 * Once a reference to a virtual machine is obtained, the {@link #loadAgent loadAgent},
 * {@link #loadAgentLibrary loadAgentLibrary}, and {@link #loadAgentPath loadAgentPath}
 * methods are used to load agents into target virtual machine. The {@link
 * #loadAgent loadAgent} method is used to load agents that are written in the Java
 * Language and deployed in a {@link java.util.jar.JarFile JAR file}. (See
 * {@link java.lang.instrument} for a detailed description on how these agents
 * are loaded and started). The {@link #loadAgentLibrary loadAgentLibrary} and
 * {@link #loadAgentPath loadAgentPath} methods are used to load agents that
 * are deployed either in a dynamic library or statically linked into the VM and make use of the <a
 * href="../../../../../../../../technotes/guides/jvmti/index.html">JVM Tools
 * Interface</a>. </p>
 *
 * <p> In addition to loading agents a VirtualMachine provides read access to the
 * {@link java.lang.System#getProperties() system properties} in the target VM.
 * This can be useful in some environments where properties such as
 * <code>java.home</code>, <code>os.name</code>, or <code>os.arch</code> are
 * used to construct the path to agent that will be loaded into the target VM.
 *
 * <p> The following example demonstrates how VirtualMachine may be used:</p>
 *
 * <pre>
 *
 *      // attach to target VM
 *      VirtualMachine vm = VirtualMachine.attach("2177");
 *
 *      // start management agent
 *      Properties props = new Properties();
 *      props.put("com.sun.management.jmxremote.port", "5000");
 *      vm.startManagementAgent(props);
 *
 *      // detach
 *      vm.detach();
 *
 * </pre>
 *
 * <p> In this example we attach to a Java virtual machine that is identified by
 * the process identifier <code>2177</code>. Then the JMX management agent is
 * started in the target process using the supplied arguments. Finally, the
 * client detaches from the target VM. </p>
 *
 * <p> A VirtualMachine is safe for use by multiple concurrent threads. </p>
 *
 * @since 1.6
 */

public abstract class VirtualMachine {
    private AttachProvider provider;
    private String id;
    private volatile int hash;        // 0 => not computed

    /**
     * Initializes a new instance of this class.
     *
     * @param   provider
     *          The attach provider creating this class.
     * @param   id
     *          The abstract identifier that identifies the Java virtual machine.
     *
     * @throws  NullPointerException
     *          If <code>provider</code> or <code>id</code> is <code>null</code>.
     */
    protected VirtualMachine(AttachProvider provider, String id) {
        if (provider == null) {
            throw new NullPointerException("provider cannot be null");
        }
        if (id == null) {
            throw new NullPointerException("id cannot be null");
        }
        this.provider = provider;
        this.id = id;
    }

    /**
     * Return a list of Java virtual machines.
     *
     * <p> This method returns a list of Java {@link
     * com.sun.tools.attach.VirtualMachineDescriptor} elements.
     * The list is an aggregation of the virtual machine
     * descriptor lists obtained by invoking the {@link
     * com.sun.tools.attach.spi.AttachProvider#listVirtualMachines
     * listVirtualMachines} method of all installed
     * {@link com.sun.tools.attach.spi.AttachProvider attach providers}.
     * If there are no Java virtual machines known to any provider
     * then an empty list is returned.
     *
     * @return  The list of virtual machine descriptors.
     */
    public static List<VirtualMachineDescriptor> list() {
        ArrayList<VirtualMachineDescriptor> l =
            new ArrayList<VirtualMachineDescriptor>();
        List<AttachProvider> providers = AttachProvider.providers();
        for (AttachProvider provider: providers) {
            l.addAll(provider.listVirtualMachines());
        }
        return l;
    }

    /**
     * Attaches to a Java virtual machine.
     *
     * <p> This method obtains the list of attach providers by invoking the
     * {@link com.sun.tools.attach.spi.AttachProvider#providers()
     * AttachProvider.providers()} method. It then iterates overs the list
     * and invokes each provider's {@link
     * com.sun.tools.attach.spi.AttachProvider#attachVirtualMachine(java.lang.String)
     * attachVirtualMachine} method in turn. If a provider successfully
     * attaches then the iteration terminates, and the VirtualMachine created
     * by the provider that successfully attached is returned by this method.
     * If the <code>attachVirtualMachine</code> method of all providers throws
     * {@link com.sun.tools.attach.AttachNotSupportedException AttachNotSupportedException}
     * then this method also throws <code>AttachNotSupportedException</code>.
     * This means that <code>AttachNotSupportedException</code> is thrown when
     * the identifier provided to this method is invalid, or the identifier
     * corresponds to a Java virtual machine that does not exist, or none
     * of the providers can attach to it. This exception is also thrown if
     * {@link com.sun.tools.attach.spi.AttachProvider#providers()
     * AttachProvider.providers()} returns an empty list. </p>
     *
     * @param   id
     *          The abstract identifier that identifies the Java virtual machine.
     *
     * @return  A VirtualMachine representing the target VM.
     *
     * @throws  SecurityException
     *          If a security manager has been installed and it denies
     *          {@link com.sun.tools.attach.AttachPermission AttachPermission}
     *          <tt>("attachVirtualMachine")</tt>, or another permission
     *          required by the implementation.
     *
     * @throws  AttachNotSupportedException
     *          If the <code>attachVirtualmachine</code> method of all installed
     *          providers throws <code>AttachNotSupportedException</code>, or
     *          there aren't any providers installed.
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If <code>id</code> is <code>null</code>.
     */
    public static VirtualMachine attach(String id)
        throws AttachNotSupportedException, IOException
    {
        if (id == null) {
            throw new NullPointerException("id cannot be null");
        }
        List<AttachProvider> providers = AttachProvider.providers();
        if (providers.size() == 0) {
            throw new AttachNotSupportedException("no providers installed");
        }
        AttachNotSupportedException lastExc = null;
        for (AttachProvider provider: providers) {
            try {
                return provider.attachVirtualMachine(id);
            } catch (AttachNotSupportedException x) {
                lastExc = x;
            }
        }
        throw lastExc;
    }

    /**
     * Attaches to a Java virtual machine.
     *
     * <p> This method first invokes the {@link
     * com.sun.tools.attach.VirtualMachineDescriptor#provider() provider()} method
     * of the given virtual machine descriptor to obtain the attach provider. It
     * then invokes the attach provider's {@link
     * com.sun.tools.attach.spi.AttachProvider#attachVirtualMachine(VirtualMachineDescriptor)
     * attachVirtualMachine} to attach to the target VM.
     *
     * @param   vmd
     *          The virtual machine descriptor.
     *
     * @return  A VirtualMachine representing the target VM.
     *
     * @throws  SecurityException
     *          If a security manager has been installed and it denies
     *          {@link com.sun.tools.attach.AttachPermission AttachPermission}
     *          <tt>("attachVirtualMachine")</tt>, or another permission
     *          required by the implementation.
     *
     * @throws  AttachNotSupportedException
     *          If the attach provider's <code>attachVirtualmachine</code>
     *          throws <code>AttachNotSupportedException</code>.
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If <code>vmd</code> is <code>null</code>.
     */
    public static VirtualMachine attach(VirtualMachineDescriptor vmd)
        throws AttachNotSupportedException, IOException
    {
        return vmd.provider().attachVirtualMachine(vmd);
    }

    /**
     * Detach from the virtual machine.
     *
     * <p> After detaching from the virtual machine, any further attempt to invoke
     * operations on that virtual machine will cause an {@link java.io.IOException
     * IOException} to be thrown. If an operation (such as {@link #loadAgent
     * loadAgent} for example) is in progress when this method is invoked then
     * the behaviour is implementation dependent. In other words, it is
     * implementation specific if the operation completes or throws
     * <tt>IOException</tt>.
     *
     * <p> If already detached from the virtual machine then invoking this
     * method has no effect. </p>
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public abstract void detach() throws IOException;

    /**
     * Returns the provider that created this virtual machine.
     *
     * @return  The provider that created this virtual machine.
     */
    public final AttachProvider provider() {
        return provider;
    }

    /**
     * Returns the identifier for this Java virtual machine.
     *
     * @return  The identifier for this Java virtual machine.
     */
    public final String id() {
        return id;
    }

    /**
     * Loads an agent library.
     *
     * @param   agentLibrary
     *          The name of the agent library.
     *
     * @param   options
     *          The options to provide to the <code>Agent_OnAttach[_L]</code>
     *          function (can be <code>null</code>).
     *
     * @throws  AgentLoadException
     *          If the agent library does not exist.
     *
     * @throws  AgentInitializationException
     *          If the <code>Agent_OnAttach[_L]</code> function returns an error.
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If <code>agentLibrary</code> is <code>null</code>.
     */
    public abstract void loadAgentLibrary(String agentLibrary, String options)
        throws AgentLoadException, AgentInitializationException, IOException;

    /**
     * Loads an agent library.
     *
     * @param   agentLibrary
     *          The name of the agent library.
     *
     * @throws  AgentLoadException
     *          If the agent library does not exist.
     *
     * @throws  AgentInitializationException
     *          If the <code>Agent_OnAttach[_L]</code> function returns an error.
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If <code>agentLibrary</code> is <code>null</code>.
     */
    public void loadAgentLibrary(String agentLibrary)
        throws AgentLoadException, AgentInitializationException, IOException
    {
        loadAgentLibrary(agentLibrary, null);
    }

    /**
     * Load a native agent library by full pathname.
     *
     * @param   agentPath
     *          The full path of the agent library.
     *
     * @param   options
     *          The options to provide to the <code>Agent_OnAttach[_L]</code>
     *          function (can be <code>null</code>).
     *
     * @throws  AgentLoadException
     *          If the agent library does not exist.
     *
     * @throws  AgentInitializationException
     *          If the <code>Agent_OnAttach[_L]</code> function returns an error.
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If <code>agentPath</code> is <code>null</code>.
     */
    public abstract void loadAgentPath(String agentPath, String options)
        throws AgentLoadException, AgentInitializationException, IOException;

    /**
     * Load a native agent library by full pathname.
     *
     * @param   agentPath
     *          The full path to the agent library.
     *
     * @throws  AgentLoadException
     *          If the agent library does not exist.
     *
     * @throws  AgentInitializationException
     *          If the <code>Agent_OnAttach[_L]</code> function returns an error.
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If <code>agentPath</code> is <code>null</code>.
     */
    public void loadAgentPath(String agentPath)
       throws AgentLoadException, AgentInitializationException, IOException
    {
        loadAgentPath(agentPath, null);
    }


   /**
     * Loads an agent.
     *
     * <p> The agent provided to this method is a path name to a JAR file on the file
     * system of the target virtual machine. This path is passed to the target virtual
     * machine where it is interpreted. The target virtual machine attempts to start
     * the agent as specified by the {@link java.lang.instrument} specification.
     * That is, the specified JAR file is added to the system class path (of the target
     * virtual machine), and the <code>agentmain</code> method of the agent class, specified
     * by the <code>Agent-Class</code> attribute in the JAR manifest, is invoked. This
     * method completes when the <code>agentmain</code> method completes.
     *
     * @param   agent
     *          Path to the JAR file containing the agent.
     *
     * @param   options
     *          The options to provide to the agent's <code>agentmain</code>
     *          method (can be <code>null</code>).
     *
     * @throws  AgentLoadException
     *          If the agent does not exist, or cannot be started in the manner
     *          specified in the {@link java.lang.instrument} specification.
     *
     * @throws  AgentInitializationException
     *          If the <code>agentmain</code> throws an exception
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If <code>agent</code> is <code>null</code>.
     */
    public abstract void loadAgent(String agent, String options)
        throws AgentLoadException, AgentInitializationException, IOException;

    /**
     * Loads an agent.
     *
     * @param   agent
     *          Path to the JAR file containing the agent.
     *
     * @throws  AgentLoadException
     *          If the agent does not exist.
     *
     * @throws  AgentInitializationException
     *          If the <code>agentmain</code> throws an exception
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If <code>agent</code> is <code>null</code>.
     */
    public void loadAgent(String agent)
        throws AgentLoadException, AgentInitializationException, IOException
    {
        loadAgent(agent, null);
    }

    /**
     * Returns the current system properties in the target virtual machine.
     *
     * @return  The system properties
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @see     java.lang.System#getProperties
     */
    public abstract Properties getSystemProperties() throws IOException;

    /**
     * Returns the current <i>agent properties</i> in the target virtual
     * machine.
     *
     * @return       The agent properties
     *
     * @throws       IOException
     *               If an I/O error occurs
     */
    public abstract Properties getAgentProperties() throws IOException;

    /**
     * Starts the JMX management agent in the target virtual machine.
     *
     * @param   agentProperties
     *          A Properties object containing the configuration properties
     *          for the agent.
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  IllegalArgumentException
     *          If keys or values in agentProperties are invalid.
     *
     * @throws  NullPointerException
     *          If agentProperties is null.
     *
     * @since   1.8
     */
    public abstract void startManagementAgent(Properties agentProperties) throws IOException;

    /**
     * Starts the local JMX management agent in the target virtual machine.
     *
     * @return  The String representation of the local connector's service address.
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @since   1.8
     */
    public abstract String startLocalManagementAgent() throws IOException;

    /**
     * Returns a hash-code value for this VirtualMachine. The hash
     * code is based upon the VirtualMachine's components, and satifies
     * the general contract of the {@link java.lang.Object#hashCode()
     * Object.hashCode} method.
     *
     * @return  A hash-code value for this virtual machine
     */
    public int hashCode() {
        if (hash != 0) {
            return hash;
        }
        hash = provider.hashCode() * 127 + id.hashCode();
        return hash;
    }

    /**
     * Tests this VirtualMachine for equality with another object.
     *
     * <p> If the given object is not a VirtualMachine then this
     * method returns <tt>false</tt>. For two VirtualMachines to
     * be considered equal requires that they both reference the same
     * provider, and their {@link VirtualMachineDescriptor#id() identifiers} are equal. </p>
     *
     * @param   ob   The object to which this object is to be compared
     *
     * @return  <tt>true</tt> if, and only if, the given object is
     *                a VirtualMachine that is equal to this
     *                VirtualMachine.
     */
    public boolean equals(Object ob) {
        if (ob == this)
            return true;
        if (!(ob instanceof VirtualMachine))
            return false;
        VirtualMachine other = (VirtualMachine)ob;
        if (other.provider() != this.provider()) {
            return false;
        }
        if (!other.id().equals(this.id())) {
            return false;
        }
        return true;
    }

    /**
     * Returns the string representation of the <code>VirtualMachine</code>.
     */
    public String toString() {
        return provider.toString() + ": " + id;
    }
}